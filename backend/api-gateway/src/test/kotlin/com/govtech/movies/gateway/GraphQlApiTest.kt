package com.govtech.movies.gateway

import com.govtech.movies.grpc.movie.Movie
import com.govtech.movies.grpc.movie.MovieList
import com.govtech.movies.grpc.movie.MoviePage
import com.govtech.movies.grpc.movie.MovieServiceGrpc.MovieServiceBlockingStub
import com.govtech.movies.grpc.showtime.Country
import com.govtech.movies.grpc.showtime.ListShowtimesRequest
import com.govtech.movies.grpc.showtime.Showtime
import com.govtech.movies.grpc.showtime.ShowtimeList
import com.govtech.movies.grpc.showtime.MovieIdList
import com.govtech.movies.grpc.showtime.ShowtimeServiceGrpc.ShowtimeServiceBlockingStub
import com.govtech.movies.grpc.movie.UploadArtworkRequest
import com.govtech.movies.grpc.people.PeopleServiceGrpc.PeopleServiceBlockingStub
import com.govtech.movies.grpc.people.Person
import com.govtech.movies.grpc.people.PersonList
import com.govtech.movies.grpc.people.PersonPage
import com.govtech.movies.grpc.people.PersonRole
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest
import org.springframework.context.annotation.Import
import org.springframework.graphql.execution.ErrorType
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.Base64

/** Runs real GraphQL requests against the schema and controllers, with the gRPC services mocked. */
@GraphQlTest(controllers = [MovieController::class, PersonController::class])
@Import(GraphQlErrors::class)
class GraphQlApiTest {
    @Autowired
    lateinit var graphQl: GraphQlTester

    @MockitoBean
    lateinit var grpc: GrpcClients

    private val movies = mock<MovieServiceBlockingStub>()
    private val people = mock<PeopleServiceBlockingStub>()
    private val showtimes = mock<ShowtimeServiceBlockingStub>()

    @BeforeEach
    fun stubs() {
        whenever(grpc.movies()).thenReturn(movies)
        whenever(grpc.people()).thenReturn(people)
        whenever(grpc.showtimes()).thenReturn(showtimes)
    }

    private fun movie(id: Long, vararg personIds: Long, artworkAt: Long = 0, year: Int? = null) =
        Movie.newBuilder().setId(id).setTitle("Movie $id").setArtworkUpdatedAtMs(artworkAt)
            .addAllPersonIds(personIds.toList())
            .apply { year?.let { setReleaseYear(it) } }
            .build()

    private fun person(id: Long, name: String, role: PersonRole = PersonRole.CAST) =
        Person.newBuilder().setId(id).setName(name).setRole(role).build()

    private fun assertError(document: String, type: ErrorType, message: String) {
        graphQl.document(document).execute().errors().satisfy { errors ->
            assertEquals(1, errors.size)
            assertEquals(type, errors[0].errorType)
            assertEquals(message, errors[0].message)
        }
    }

    @Test
    fun `lists movies with artwork url, optional year and default paging`() {
        val request = argumentCaptor<com.govtech.movies.grpc.movie.ListMoviesRequest>()
        whenever(movies.listMovies(request.capture())).thenReturn(
            MoviePage.newBuilder().addMovies(movie(1, artworkAt = 99, year = 2022)).addMovies(movie(2))
                .setTotalCount(2).setPage(0).setSize(12).build(),
        )

        graphQl.document("{ movies { totalCount items { id releaseYear artworkUrl } } }").execute()
            .path("movies.totalCount").entity(Int::class.java).isEqualTo(2)
            .path("movies.items[0].artworkUrl").entity(String::class.java).isEqualTo("/api/movies/1/artwork?v=99")
            .path("movies.items[0].releaseYear").entity(Int::class.java).isEqualTo(2022)
            .path("movies.items[1].artworkUrl").valueIsNull()
            .path("movies.items[1].releaseYear").valueIsNull()

        assertEquals("", request.firstValue.search)
        assertEquals(emptyList<Long>(), request.firstValue.idsList)
        assertEquals(0, request.firstValue.page)
        assertEquals(12, request.firstValue.size)
    }

    @Test
    fun `showingIn lists only movies showing in that country, with search and paging`() {
        whenever(showtimes.listMovieIdsShowing(any())).thenReturn(MovieIdList.newBuilder().addAllMovieIds(listOf(3, 9)).build())
        val request = argumentCaptor<com.govtech.movies.grpc.movie.ListMoviesRequest>()
        whenever(movies.listMovies(request.capture())).thenReturn(
            MoviePage.newBuilder().addMovies(movie(3)).setTotalCount(1).setPage(0).setSize(12).build(),
        )

        graphQl.document("""{ movies(search: "ja", showingIn: SINGAPORE) { totalCount items { id } } }""").execute()
            .path("movies.items[*].id").entityList(String::class.java).containsExactly("3")

        assertEquals(listOf(3L, 9L), request.firstValue.idsList)
        assertEquals("ja", request.firstValue.search)
        verify(showtimes).listMovieIdsShowing(org.mockito.kotlin.argThat { country == Country.SINGAPORE })
    }

