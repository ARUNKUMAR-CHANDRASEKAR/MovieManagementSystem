package com.govtech.movies.it

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.govtech.movies.gateway.ApiGatewayApplication
import com.govtech.movies.movie.MovieServiceApplication
import com.govtech.movies.people.PeopleServiceApplication
import com.govtech.movies.showtime.ShowtimeServiceApplication
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.context.WebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.PostgreSQLContainer
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Starts movie-service, people-service, showtime-service and api-gateway exactly as in production (real gRPC over
 * the network, real PostgreSQL databases), then sends GraphQL requests over HTTP.
 * This checks what unit tests cannot: that the services really work together.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FullStackIntegrationTest {
    private val movieDb = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    private val peopleDb = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    private val showtimeDb = PostgreSQLContainer<Nothing>("postgres:16-alpine")

    private lateinit var movieService: ConfigurableApplicationContext
    private lateinit var peopleService: ConfigurableApplicationContext
    private lateinit var showtimeService: ConfigurableApplicationContext
    private lateinit var gateway: ConfigurableApplicationContext
    private var gatewayPort = 0

    private val http = HttpClient.newHttpClient()
    private val json = jacksonObjectMapper()

    @BeforeAll
    fun startServices() {
        movieDb.start()
        peopleDb.start()
        showtimeDb.start()
        val moviePort = freePort()
        val peoplePort = freePort()
        val showtimePort = freePort()
        val maxMessageBytes = "--grpc.max-message-bytes=6291456"

        // All services share this test's classpath, so each one's application.yml would hide the
        // others'. "spring.config.name=none" skips those files; each service gets its settings here instead.
        val movieFuture = CompletableFuture.supplyAsync {
            start(
                MovieServiceApplication::class.java,
                "--spring.datasource.url=${movieDb.jdbcUrl}",
                "--spring.datasource.username=${movieDb.username}",
                "--spring.datasource.password=${movieDb.password}",
                "--spring.flyway.locations=classpath:db/migration/movie",
                "--grpc.port=$moviePort",
                maxMessageBytes,
                "--people-service.host=localhost",
                "--people-service.port=$peoplePort",
                *SERVICE_ONLY,
            )
        }
        val peopleFuture = CompletableFuture.supplyAsync {
            start(
                PeopleServiceApplication::class.java,
                "--spring.datasource.url=${peopleDb.jdbcUrl}",
                "--spring.datasource.username=${peopleDb.username}",
                "--spring.datasource.password=${peopleDb.password}",
                "--spring.flyway.locations=classpath:db/migration/people",
                "--grpc.port=$peoplePort",
                maxMessageBytes,
                "--movie-service.host=localhost",
                "--movie-service.port=$moviePort",
                *SERVICE_ONLY,
            )
        }
        val showtimeFuture = CompletableFuture.supplyAsync {
            start(
                ShowtimeServiceApplication::class.java,
                "--spring.datasource.url=${showtimeDb.jdbcUrl}",
                "--spring.datasource.username=${showtimeDb.username}",
                "--spring.datasource.password=${showtimeDb.password}",
                "--spring.flyway.locations=classpath:db/migration/showtime",
                "--grpc.port=$showtimePort",
                maxMessageBytes,
                "--movie-service.host=localhost",
                "--movie-service.port=$moviePort",
                *SERVICE_ONLY,
            )
        }
        val gatewayFuture = CompletableFuture.supplyAsync {
            start(
                ApiGatewayApplication::class.java,
                "--server.port=0",
                maxMessageBytes,
                "--grpc.deadline-seconds=5",
                "--movie-service.host=localhost",
                "--movie-service.port=$moviePort",
                "--people-service.host=localhost",
                "--people-service.port=$peoplePort",
                "--showtime-service.host=localhost",
                "--showtime-service.port=$showtimePort",
                // The services' database libraries are on this classpath too; the gateway has no database.
                "--spring.autoconfigure.exclude=" +
                    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                    "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                    "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
            )
        }

        CompletableFuture.allOf(movieFuture, peopleFuture, showtimeFuture, gatewayFuture).join()
        movieService = movieFuture.get()
        peopleService = peopleFuture.get()
        showtimeService = showtimeFuture.get()
        gateway = gatewayFuture.get()
        gatewayPort = (gateway as WebServerApplicationContext).webServer.port
    }

    @AfterAll
    fun stopServices() {
        listOf(gateway, showtimeService, peopleService, movieService).filter { it.isActive }.forEach { it.close() }
        movieDb.stop()
        peopleDb.stop()
        showtimeDb.stop()
    }

    // ---- tests ----

    @Test
    @Order(1)
    fun `movie shows cast and creators stored in people-service`() {
        val movieId = createMovie("Integration Movie")
        val actorId = createPerson("Integration Actor", "CAST")
        val directorId = createPerson("Integration Director", "CREATOR")

        // movie-service asks people-service over gRPC whether each person exists.
        data("""mutation { addCredit(movieId: "$movieId", personId: "$actorId") { id } }""")
        data("""mutation { addCredit(movieId: "$movieId", personId: "$directorId") { id } }""")

        // The gateway reads credits from movie-service and names from people-service.
        val people = data("""{ movie(id: "$movieId") { people { name role } } }""").at("/movie/people")
        assertEquals(listOf("Integration Actor" to "CAST", "Integration Director" to "CREATOR"),
            people.map { it["name"].asText() to it["role"].asText() })

        val movies = data("""{ person(id: "$actorId") { movies { title } } }""").at("/person/movies")
        assertEquals(listOf("Integration Movie"), movies.map { it["title"].asText() })
    }

    @Test
    @Order(2)
    fun `crediting a person who does not exist is rejected by people-service`() {
        val movieId = createMovie("Credit Check Movie")
        val error = error("""mutation { addCredit(movieId: "$movieId", personId: "999999") { id } }""")
        assertEquals("NOT_FOUND", error["extensions"]["classification"].asText())
        assertEquals("Person 999999 not found", error["message"].asText())
    }

    @Test
    @Order(3)
    fun `search works through the whole stack`() {
        createMovie("Zeta Search India")
        createMovie("Zeta Search Hollywood")
        createMovie("zeta search none")

        val all = data("""{ movies(search: "ZETA SEARCH") { totalCount items { title } } }""")
        assertEquals(3, all.at("/movies/totalCount").asInt())
        assertEquals(
            listOf("Zeta Search Hollywood", "Zeta Search India", "zeta search none"),
            all.at("/movies/items").map { it["title"].asText() },
        )

    }

    @Test
    @Order(4)
    fun `uploaded artwork is stored and served back unchanged`() {
        val movieId = createMovie("Artwork Movie")
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(50_000) { it.toByte() }

        val url = data(
            """mutation(${'$'}d: String!) { uploadArtwork(movieId: "$movieId", dataBase64: ${'$'}d) { artworkUrl } }""",
            mapOf("d" to Base64.getEncoder().encodeToString(png)),
        ).at("/uploadArtwork/artworkUrl").asText()

        val response = http.send(
            HttpRequest.newBuilder(URI("http://localhost:$gatewayPort$url")).build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
        assertEquals(200, response.statusCode())
        assertEquals("image/png", response.headers().firstValue("Content-Type").orElse(""))
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(""))
        assertArrayEquals(png, response.body())
    }

    @Test
    @Order(5)
    fun `a file that is not an image is rejected and missing artwork is 404`() {
        val movieId = createMovie("Bad Artwork Movie")
        val html = Base64.getEncoder().encodeToString("<html><script>alert(1)</script></html>".toByteArray())

        val error = error("""mutation { uploadArtwork(movieId: "$movieId", dataBase64: "$html") { id } }""")

        assertEquals("BAD_REQUEST", error["extensions"]["classification"].asText())
        val response = http.send(
            HttpRequest.newBuilder(URI("http://localhost:$gatewayPort/api/movies/$movieId/artwork")).build(),
            HttpResponse.BodyHandlers.discarding(),
        )
        assertEquals(404, response.statusCode())
    }

    @Test
    @Order(6)
    fun `validation errors from a service reach the client`() {
        val error = error("""mutation { createMovie(input: { title: "   ", releaseYear: 1700 }) { id } }""")
        assertEquals("BAD_REQUEST", error["extensions"]["classification"].asText())
        assertEquals("Title is required", error["message"].asText())
    }

    @Test
    @Order(7)
    fun `deleting a person also removes them from movies in the other database`() {
        val movieId = createMovie("Delete Person Movie")
        val personId = createPerson("Person To Delete", "CAST")
        data("""mutation { addCredit(movieId: "$movieId", personId: "$personId") { id } }""")

        // people-service calls movie-service over gRPC before deleting.
        data("""mutation { deletePerson(id: "$personId") }""")

        assertEquals(0, data("""{ movie(id: "$movieId") { people { id } } }""").at("/movie/people").size())
        val gone = error("""{ person(id: "$personId") { id } }""")
        assertEquals("NOT_FOUND", gone["extensions"]["classification"].asText())
    }

    @Test
    @Order(8)
    fun `deleting a movie removes it everywhere`() {
        val movieId = createMovie("Movie To Delete")
        val personId = createPerson("Person Kept", "CAST")
        data("""mutation { addCredit(movieId: "$movieId", personId: "$personId") { id } }""")

        data("""mutation { deleteMovie(id: "$movieId") }""")

        assertEquals("NOT_FOUND", error("""{ movie(id: "$movieId") { id } }""")["extensions"]["classification"].asText())
        assertEquals(0, data("""{ person(id: "$personId") { movies { id } } }""").at("/person/movies").size())
    }

    @Test
    @Order(9)
    fun `adding the same credit many times at once keeps exactly one credit`() {
        val movieId = createMovie("Credit Race Movie")
        val personId = createPerson("Credit Race Person", "CAST")
        val query = """mutation { addCredit(movieId: "$movieId", personId: "$personId") { id } }"""

        val pool = Executors.newFixedThreadPool(8)
        val results = (1..8).map { CompletableFuture.supplyAsync({ post(query, emptyMap()) }, pool) }.map { it.get() }
        pool.shutdown()

        assertEquals(0, results.count { it["errors"] != null }, "Errors: ${results.mapNotNull { it["errors"] }}")
        assertEquals(1, data("""{ movie(id: "$movieId") { people { id } } }""").at("/movie/people").size())
    }

    @Test
    @Order(20)
    fun `search by location lists only movies showing in that country`() {
        val jailer = createMovie("Location Jailer")
        val leo = createMovie("Location Leo")
        createShowtime(jailer, "Orchard")
        createShowtime(jailer, "Kuala Lumpur")
        createShowtime(leo, "Penang")

        val singapore = data("{ moviesShowing(country: SINGAPORE) { title } }").at("/moviesShowing").map { it["title"].asText() }
        val malaysia = data("{ moviesShowing(country: MALAYSIA) { title } }").at("/moviesShowing").map { it["title"].asText() }

        assertEquals(listOf("Location Jailer"), singapore)
        assertEquals(listOf("Location Jailer", "Location Leo"), malaysia)

        // The Movies page tabs: same filter, combined with search and paging in movie-service.
        val tab = data("""{ movies(search: "location", showingIn: SINGAPORE) { totalCount items { title } } }""")
        assertEquals(1, tab.at("/movies/totalCount").asInt())
        assertEquals("Location Jailer", tab.at("/movies/items/0/title").asText())

        val shows = data("{ showtimes(movieId: \"$jailer\", country: SINGAPORE) { theatre { location } seatsLeft currency } }")
            .at("/showtimes")
        assertEquals(listOf("Orchard"), shows.map { it.at("/theatre/location").asText() })
        assertEquals(96, shows[0]["seatsLeft"].asInt())
        assertEquals("SGD", shows[0]["currency"].asText())
    }

    @Test
    @Order(21)
    fun `scheduling a show for a movie that does not exist is rejected by movie-service`() {
        val error = error(
            "mutation { createShowtime(input: { movieId: \"999999\", theatreId: \"${theatreId("Orchard")}\", " +
                "startsAt: \"${inHours(3)}\", priceCents: 1350 }) { id } }",
        )
        assertEquals("NOT_FOUND", error["extensions"]["classification"].asText())
        assertEquals("Movie 999999 not found", error["message"].asText())
    }

    @Test
    @Order(22)
    fun `booking seats marks them sold and the same seats cannot be booked again`() {
        val showId = createShowtime(createMovie("Booking Movie"), "Tampines")

        val booking = data(
            "mutation { bookSeats(showtimeId: \"$showId\", seats: [\"c5\", \"C4\"], customerName: \"Arun\") " +
                "{ reference seats totalCents currency showtime { seatsLeft soldSeats } } }",
        ).at("/bookSeats")
        assertTrue(booking["reference"].asText().matches(Regex("BK-\\d{6}")))
        assertEquals(listOf("C4", "C5"), booking["seats"].map { it.asText() })
        assertEquals(2700, booking["totalCents"].asInt())
        assertEquals(94, booking.at("/showtime/seatsLeft").asInt())

        val sold = data("{ showtime(id: \"$showId\") { soldSeats movie { title } } }").at("/showtime")
        assertEquals(listOf("C4", "C5"), sold["soldSeats"].map { it.asText() })
        assertEquals("Booking Movie", sold.at("/movie/title").asText())

        val again = error("mutation { bookSeats(showtimeId: \"$showId\", seats: [\"C5\", \"C6\"], customerName: \"Bala\") { reference } }")
        assertEquals("Already sold: C5", again["message"].asText())
        // C6 was not booked either: a booking is all seats or none.
        assertEquals(listOf("C4", "C5"), data("{ showtime(id: \"$showId\") { soldSeats } }").at("/showtime/soldSeats").map { it.asText() })
    }

    @Test
    @Order(23)
    fun `when four people book the same seat at once, exactly one wins`() {
        val showId = createShowtime(createMovie("Race Movie"), "Bishan")
        val query = { name: String -> "mutation { bookSeats(showtimeId: \"$showId\", seats: [\"D7\"], customerName: \"$name\") { reference } }" }

        // Four requests at once, each on its own thread.
        val results = listOf("Ann", "Ben", "Cat", "Dan")
            .map { name -> CompletableFuture.supplyAsync { post(query(name), emptyMap()) } }
            .map { it.get() }

        assertEquals(1, results.count { it["errors"] == null })
        assertEquals(3, results.count { it["errors"] != null })
        assertEquals(listOf("D7"), data("{ showtime(id: \"$showId\") { soldSeats } }").at("/showtime/soldSeats").map { it.asText() })
    }

    @Test
    @Order(24)
    fun `a movie with upcoming shows cannot be deleted`() {
        val movieId = createMovie("Still Showing")
        createShowtime(movieId, "Yishun")

        val refused = error("""mutation { deleteMovie(id: "$movieId") }""")

        assertEquals("BAD_REQUEST", refused["extensions"]["classification"].asText())
        assertEquals("This movie has 1 upcoming show and cannot be deleted until it is over", refused["message"].asText())
        assertEquals("Still Showing", data("""{ movie(id: "$movieId") { title } }""").at("/movie/title").asText())
    }

    // Runs last: it stops movie-service.
    @Test
    @Order(99)
    fun `person is kept when movie-service is down`() {
        val personId = createPerson("Survivor", "CREATOR")

        movieService.close()

        val error = error("""mutation { deletePerson(id: "$personId") }""")
        assertEquals("A backend service is unavailable. Please try again shortly.", error["message"].asText())
        assertEquals("Survivor", data("""{ person(id: "$personId") { name } }""").at("/person/name").asText())
    }

    // ---- helpers ----

    private fun createMovie(title: String): String =
        data("""mutation { createMovie(input: { title: "$title" }) { id } }""").at("/createMovie/id").asText()

    private fun theatreId(location: String): String =
        data("{ theatres { id location } }").at("/theatres").single { it["location"].asText() == location }["id"].asText()

    private fun inHours(hours: Long): String =
        Instant.now().plus(Duration.ofHours(hours)).truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.ofHours(8)).toString()

    private fun createShowtime(movieId: String, location: String): String = data(
        "mutation { createShowtime(input: { movieId: \"$movieId\", theatreId: \"${theatreId(location)}\", " +
            "startsAt: \"${inHours(3)}\", priceCents: 1350 }) { id } }",
    ).at("/createShowtime/id").asText()

    private fun createPerson(name: String, role: String): String =
        data("""mutation { createPerson(input: { name: "$name", role: $role }) { id } }""").at("/createPerson/id").asText()

    /** Sends a GraphQL request and returns `data`, failing the test if there are errors. */
    private fun data(query: String, variables: Map<String, Any> = emptyMap()): JsonNode {
        val body = post(query, variables)
        assertTrue(body["errors"] == null, "Unexpected GraphQL errors: ${body["errors"]}")
        return body["data"]
    }

    /** Sends a GraphQL request that must fail, and returns its first error. */
    private fun error(query: String): JsonNode {
        val errors = post(query, emptyMap())["errors"]
        assertTrue(errors != null && errors.size() > 0, "Expected a GraphQL error")
        return errors[0]
    }

    private fun post(query: String, variables: Map<String, Any>): JsonNode {
        val request = HttpRequest.newBuilder(URI("http://localhost:$gatewayPort/graphql"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(mapOf("query" to query, "variables" to variables))))
            .build()
        return json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body())
    }

    private fun start(app: Class<*>, vararg args: String): ConfigurableApplicationContext =
        SpringApplicationBuilder(app).run("--spring.config.name=none", *args)

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private companion object {
        // Settings shared by movie-service and people-service.
        val SERVICE_ONLY = arrayOf(
            "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.jpa.open-in-view=false",
            // The gateway's web and GraphQL libraries are on this classpath too; the services have no web server.
            "--spring.main.web-application-type=none",
            "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.graphql.GraphQlAutoConfiguration",
        )
    }
}
