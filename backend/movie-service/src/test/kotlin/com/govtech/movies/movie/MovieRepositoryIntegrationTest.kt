package com.govtech.movies.movie

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

/**
 * Runs the repositories against a real PostgreSQL (Docker), with the real Flyway migrations.
 * ddl-auto=validate also checks that the entities match the migrated tables.
 * Each test runs in a transaction that is rolled back afterwards.
 */
@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=validate"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MovieRepositoryIntegrationTest {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }

    @Autowired lateinit var movies: MovieRepository
    @Autowired lateinit var artworks: MovieArtworkRepository
    @Autowired lateinit var entities: TestEntityManager
    @Autowired lateinit var jdbc: JdbcTemplate

    private val byTitle = Sort.by(Sort.Order.asc("title").ignoreCase(), Sort.Order.asc("id"))

    private fun save(title: String, vararg personIds: Long): Movie {
        val now = Instant.now()
        val movie = Movie(title, 2024, "", "", createdAt = now, updatedAt = now)
        movie.personIds.addAll(personIds.toList())
        return movies.save(movie)
    }

    /** Writes pending changes and empties the cache, so the next read really hits the database. */
    private fun flushAndClear() = entities.flush().also { entities.clear() }

    private fun titles(search: String, ids: List<Long> = emptyList(), page: Int = 0, size: Int = 10): List<String> {
        val pageable = PageRequest.of(page, size, byTitle)
        val result = if (ids.isEmpty()) {
            movies.findByTitleContainingIgnoreCase(search, pageable)
        } else {
            movies.findByIdInAndTitleContainingIgnoreCase(ids, search, pageable)
        }
        return result.content.map { it.title }
    }

    private fun count(table: String) = jdbc.queryForObject("select count(*) from $table", Int::class.java)

    @Test
    fun `search is partial, case-insensitive and sorted ignoring case`() {
        save("The Dark Knight")
        save("dark city")
        save("Leo")
        flushAndClear()

        assertEquals(listOf("dark city", "The Dark Knight"), titles("DARK"))
        assertEquals(listOf("dark city", "Leo", "The Dark Knight"), titles(""))
    }

    @Test
    fun `percent and underscore in the search text are matched literally`() {
        save("100% Love")
        save("1000 Ways")
        save("A_B")
        save("AxB")
        flushAndClear()

        assertEquals(listOf("100% Love"), titles("100%"))
        assertEquals(listOf("A_B"), titles("A_B"))
    }

    @Test
    fun `paging returns the right slice and total`() {
        listOf("E", "D", "C", "B", "A").forEach { save(it) }
        flushAndClear()

        val page = movies.findByTitleContainingIgnoreCase("", PageRequest.of(1, 2, byTitle))

        assertEquals(listOf("C", "D"), page.content.map { it.title })
        assertEquals(5, page.totalElements)
        assertEquals(emptyList<String>(), titles("", page = 9, size = 2))
    }

    @Test
    fun `id filter combines with search and paging`() {
        val leo = save("Leo")
        save("Leon")
        val jailer = save("Jailer")
        flushAndClear()

        assertEquals(listOf("Leo"), titles("leo", listOf(leo.id!!, jailer.id!!)))
        assertEquals(listOf("Jailer", "Leo"), titles("", listOf(leo.id!!, jailer.id!!)))
        assertEquals(listOf("Leo"), titles("", listOf(leo.id!!, jailer.id!!), page = 1, size = 1))
        assertEquals(emptyList<String>(), titles("", listOf(99999)))
    }

    @Test
    fun `credits are stored and movies are found by person`() {
        save("Vikram", 1, 2)
        save("Leo", 2)
        save("Kaithi", 3)
        flushAndClear()

        assertEquals(listOf("Leo", "Vikram"), movies.findByPersonId(2).map { it.title })
        assertEquals(emptyList<String>(), movies.findByPersonId(99).map { it.title })
        val vikram = movies.findAll().single { it.title == "Vikram" }
        assertEquals(setOf(1L, 2L), vikram.personIds)
    }

    @Test
    fun `deleting a person's credits leaves other credits alone`() {
        save("Vikram", 1, 2)
        save("Leo", 2)
        flushAndClear()

        assertEquals(2, movies.deleteCreditsForPerson(2))
        flushAndClear()

        assertEquals(1, count("movie_credit"))
        assertEquals(emptyList<Movie>(), movies.findByPersonId(2))
        assertEquals(0, movies.deleteCreditsForPerson(2))
    }

    @Test
    fun `artwork bytes are stored unchanged`() {
        val movie = save("Leo")
        val bytes = ByteArray(300_000) { (it % 256).toByte() }
        artworks.save(MovieArtwork(movie.id!!, "image/png", bytes))
        flushAndClear()

        val loaded = artworks.findById(movie.id!!).orElseThrow()
        assertEquals("image/png", loaded.contentType)
        assertArrayEquals(bytes, loaded.data)
    }

    @Test
    fun `deleting a movie also deletes its artwork and credits`() {
        val movie = save("Leo", 1, 2)
        save("Kaithi", 1)
        artworks.save(MovieArtwork(movie.id!!, "image/png", byteArrayOf(1, 2, 3)))
        flushAndClear()

        movies.deleteById(movie.id!!)
        flushAndClear()

        assertEquals(1, count("movie"))
        assertEquals(0, count("movie_artwork"))
        assertEquals(1, count("movie_credit")) // only Kaithi's credit is left
    }

    @Test
    fun `database rejects a title longer than 200 characters`() {
        assertThrows<DataIntegrityViolationException> {
            jdbc.update("insert into movie (title) values (?)", "a".repeat(201))
        }
    }
}
