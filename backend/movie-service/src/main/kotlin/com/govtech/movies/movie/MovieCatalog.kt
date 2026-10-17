package com.govtech.movies.movie

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.Year

/** Movie fields as sent by a client, before validation. */
data class MovieDraft(
    val title: String,
    val releaseYear: Int?,
    val genre: String,
    val synopsis: String,
)

/** Business rules for movies, their artwork and their credits. */
@Service
class MovieCatalog(
    private val movies: MovieRepository,
    private val artworks: MovieArtworkRepository,
    private val people: PeopleClient,
    private val clock: Clock,
) {
    companion object {
        const val MAX_PAGE_SIZE = 100
        const val MAX_IDS_PER_LOOKUP = 500
        const val MAX_TITLE_LENGTH = 200
        const val MAX_GENRE_LENGTH = 100
        const val MAX_SYNOPSIS_LENGTH = 5000
        const val MAX_ARTWORK_BYTES = 5 * 1024 * 1024
        const val FIRST_FILM_YEAR = 1888 // Roundhay Garden Scene
        const val MAX_YEARS_AHEAD = 10 // allow announced, unreleased movies
    }

    /** [ids] empty lists all movies; otherwise only those movies (e.g. the ones showing in a country). */
    @Transactional(readOnly = true)
    fun list(search: String, ids: List<Long>, page: Int, size: Int): Page<Movie> {
        validatePaging(page, size)
        if (ids.size > MAX_IDS_PER_LOOKUP) throw InvalidInputException("At most $MAX_IDS_PER_LOOKUP ids per lookup")
        val sort = Sort.by(Sort.Order.asc("title").ignoreCase(), Sort.Order.asc("id"))
        val pageable = PageRequest.of(page, size, sort)
        val text = search.trim()
        return if (ids.isEmpty()) {
            movies.findByTitleContainingIgnoreCase(text, pageable)
        } else {
            movies.findByIdInAndTitleContainingIgnoreCase(ids, text, pageable)
        }
    }

    @Transactional(readOnly = true)
    fun get(id: Long): Movie = movies.findById(id).orElseThrow { NotFoundException("Movie $id not found") }

    /** Returns the movies that exist, in the order of [ids]. Unknown ids are skipped. */
    @Transactional(readOnly = true)
    fun getByIds(ids: List<Long>): List<Movie> {
        val distinct = ids.distinct()
        if (distinct.size > MAX_IDS_PER_LOOKUP) throw InvalidInputException("At most $MAX_IDS_PER_LOOKUP ids per lookup")
        val byId = movies.findAllById(distinct).associateBy { it.id }
        return distinct.mapNotNull { byId[it] }
    }

    @Transactional
    fun create(draft: MovieDraft): Movie {
        val clean = validate(draft)
        val now = clock.instant()
        return movies.save(
            Movie(clean.title, clean.releaseYear, clean.genre, clean.synopsis, createdAt = now, updatedAt = now),
        )
    }

    @Transactional
    fun update(id: Long, draft: MovieDraft): Movie {
        val clean = validate(draft)
        return get(id).apply {
            title = clean.title
            releaseYear = clean.releaseYear
            genre = clean.genre
            synopsis = clean.synopsis
            updatedAt = clock.instant()
        }
    }

    @Transactional
    fun delete(id: Long) {
        // Artwork and credits are removed by "on delete cascade" in the database.
        movies.delete(get(id))
    }

    @Transactional
    fun uploadArtwork(id: Long, data: ByteArray): Movie {
        val movie = get(id)
        if (data.isEmpty()) throw InvalidInputException("Artwork file is empty")
        if (data.size > MAX_ARTWORK_BYTES) throw InvalidInputException("Artwork must be 5 MB or smaller")
        val type = detectImageType(data)
            ?: throw InvalidInputException("Artwork must be a JPEG, PNG, GIF or WebP image")
        artworks.save(MovieArtwork(id, type, data))
        val now: Instant = clock.instant()
        movie.artworkUpdatedAt = now
        movie.updatedAt = now
        return movie
    }

    @Transactional(readOnly = true)
    fun getArtwork(id: Long): MovieArtwork =
        artworks.findById(id).orElseThrow { NotFoundException("Movie $id has no artwork") }

    @Transactional
    fun addCredit(movieId: Long, personId: Long): Movie {
        val movie = get(movieId)
        // Service-to-service gRPC call: people live in people-service's database.
        if (!people.exists(personId)) throw NotFoundException("Person $personId not found")
        movie.personIds.add(personId) // a set, so adding the same person twice is harmless
        return movie
    }

    @Transactional
    fun removeCredit(movieId: Long, personId: Long): Movie = get(movieId).apply { personIds.remove(personId) }

    @Transactional(readOnly = true)
    fun moviesByPerson(personId: Long): List<Movie> = movies.findByPersonId(personId)

    @Transactional
    fun deleteCreditsForPerson(personId: Long) {
        movies.deleteCreditsForPerson(personId)
    }

    private fun validatePaging(page: Int, size: Int) {
        if (page < 0) throw InvalidInputException("Page must be 0 or more")
        if (size !in 1..MAX_PAGE_SIZE) throw InvalidInputException("Page size must be between 1 and $MAX_PAGE_SIZE")
    }

    /** Trims text fields and checks every rule. Returns the cleaned draft. */
    private fun validate(draft: MovieDraft): MovieDraft {
        val clean = draft.copy(title = draft.title.trim(), genre = draft.genre.trim(), synopsis = draft.synopsis.trim())
        val latestYear = Year.now(clock).value + MAX_YEARS_AHEAD
        when {
            clean.title.isEmpty() -> throw InvalidInputException("Title is required")
            clean.title.length > MAX_TITLE_LENGTH ->
                throw InvalidInputException("Title must be $MAX_TITLE_LENGTH characters or fewer")
            clean.releaseYear != null && clean.releaseYear !in FIRST_FILM_YEAR..latestYear ->
                throw InvalidInputException("Release year must be between $FIRST_FILM_YEAR and $latestYear")
            clean.genre.length > MAX_GENRE_LENGTH ->
                throw InvalidInputException("Genre must be $MAX_GENRE_LENGTH characters or fewer")
            clean.synopsis.length > MAX_SYNOPSIS_LENGTH ->
                throw InvalidInputException("Synopsis must be $MAX_SYNOPSIS_LENGTH characters or fewer")
        }
        return clean
    }
}