    @Test
    fun `nothing showing gives an empty page instead of every movie`() {
        whenever(showtimes.listMovieIdsShowing(any())).thenReturn(MovieIdList.getDefaultInstance())
        graphQl.document("{ movies(showingIn: MALAYSIA, page: 2) { totalCount page items { id } } }").execute()
            .path("movies.totalCount").entity(Int::class.java).isEqualTo(0)
            .path("movies.page").entity(Int::class.java).isEqualTo(2)
        verify(movies, never()).listMovies(any())
    }

    @Test
    fun `without showingIn showtime-service is not called`() {
        whenever(movies.listMovies(any())).thenReturn(MoviePage.getDefaultInstance())
        graphQl.document("{ movies { totalCount } }").execute().path("movies.totalCount").hasValue()
        verify(showtimes, never()).listMovieIdsShowing(any())
    }

    @Test
    fun `unknown country is rejected by the schema`() {
        graphQl.document("{ movies(showingIn: INDIA) { totalCount } }").execute().errors().satisfy { assertEquals(1, it.size) }
        verify(movies, never()).listMovies(any())
    }

    @Test
    fun `explicit null search is treated as empty`() {
        whenever(movies.listMovies(any())).thenReturn(MoviePage.getDefaultInstance())
        graphQl.document("{ movies(search: null) { totalCount } }").execute()
            .path("movies.totalCount").entity(Int::class.java).isEqualTo(0)
    }

    @Test
    fun `loads people for all movies with one people-service call`() {
        whenever(movies.listMovies(any())).thenReturn(
            MoviePage.newBuilder().addMovies(movie(1, 10, 11)).addMovies(movie(2, 11)).addMovies(movie(3)).build(),
        )
        whenever(people.getPeopleByIds(any())).thenReturn(
            PersonList.newBuilder().addPeople(person(10, "Zed")).addPeople(person(11, "Amy")).build(),
        )

        graphQl.document("{ movies { items { people { name } } } }").execute()
            .path("movies.items[0].people[*].name").entityList(String::class.java).containsExactly("Amy", "Zed")
            .path("movies.items[1].people[*].name").entityList(String::class.java).containsExactly("Amy")
            .path("movies.items[2].people").entityList(Any::class.java).hasSize(0)

        verify(people, times(1)).getPeopleByIds(any())
    }

    @Test
    fun `movies without people do not call people-service`() {
        whenever(movies.listMovies(any())).thenReturn(MoviePage.newBuilder().addMovies(movie(1)).build())
        graphQl.document("{ movies { items { people { name } } } }").execute()
            .path("movies.items[0].people").entityList(Any::class.java).hasSize(0)
        verify(people, never()).getPeopleByIds(any())
    }

    @Test
    fun `creates a movie without a release year`() {
        val request = argumentCaptor<com.govtech.movies.grpc.movie.MovieInput>()
        whenever(movies.createMovie(request.capture())).thenReturn(movie(7))

        graphQl.document("""mutation { createMovie(input: { title: "Leo" }) { id } }""").execute()
            .path("createMovie.id").entity(String::class.java).isEqualTo("7")

        assertEquals("Leo", request.firstValue.title)
        assertEquals(false, request.firstValue.hasReleaseYear())
        assertEquals("", request.firstValue.genre)
    }

    @Test
    fun `uploads decoded artwork bytes`() {
        val bytes = byteArrayOf(1, 2, 3, -1)
        val request = argumentCaptor<UploadArtworkRequest>()
        whenever(movies.uploadArtwork(request.capture())).thenReturn(movie(1, artworkAt = 5))
        val data = Base64.getEncoder().encodeToString(bytes)

        graphQl.document("""mutation { uploadArtwork(movieId: "1", dataBase64: "$data") { artworkUrl } }""").execute()
            .path("uploadArtwork.artworkUrl").entity(String::class.java).isEqualTo("/api/movies/1/artwork?v=5")

        assertArrayEquals(bytes, request.firstValue.data.toByteArray())
    }

    @Test
    fun `invalid base64 is a bad request`() {
        assertError(
            """mutation { uploadArtwork(movieId: "1", dataBase64: "not base64!!") { id } }""",
            ErrorType.BAD_REQUEST,
            "Artwork data is not valid base64",
        )
        verify(movies, never()).uploadArtwork(any())
    }

    @Test
    fun `non-numeric, zero and negative ids are bad requests`() {
        for (id in listOf("abc", "0", "-3", "1.5", "")) {
            assertError("""{ movie(id: "$id") { id } }""", ErrorType.BAD_REQUEST, "Invalid id: $id")
        }
        verify(movies, never()).getMovie(any())
    }

