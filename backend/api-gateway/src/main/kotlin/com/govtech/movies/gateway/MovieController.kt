package com.govtech.movies.gateway

import com.govtech.movies.grpc.movie.CreditRequest
import com.govtech.movies.grpc.movie.ListMoviesRequest
import com.govtech.movies.grpc.movie.MovieIdRequest
import com.govtech.movies.grpc.movie.MovieInput
import com.govtech.movies.grpc.showtime.Country
import com.govtech.movies.grpc.showtime.CountryRequest
import com.govtech.movies.grpc.showtime.ListShowtimesRequest
import com.govtech.movies.grpc.movie.UpdateMovieRequest
import com.govtech.movies.grpc.movie.UploadArtworkRequest
import com.govtech.movies.grpc.people.PersonIdsRequest
import com.google.protobuf.ByteString
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.util.Base64

data class MovieInputArg(
    val title: String,
    val releaseYear: Int?,
    val genre: String?,
    val synopsis: String?,
)

@Controller
class MovieController(private val grpc: GrpcClients) {

    @QueryMapping
    fun movies(
        @Argument search: String?,
        @Argument showingIn: Country?,
        @Argument page: Int,
        @Argument size: Int,
    ): PageView<MovieView> {
        // showingIn: showtime-service knows which movies are showing there; movie-service then
        // searches and pages only those. No movies showing means an empty page, not "all movies".
        val ids = if (showingIn == null) {
            emptyList()
        } else {
            grpc.showtimes().listMovieIdsShowing(CountryRequest.newBuilder().setCountry(showingIn).build()).movieIdsList
        }
        if (showingIn != null && ids.isEmpty()) return PageView(emptyList(), 0, page, size)
        val request = ListMoviesRequest.newBuilder()
            .setSearch(search.orEmpty())
            .addAllIds(ids)
            .setPage(page)
            .setSize(size)
            .build()
        val result = grpc.movies().listMovies(request)
        return PageView(result.moviesList.map { it.toView() }, result.totalCount, result.page, result.size)
    }

    @QueryMapping
    fun movie(@Argument id: String): MovieView = grpc.movies().getMovie(movieId(id)).toView()

    @MutationMapping
    fun createMovie(@Argument input: MovieInputArg): MovieView = grpc.movies().createMovie(input.toMessage()).toView()

    @MutationMapping
    fun updateMovie(@Argument id: String, @Argument input: MovieInputArg): MovieView {
        val request = UpdateMovieRequest.newBuilder().setId(parseId(id)).setMovie(input.toMessage()).build()
        return grpc.movies().updateMovie(request).toView()
    }

    @MutationMapping
    fun deleteMovie(@Argument id: String): Boolean {
        val request = movieId(id)
        // ponytail: race between check and delete possible in distributed system, accept it
        val upcoming = grpc.showtimes().listShowtimes(
            ListShowtimesRequest.newBuilder().setMovieId(request.id).setCountry(Country.COUNTRY_UNSPECIFIED).build(),
        ).showtimesCount
        if (upcoming > 0) {
            val shows = if (upcoming == 1) "1 upcoming show and cannot be deleted until it is over"
            else "$upcoming upcoming shows and cannot be deleted until they are over"
            throw BadInputException("This movie has $shows")
        }
        grpc.movies().deleteMovie(request)
        return true
    }

    @MutationMapping
    fun uploadArtwork(@Argument movieId: String, @Argument dataBase64: String): MovieView {
        val bytes = try {
            Base64.getDecoder().decode(dataBase64)
        } catch (e: IllegalArgumentException) {
            throw BadInputException("Artwork data is not valid base64")
        }
        // Size and image type are checked by movie-service, which owns the rules.
        val request = UploadArtworkRequest.newBuilder().setMovieId(parseId(movieId)).setData(ByteString.copyFrom(bytes))
        return grpc.movies().uploadArtwork(request.build()).toView()
    }

    @MutationMapping
    fun addCredit(@Argument movieId: String, @Argument personId: String): MovieView =
        grpc.movies().addCredit(credit(movieId, personId)).toView()

    @MutationMapping
    fun removeCredit(@Argument movieId: String, @Argument personId: String): MovieView =
        grpc.movies().removeCredit(credit(movieId, personId)).toView()

    /**
     * Resolves Movie.people for all movies in a response with ONE call to people-service,
     * instead of one call per movie (the "N+1" problem).
     */
    @BatchMapping(typeName = "Movie")
    fun people(movies: List<MovieView>): Map<MovieView, List<PersonView>> {
        val ids = movies.flatMap { it.personIds }.distinct()
        if (ids.isEmpty()) return movies.associateWith { emptyList() }
        val byId = grpc.people().getPeopleByIds(PersonIdsRequest.newBuilder().addAllIds(ids).build())
            .peopleList.associate { it.id to it.toView() }
        return movies.associateWith { movie -> movie.personIds.mapNotNull { byId[it] }.sortedBy { it.name } }
    }

    private fun movieId(id: String) = MovieIdRequest.newBuilder().setId(parseId(id)).build()

    private fun credit(movieId: String, personId: String) =
        CreditRequest.newBuilder().setMovieId(parseId(movieId)).setPersonId(parseId(personId)).build()

    private fun MovieInputArg.toMessage(): MovieInput {
        val builder = MovieInput.newBuilder()
            .setTitle(title)
            .setGenre(genre.orEmpty())
            .setSynopsis(synopsis.orEmpty())
        releaseYear?.let { builder.setReleaseYear(it) }
        return builder.build()
    }
}
