package com.govtech.movies.gateway

import com.govtech.movies.grpc.movie.Movie
import com.govtech.movies.grpc.movie.Region
import com.govtech.movies.grpc.people.Person

// GraphQL output types. Field names match schema.graphqls.

data class MovieView(
    val id: String,
    val title: String,
    val releaseYear: Int?,
    val genre: String,
    val synopsis: String,
    val region: String?,
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
    // REGION_UNSPECIFIED (or a value this gateway does not know yet) becomes null.
    region = region.takeIf { it in KNOWN_REGIONS }?.name,
    synopsis = synopsis,
    // The "v" parameter changes whenever new artwork is uploaded, so browsers can cache each version forever.
    artworkUrl = if (artworkUpdatedAtMs > 0) "/api/movies/$id/artwork?v=$artworkUpdatedAtMs" else null,
    personIds = personIdsList,
)

private val KNOWN_REGIONS = setOf(Region.SINGAPORE, Region.INDIA, Region.HOLLYWOOD)

fun Person.toView() = PersonView(id = id.toString(), name = name, role = role.name, bio = bio)

class BadInputException(message: String) : RuntimeException(message)

/** GraphQL IDs are strings; our services use positive numbers. */
fun parseId(id: String): Long =
    id.toLongOrNull()?.takeIf { it > 0 } ?: throw BadInputException("Invalid id: $id")
