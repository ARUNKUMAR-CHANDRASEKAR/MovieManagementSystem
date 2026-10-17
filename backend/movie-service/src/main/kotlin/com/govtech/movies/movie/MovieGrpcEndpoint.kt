package com.govtech.movies.movie

import com.govtech.movies.grpc.movie.Artwork
import com.govtech.movies.grpc.movie.CreditRequest
import com.govtech.movies.grpc.movie.ListMoviesRequest
import com.govtech.movies.grpc.movie.MovieIdRequest
import com.govtech.movies.grpc.movie.MovieIdsRequest
import com.govtech.movies.grpc.movie.MovieInput
import com.govtech.movies.grpc.movie.MovieList
import com.govtech.movies.grpc.movie.MoviePage
import com.govtech.movies.grpc.movie.MovieServiceGrpc
import com.govtech.movies.grpc.movie.PersonRef
import com.govtech.movies.grpc.movie.UpdateMovieRequest
import com.govtech.movies.grpc.movie.UploadArtworkRequest
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.stub.StreamObserver
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import com.govtech.movies.grpc.movie.Movie as MovieMessage

/** gRPC adapter: converts protobuf messages to and from domain objects and calls [MovieCatalog]. */
@Component
class MovieGrpcEndpoint(private val catalog: MovieCatalog) : MovieServiceGrpc.MovieServiceImplBase() {

    override fun listMovies(request: ListMoviesRequest, observer: StreamObserver<MoviePage>) = respond(observer) {
        val page = catalog.list(request.search, request.idsList, request.page, request.size)
        MoviePage.newBuilder()
            .addAllMovies(page.content.map { it.toMessage() })
            .setTotalCount(page.totalElements)
            .setPage(request.page)
            .setSize(request.size)
            .build()
    }

    override fun getMovie(request: MovieIdRequest, observer: StreamObserver<MovieMessage>) = respond(observer) {
        catalog.get(request.id).toMessage()
    }

    override fun getMoviesByIds(request: MovieIdsRequest, observer: StreamObserver<MovieList>) = respond(observer) {
        MovieList.newBuilder().addAllMovies(catalog.getByIds(request.idsList).map { it.toMessage() }).build()
    }

    override fun createMovie(request: MovieInput, observer: StreamObserver<MovieMessage>) = respond(observer) {
        catalog.create(request.toDraft()).toMessage()
    }

    override fun updateMovie(request: UpdateMovieRequest, observer: StreamObserver<MovieMessage>) = respond(observer) {
        catalog.update(request.id, request.movie.toDraft()).toMessage()
    }

    override fun deleteMovie(request: MovieIdRequest, observer: StreamObserver<Empty>) = respond(observer) {
        catalog.delete(request.id)
        Empty.getDefaultInstance()
    }

    override fun uploadArtwork(request: UploadArtworkRequest, observer: StreamObserver<MovieMessage>) =
        respond(observer) { catalog.uploadArtwork(request.movieId, request.data.toByteArray()).toMessage() }

    override fun getArtwork(request: MovieIdRequest, observer: StreamObserver<Artwork>) = respond(observer) {
        val artwork = catalog.getArtwork(request.id)
        Artwork.newBuilder().setContentType(artwork.contentType).setData(ByteString.copyFrom(artwork.data)).build()
    }

    override fun addCredit(request: CreditRequest, observer: StreamObserver<MovieMessage>) = respond(observer) {
        try {
            catalog.addCredit(request.movieId, request.personId).toMessage()
        } catch (e: DataIntegrityViolationException) {
            // Duplicate PK: another request added same person first. Credit exists, retry reads it.
            // FK violations caught by get(movieId) validation, so only PK duplicates reach here.
            val isPkDuplicate = e.message?.contains("movie_credit_pkey") == true || e.message?.contains("duplicate key") == true
            if (isPkDuplicate) {
                catalog.addCredit(request.movieId, request.personId).toMessage()
            } else {
                throw e
            }
        }
    }

    override fun removeCredit(request: CreditRequest, observer: StreamObserver<MovieMessage>) = respond(observer) {
        catalog.removeCredit(request.movieId, request.personId).toMessage()
    }

    override fun listMoviesByPerson(request: PersonRef, observer: StreamObserver<MovieList>) = respond(observer) {
        MovieList.newBuilder().addAllMovies(catalog.moviesByPerson(request.personId).map { it.toMessage() }).build()
    }

    override fun deleteCreditsForPerson(request: PersonRef, observer: StreamObserver<Empty>) = respond(observer) {
        catalog.deleteCreditsForPerson(request.personId)
        Empty.getDefaultInstance()
    }
}

fun Movie.toMessage(): MovieMessage {
    val builder = MovieMessage.newBuilder()
        .setId(id!!)
        .setTitle(title)
        .setGenre(genre)
        .setSynopsis(synopsis)
        .setArtworkUpdatedAtMs(artworkUpdatedAt?.toEpochMilli() ?: 0)
        .addAllPersonIds(personIds.sorted())
    releaseYear?.let { builder.setReleaseYear(it) }
    return builder.build()
}

fun MovieInput.toDraft() = MovieDraft(
    title = title,
    releaseYear = if (hasReleaseYear()) releaseYear else null,
    genre = genre,
    synopsis = synopsis,
)

