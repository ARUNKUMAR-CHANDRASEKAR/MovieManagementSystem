package com.govtech.movies.gateway

import com.govtech.movies.grpc.movie.Movie
import com.govtech.movies.grpc.people.Person
import com.govtech.movies.grpc.showtime.Showtime
import com.govtech.movies.grpc.showtime.Theatre
import java.time.Instant

// GraphQL output types. Field names match schema.graphqls.

data class MovieView(
    val id: String,
    val title: String,
    val releaseYear: Int?,
    val genre: String,
    val synopsis: String,
    val artworkUrl: String?,
    // Not in the GraphQL schema; used to resolve Movie.people.
    val personIds: List<Long>,
)

data class PersonView(
    val id: String,
    val name: String,
    val role: String,
    val bio: String,
)

data class PageView<T>(
    val items: List<T>,
    val totalCount: Long,
    val page: Int,
    val size: Int,
)

fun Movie.toView() = MovieView(
    id = id.toString(),
    title = title,
    releaseYear = if (hasReleaseYear()) releaseYear else null,
    genre = genre,
    synopsis = synopsis,
    // The "v" parameter changes whenever new artwork is uploaded, so browsers can cache each version forever.
    artworkUrl = if (artworkUpdatedAtMs > 0) "/api/movies/$id/artwork?v=$artworkUpdatedAtMs" else null,
    personIds = personIdsList,
)

fun Person.toView() = PersonView(id = id.toString(), name = name, role = role.name, bio = bio)

data class TheatreView(
    val id: String,
    val name: String,
    val country: String,
    val location: String,
    val seatRows: Int,
    val seatsPerRow: Int,
)

data class ShowtimeView(
    val id: String,
    val startsAt: String,
    val priceCents: Int,
    val currency: String,
    val seatsLeft: Int,
    val soldSeats: List<String>,
    val theatre: TheatreView,
    // Not in the GraphQL schema; used to resolve Showtime.movie.
    val movieId: Long,
)

data class BookingView(
    val reference: String,
    val seats: List<String>,
    val totalCents: Int,
    val currency: String,
    val customerName: String,
    val showtime: ShowtimeView,
)

fun Theatre.toView() = TheatreView(id.toString(), name, country.name, location, seatRows, seatsPerRow)

fun Showtime.toView() = ShowtimeView(
    id = id.toString(),
    startsAt = Instant.ofEpochMilli(startsAtEpochMs).toString(),
    priceCents = priceCents,
    currency = currency,
    seatsLeft = seatsLeft,
    soldSeats = soldSeatsList,
    theatre = theatre.toView(),
    movieId = movieId,
)

class BadInputException(message: String) : RuntimeException(message)

/** GraphQL IDs are strings; our services use positive numbers. */
fun parseId(id: String): Long =
    id.toLongOrNull()?.takeIf { it > 0 } ?: throw BadInputException("Invalid id: $id")
