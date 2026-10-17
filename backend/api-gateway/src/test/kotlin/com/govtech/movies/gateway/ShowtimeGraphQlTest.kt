package com.govtech.movies.gateway

import com.govtech.movies.grpc.movie.Movie
import com.govtech.movies.grpc.movie.MovieList
import com.govtech.movies.grpc.movie.MovieServiceGrpc.MovieServiceBlockingStub
import com.govtech.movies.grpc.showtime.BookSeatsRequest
import com.govtech.movies.grpc.showtime.Booking
import com.govtech.movies.grpc.showtime.Country
import com.govtech.movies.grpc.showtime.CreateShowtimeRequest
import com.govtech.movies.grpc.showtime.ListShowtimesRequest
import com.govtech.movies.grpc.showtime.MovieIdList
import com.govtech.movies.grpc.showtime.Showtime
import com.govtech.movies.grpc.showtime.ShowtimeList
import com.govtech.movies.grpc.showtime.ShowtimeServiceGrpc.ShowtimeServiceBlockingStub
import com.govtech.movies.grpc.showtime.Theatre
import io.grpc.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest
import org.springframework.context.annotation.Import
import org.springframework.graphql.execution.ErrorType
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.test.context.bean.override.mockito.MockitoBean

/** Real GraphQL requests against the schema and ShowtimeController, with the gRPC services mocked. */
@GraphQlTest(controllers = [ShowtimeController::class])
@Import(GraphQlErrors::class)
class ShowtimeGraphQlTest {
    @Autowired
    lateinit var graphQl: GraphQlTester

    @MockitoBean
    lateinit var grpc: GrpcClients

    private val movies = mock<MovieServiceBlockingStub>()
    private val showtimes = mock<ShowtimeServiceBlockingStub>()

    private val orchard = Theatre.newBuilder().setId(1).setName("Orchard Cineplex").setCountry(Country.SINGAPORE)
        .setLocation("Orchard").setSeatRows(8).setSeatsPerRow(12).build()

    private fun show(id: Long = 50) = Showtime.newBuilder().setId(id).setMovieId(7).setTheatre(orchard)
        .setStartsAtEpochMs(1_791_800_000_000).setPriceCents(1350).setCurrency("SGD").setSeatsLeft(94)
        .addAllSoldSeats(listOf("A1", "A2")).build()

    @BeforeEach
    fun stubs() {
        whenever(grpc.movies()).thenReturn(movies)
        whenever(grpc.showtimes()).thenReturn(showtimes)
    }

    @Test
    fun `movies showing combines showtime-service ids with movie-service details, sorted by title`() {
        whenever(showtimes.listMovieIdsShowing(any())).thenReturn(MovieIdList.newBuilder().addAllMovieIds(listOf(3, 9)).build())
        whenever(movies.getMoviesByIds(any())).thenReturn(
            MovieList.newBuilder()
                .addMovies(Movie.newBuilder().setId(3).setTitle("leo"))
                .addMovies(Movie.newBuilder().setId(9).setTitle("Jailer"))
                .build(),
        )

        graphQl.document("{ moviesShowing(country: SINGAPORE) { title } }").execute()
            .path("moviesShowing[*].title").entityList(String::class.java).containsExactly("Jailer", "leo")
    }

    @Test
    fun `no movies showing skips movie-service`() {
        whenever(showtimes.listMovieIdsShowing(any())).thenReturn(MovieIdList.getDefaultInstance())
        graphQl.document("{ moviesShowing(country: MALAYSIA) { id } }").execute()
            .path("moviesShowing").entityList(Any::class.java).hasSize(0)
        verify(movies, never()).getMoviesByIds(any())
    }

    @Test
    fun `country is required and must be known`() {
        graphQl.document("{ moviesShowing { id } }").execute().errors().satisfy { assertEquals(1, it.size) }
        graphQl.document("{ moviesShowing(country: INDIA) { id } }").execute().errors().satisfy { assertEquals(1, it.size) }
        verify(showtimes, never()).listMovieIdsShowing(any())
    }

