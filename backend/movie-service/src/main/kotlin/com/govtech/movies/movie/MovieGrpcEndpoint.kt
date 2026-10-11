package com.govtech.movies.movie

import com.govtech.movies.grpc.movie.Artwork
import com.govtech.movies.grpc.movie.CreditRequest
import com.govtech.movies.grpc.movie.ListMoviesRequest
import com.govtech.movies.grpc.movie.MovieIdRequest
import com.govtech.movies.grpc.movie.MovieInput
import com.govtech.movies.grpc.movie.MovieList
import com.govtech.movies.grpc.movie.MoviePage
import com.govtech.movies.grpc.movie.MovieServiceGrpc
import com.govtech.movies.grpc.movie.PersonRef
import com.govtech.movies.grpc.movie.Region as RegionMessage
import com.govtech.movies.grpc.movie.UpdateMovieRequest
import com.govtech.movies.grpc.movie.UploadArtworkRequest
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Component
import com.govtech.movies.grpc.movie.Movie as MovieMessage

/** gRPC adapter: converts protobuf messages to and from domain objects and calls [MovieCatalog]. */
@Component
class MovieGrpcEndpoint(private val catalog: MovieCatalog) : MovieServiceGrpc.MovieServiceImplBase() {

    override fun listMovies(request: ListMoviesRequest, observer: StreamObserver<MoviePage>) = respond(observer) {
        val page = catalog.list(request.search, request.region.toRegion(), request.page, request.size)
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
        catalog.addCredit(request.movieId, request.personId).toMessage()
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
        .setRegion(region.toMessage())
    releaseYear?.let { builder.setReleaseYear(it) }
    return builder.build()
}

fun MovieInput.toDraft() = MovieDraft(
    title = title,
    releaseYear = if (hasReleaseYear()) releaseYear else null,
    genre = genre,
    synopsis = synopsis,
    region = region.toRegion(),
)

/** REGION_UNSPECIFIED (and unknown values from newer clients) map to null. */
fun RegionMessage.toRegion(): Region? = when (this) {
    RegionMessage.SINGAPORE -> Region.SINGAPORE
    RegionMessage.INDIA -> Region.INDIA
    RegionMessage.HOLLYWOOD -> Region.HOLLYWOOD
    else -> null
}

fun Region?.toMessage(): RegionMessage = when (this) {
    Region.SINGAPORE -> RegionMessage.SINGAPORE
    Region.INDIA -> RegionMessage.INDIA
    Region.HOLLYWOOD -> RegionMessage.HOLLYWOOD
    null -> RegionMessage.REGION_UNSPECIFIED
}