    @Test
    fun `NOT_FOUND from a service is NOT_FOUND`() {
        whenever(movies.getMovie(any())).thenThrow(Status.NOT_FOUND.withDescription("Movie 4 not found").asRuntimeException())
        assertError("""{ movie(id: "4") { id } }""", ErrorType.NOT_FOUND, "Movie 4 not found")
    }

    @Test
    fun `INVALID_ARGUMENT from a service keeps its message`() {
        whenever(movies.createMovie(any())).thenThrow(Status.INVALID_ARGUMENT.withDescription("Title is required").asRuntimeException())
        assertError("""mutation { createMovie(input: { title: "" }) { id } }""", ErrorType.BAD_REQUEST, "Title is required")
    }

    @Test
    fun `too large upload is a friendly bad request`() {
        whenever(movies.uploadArtwork(any())).thenThrow(StatusRuntimeException(Status.RESOURCE_EXHAUSTED))
        assertError(
            """mutation { uploadArtwork(movieId: "1", dataBase64: "AA==") { id } }""",
            ErrorType.BAD_REQUEST,
            "The upload is too large",
        )
    }

    @Test
    fun `unavailable service gives a retry message`() {
        whenever(movies.listMovies(any())).thenThrow(StatusRuntimeException(Status.UNAVAILABLE))
        assertError(
            "{ movies { totalCount } }",
            ErrorType.INTERNAL_ERROR,
            "A backend service is unavailable. Please try again shortly.",
        )
    }

    @Test
    fun `unexpected gRPC errors do not leak details`() {
        whenever(movies.listMovies(any())).thenThrow(Status.INTERNAL.withDescription("secret stack").asRuntimeException())
        assertError("{ movies { totalCount } }", ErrorType.INTERNAL_ERROR, "Something went wrong on the server")
    }

    @Test
    fun `lists people filtered by role`() {
        val request = argumentCaptor<com.govtech.movies.grpc.people.ListPeopleRequest>()
        whenever(people.listPeople(request.capture())).thenReturn(
            PersonPage.newBuilder().addPeople(person(1, "Lokesh", PersonRole.CREATOR)).setTotalCount(1).build(),
        )

        graphQl.document("""{ people(search: "lo", role: CREATOR) { totalCount items { name role } } }""").execute()
            .path("people.items[0].role").entity(String::class.java).isEqualTo("CREATOR")

        assertEquals(PersonRole.CREATOR, request.firstValue.role)
        assertEquals("lo", request.firstValue.search)
    }

    @Test
    fun `no role lists both cast and creators`() {
        val request = argumentCaptor<com.govtech.movies.grpc.people.ListPeopleRequest>()
        whenever(people.listPeople(request.capture())).thenReturn(PersonPage.getDefaultInstance())
        graphQl.document("{ people { totalCount } }").execute().path("people.totalCount").hasValue()
        assertEquals(PersonRole.PERSON_ROLE_UNSPECIFIED, request.firstValue.role)
    }

    @Test
    fun `unknown role value is rejected by the schema`() {
        graphQl.document("{ people(role: DIRECTOR) { totalCount } }").execute().errors().satisfy { errors ->
            assertEquals(1, errors.size)
        }
        verify(people, never()).listPeople(any())
    }

    @Test
    fun `person detail includes their movies`() {
        whenever(people.getPerson(any())).thenReturn(person(3, "Kamal"))
        whenever(movies.listMoviesByPerson(any())).thenReturn(MovieList.newBuilder().addMovies(movie(1)).build())

        graphQl.document("""{ person(id: "3") { name movies { title } } }""").execute()
            .path("person.movies[0].title").entity(String::class.java).isEqualTo("Movie 1")
    }

    @Test
    fun `delete person returns true`() {
        graphQl.document("""mutation { deletePerson(id: "3") }""").execute()
            .path("deletePerson").entity(Boolean::class.java).isEqualTo(true)
    }

    @Test
    fun `delete movie is refused while it has upcoming shows`() {
        whenever(showtimes.listShowtimes(any())).thenReturn(
            ShowtimeList.newBuilder().addShowtimes(Showtime.getDefaultInstance()).addShowtimes(Showtime.getDefaultInstance()).build(),
        )
        assertError(
            """mutation { deleteMovie(id: "4") }""",
            ErrorType.BAD_REQUEST,
            "This movie has 2 upcoming shows and cannot be deleted until they are over",
        )
        verify(movies, never()).deleteMovie(any())
    }

    @Test
    fun `delete movie without upcoming shows checks all countries, then deletes`() {
        val request = argumentCaptor<ListShowtimesRequest>()
        whenever(showtimes.listShowtimes(request.capture())).thenReturn(ShowtimeList.getDefaultInstance())

        graphQl.document("""mutation { deleteMovie(id: "4") }""").execute()
            .path("deleteMovie").entity(Boolean::class.java).isEqualTo(true)

        assertEquals(4L, request.firstValue.movieId)
        assertEquals(Country.COUNTRY_UNSPECIFIED, request.firstValue.country)
        verify(movies).deleteMovie(any())
    }
}