    @Test
    fun `showtimes return theatre, UTC start time, price and seats left`() {
        val request = argumentCaptor<ListShowtimesRequest>()
        whenever(showtimes.listShowtimes(request.capture())).thenReturn(ShowtimeList.newBuilder().addShowtimes(show()).build())

        graphQl.document("""{ showtimes(movieId: "7") { startsAt priceCents currency seatsLeft theatre { location country } } }""")
            .execute()
            .path("showtimes[0].startsAt").entity(String::class.java).isEqualTo("2026-10-12T10:13:20Z")
            .path("showtimes[0].currency").entity(String::class.java).isEqualTo("SGD")
            .path("showtimes[0].seatsLeft").entity(Int::class.java).isEqualTo(94)
            .path("showtimes[0].theatre.location").entity(String::class.java).isEqualTo("Orchard")
            .path("showtimes[0].theatre.country").entity(String::class.java).isEqualTo("SINGAPORE")

        assertEquals(7, request.firstValue.movieId)
        assertEquals(Country.COUNTRY_UNSPECIFIED, request.firstValue.country)
    }

    @Test
    fun `show for the seat map includes sold seats and the movie`() {
        whenever(showtimes.getShowtime(any())).thenReturn(show())
        whenever(movies.getMovie(any())).thenReturn(Movie.newBuilder().setId(7).setTitle("Jailer").build())

        graphQl.document("""{ showtime(id: "50") { soldSeats movie { title } theatre { seatRows seatsPerRow } } }""").execute()
            .path("showtime.soldSeats").entityList(String::class.java).containsExactly("A1", "A2")
            .path("showtime.movie.title").entity(String::class.java).isEqualTo("Jailer")
            .path("showtime.theatre.seatsPerRow").entity(Int::class.java).isEqualTo(12)
    }

    @Test
    fun `deleted movie shows as null instead of failing the page`() {
        whenever(showtimes.getShowtime(any())).thenReturn(show())
        whenever(movies.getMovie(any())).thenThrow(Status.NOT_FOUND.asRuntimeException())
        graphQl.document("""{ showtime(id: "50") { id movie { title } } }""").execute()
            .path("showtime.movie").valueIsNull()
    }

    @Test
    fun `books seats and returns the reference and total`() {
        val request = argumentCaptor<BookSeatsRequest>()
        whenever(showtimes.bookSeats(request.capture())).thenReturn(
            Booking.newBuilder().setId(42).setReference("BK-000042").setShowtime(show())
                .addAllSeats(listOf("B2", "B3")).setTotalCents(2700).setCurrency("SGD").setCustomerName("Arun").build(),
        )

        graphQl.document("""mutation { bookSeats(showtimeId: "50", seats: ["B2", "B3"], customerName: "Arun") { reference totalCents seats } }""")
            .execute()
            .path("bookSeats.reference").entity(String::class.java).isEqualTo("BK-000042")
            .path("bookSeats.totalCents").entity(Int::class.java).isEqualTo(2700)

        assertEquals(50, request.firstValue.showtimeId)
        assertEquals(listOf("B2", "B3"), request.firstValue.seatsList)
    }

    @Test
    fun `booking errors from the service keep their message`() {
        whenever(showtimes.bookSeats(any())).thenThrow(Status.INVALID_ARGUMENT.withDescription("Already sold: A1").asRuntimeException())
        graphQl.document("""mutation { bookSeats(showtimeId: "50", seats: ["A1"], customerName: "Arun") { reference } }""")
            .execute().errors().satisfy {
                assertEquals(ErrorType.BAD_REQUEST, it[0].errorType)
                assertEquals("Already sold: A1", it[0].message)
            }
    }

    @Test
    fun `create show converts the local time with offset to UTC`() {
        val request = argumentCaptor<CreateShowtimeRequest>()
        whenever(showtimes.createShowtime(request.capture())).thenReturn(show())

        graphQl.document("""mutation { createShowtime(input: { movieId: "7", theatreId: "1", startsAt: "2026-10-12T19:30:00+08:00", priceCents: 1350 }) { id } }""")
            .execute().path("createShowtime.id").hasValue()

        assertEquals(java.time.Instant.parse("2026-10-12T11:30:00Z").toEpochMilli(), request.firstValue.startsAtEpochMs)
    }

    @Test
    fun `create show rejects a time without offset`() {
        graphQl.document("""mutation { createShowtime(input: { movieId: "7", theatreId: "1", startsAt: "2026-10-12 19:30", priceCents: 1350 }) { id } }""")
            .execute().errors().satisfy {
                assertEquals(ErrorType.BAD_REQUEST, it[0].errorType)
                assertEquals("startsAt must be a date-time with offset, e.g. 2026-10-12T19:30:00+08:00", it[0].message)
            }
        verify(showtimes, never()).createShowtime(any())
    }
}
