package com.govtech.movies.movie

import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class MovieCatalogTest {
    private val now = Instant.parse("2026-01-15T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val movies = mock<MovieRepository>()
    private val artworks = mock<MovieArtworkRepository>()
    private val people = mock<PeopleClient>()
    private val catalog = MovieCatalog(movies, artworks, people, clock)

    private fun movie(id: Long = 1, title: String = "Vikram") =
        Movie(title, 2022, "Action", "", createdAt = now, updatedAt = now, id = id)

    private fun draft(
        title: String = "Vikram",
        releaseYear: Int? = 2022,
        genre: String = "Action",
        synopsis: String = "",
    ) = MovieDraft(title, releaseYear, genre, synopsis)

    @BeforeEach
    fun saveReturnsArgument() {
        whenever(movies.save(any<Movie>())).doAnswer { it.arguments[0] as Movie }
    }

    @Nested
    inner class List {
        @Test
        fun `trims search text and sorts by title`() {
            val pageable = argumentCaptor<Pageable>()
            whenever(movies.findByTitleContainingIgnoreCase(any(), pageable.capture())).thenReturn(PageImpl(listOf(movie())))

            val result = catalog.list("  vik  ", null, 2, 10)

            verify(movies).findByTitleContainingIgnoreCase(org.mockito.kotlin.eq("vik"), any())
            assertEquals(2, pageable.firstValue.pageNumber)
            assertEquals(10, pageable.firstValue.pageSize)
            assertEquals(true, pageable.firstValue.sort.getOrderFor("title")?.isAscending)
            assertEquals(1, result.content.size)
        }

        @Test
        fun `empty search lists everything`() {
            whenever(movies.findByTitleContainingIgnoreCase(any(), any())).thenReturn(PageImpl(emptyList()))
            catalog.list("", null, 0, 1)
            verify(movies).findByTitleContainingIgnoreCase(org.mockito.kotlin.eq(""), any())
        }

        @Test
        fun `without region lists every region`() {
            whenever(movies.findByTitleContainingIgnoreCase(any(), any())).thenReturn(PageImpl(emptyList()))
            catalog.list("", null, 0, 10)
            verify(movies, never()).findByRegionAndTitleContainingIgnoreCase(any(), any(), any())
        }

        @Test
        fun `with region filters by region and search`() {
            whenever(movies.findByRegionAndTitleContainingIgnoreCase(any(), any(), any())).thenReturn(PageImpl(emptyList()))
            catalog.list(" leo ", Region.INDIA, 0, 10)
            verify(movies).findByRegionAndTitleContainingIgnoreCase(org.mockito.kotlin.eq(Region.INDIA), org.mockito.kotlin.eq("leo"), any())
            verify(movies, never()).findByTitleContainingIgnoreCase(any(), any())
        }

        @Test
        fun `rejects negative page`() {
            val e = assertThrows<InvalidInputException> { catalog.list("", null, -1, 10) }
            assertEquals("Page must be 0 or more", e.message)
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -5, 101])
        fun `rejects page size outside 1 to 100`(size: Int) {
            assertThrows<InvalidInputException> { catalog.list("", null, 0, size) }
        }

        @ParameterizedTest
        @ValueSource(ints = [1, 100])
        fun `accepts page size at the limits`(size: Int) {
            whenever(movies.findByTitleContainingIgnoreCase(any(), any())).thenReturn(PageImpl(emptyList()))
            catalog.list("", null, 0, size)
        }
    }

    @Nested
    inner class Create {
        @Test
        fun `saves trimmed fields with timestamps`() {
            val created = catalog.create(draft(title = "  Leo  ", genre = " Thriller ", synopsis = " Story "))

            assertEquals("Leo", created.title)
            assertEquals("Thriller", created.genre)
            assertEquals("Story", created.synopsis)
            assertEquals(now, created.createdAt)
            assertEquals(now, created.updatedAt)
            assertNull(created.artworkUpdatedAt)
        }

        @Test
        fun `saves region, which is optional`() {
            assertEquals(Region.SINGAPORE, catalog.create(draft().copy(region = Region.SINGAPORE)).region)
            assertNull(catalog.create(draft()).region)
        }

        @Test
        fun `release year is optional`() {
            assertNull(catalog.create(draft(releaseYear = null)).releaseYear)
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "   ", "\t\n"])
        fun `rejects blank title`(title: String) {
            val e = assertThrows<InvalidInputException> { catalog.create(draft(title = title)) }
            assertEquals("Title is required", e.message)
            verify(movies, never()).save(any<Movie>())
        }

        @Test
        fun `accepts title of exactly 200 characters and rejects 201`() {
            catalog.create(draft(title = "a".repeat(200)))
            assertThrows<InvalidInputException> { catalog.create(draft(title = "a".repeat(201))) }
        }

        @Test
        fun `title length is checked after trimming`() {
            catalog.create(draft(title = "  " + "a".repeat(200) + "  "))
        }

        @ParameterizedTest
        @ValueSource(ints = [1888, 2026, 2036])
        fun `accepts release years from 1888 to ten years ahead`(year: Int) {
            assertEquals(year, catalog.create(draft(releaseYear = year)).releaseYear)
        }

        @ParameterizedTest
        @ValueSource(ints = [1887, 2037, 0, -1])
        fun `rejects release years outside the range`(year: Int) {
            val e = assertThrows<InvalidInputException> { catalog.create(draft(releaseYear = year)) }
            assertEquals("Release year must be between 1888 and 2036", e.message)
        }

        @Test
        fun `rejects genre over 100 characters`() {
            assertThrows<InvalidInputException> { catalog.create(draft(genre = "g".repeat(101))) }
        }

        @Test
        fun `rejects synopsis over 5000 characters`() {
            catalog.create(draft(synopsis = "s".repeat(5000)))
            assertThrows<InvalidInputException> { catalog.create(draft(synopsis = "s".repeat(5001))) }
        }

        @Test
        fun `keeps unicode titles`() {
            assertEquals("விக்ரம் 🎬", catalog.create(draft(title = "விக்ரம் 🎬")).title)
        }
    }

    @Nested
    inner class Update {
        @Test
        fun `changes fields and updatedAt but not createdAt`() {
            val existing = Movie("Old", 2000, "", "", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH, id = 1)
            whenever(movies.findById(1)).thenReturn(Optional.of(existing))

            val updated = catalog.update(1, draft(title = "New", releaseYear = null).copy(region = Region.HOLLYWOOD))

            assertEquals("New", updated.title)
            assertEquals(Region.HOLLYWOOD, updated.region)
            assertNull(updated.releaseYear)
            assertEquals(Instant.EPOCH, updated.createdAt)
            assertEquals(now, updated.updatedAt)
        }

        @Test
        fun `unknown movie is not found`() {
            whenever(movies.findById(42)).thenReturn(Optional.empty())
            val e = assertThrows<NotFoundException> { catalog.update(42, draft()) }
            assertEquals("Movie 42 not found", e.message)
        }

        @Test
        fun `invalid input is rejected before looking up the movie`() {
            assertThrows<InvalidInputException> { catalog.update(1, draft(title = "")) }
            verify(movies, never()).findById(any())
        }
    }

    @Nested
    inner class Delete {
        @Test
        fun `deletes existing movie`() {
            val existing = movie()
            whenever(movies.findById(1)).thenReturn(Optional.of(existing))
            catalog.delete(1)
            verify(movies).delete(existing)
        }

        @Test
        fun `unknown movie is not found`() {
            whenever(movies.findById(1)).thenReturn(Optional.empty())
            assertThrows<NotFoundException> { catalog.delete(1) }
            verify(movies, never()).delete(any())
        }
    }

    @Nested
    inner class Artwork {
        private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

        @Test
        fun `stores detected type and bumps artwork version`() {
            val existing = movie()
            whenever(movies.findById(1)).thenReturn(Optional.of(existing))
            val saved = argumentCaptor<MovieArtwork>()

            val result = catalog.uploadArtwork(1, png)

            verify(artworks).save(saved.capture())
            assertEquals("image/png", saved.firstValue.contentType)
            assertArrayEquals(png, saved.firstValue.data)
            assertEquals(now, result.artworkUpdatedAt)
            assertEquals(now, result.updatedAt)
        }

        @Test
        fun `rejects empty file`() {
            whenever(movies.findById(1)).thenReturn(Optional.of(movie()))
            val e = assertThrows<InvalidInputException> { catalog.uploadArtwork(1, ByteArray(0)) }
            assertEquals("Artwork file is empty", e.message)
        }

        @Test
        fun `accepts exactly 5 MB and rejects one byte more`() {
            whenever(movies.findById(1)).thenReturn(Optional.of(movie()))
            val max = png.copyOf(MovieCatalog.MAX_ARTWORK_BYTES)
            catalog.uploadArtwork(1, max)
            val e = assertThrows<InvalidInputException> { catalog.uploadArtwork(1, png.copyOf(max.size + 1)) }
            assertEquals("Artwork must be 5 MB or smaller", e.message)
        }

        @Test
        fun `rejects non-image content`() {
            whenever(movies.findById(1)).thenReturn(Optional.of(movie()))
            val html = "<html><script>alert(1)</script></html>".toByteArray()
            assertThrows<InvalidInputException> { catalog.uploadArtwork(1, html) }
            verify(artworks, never()).save(any())
        }

        @Test
        fun `upload to unknown movie is not found`() {
            whenever(movies.findById(9)).thenReturn(Optional.empty())
            assertThrows<NotFoundException> { catalog.uploadArtwork(9, png) }
            verify(artworks, never()).save(any())
        }

        @Test
        fun `movie without artwork is not found`() {
            whenever(artworks.findById(1)).thenReturn(Optional.empty())
            val e = assertThrows<NotFoundException> { catalog.getArtwork(1) }
            assertEquals("Movie 1 has no artwork", e.message)
        }
    }

    @Nested
    inner class Credits {
        @Test
        fun `adds existing person once even if added twice`() {
            val existing = movie()
            whenever(movies.findById(1)).thenReturn(Optional.of(existing))
            whenever(people.exists(7)).thenReturn(true)

            catalog.addCredit(1, 7)
            val result = catalog.addCredit(1, 7)

            assertEquals(setOf(7L), result.personIds)
        }

        @Test
        fun `rejects unknown person`() {
            whenever(movies.findById(1)).thenReturn(Optional.of(movie()))
            whenever(people.exists(7)).thenReturn(false)
            val e = assertThrows<NotFoundException> { catalog.addCredit(1, 7) }
            assertEquals("Person 7 not found", e.message)
        }

        @Test
        fun `does not call people-service when the movie is missing`() {
            whenever(movies.findById(1)).thenReturn(Optional.empty())
            assertThrows<NotFoundException> { catalog.addCredit(1, 7) }
            verify(people, never()).exists(any())
        }

        @Test
        fun `people-service failure is passed on`() {
            whenever(movies.findById(1)).thenReturn(Optional.of(movie()))
            whenever(people.exists(7)).thenThrow(StatusRuntimeException(Status.UNAVAILABLE))
            assertThrows<StatusRuntimeException> { catalog.addCredit(1, 7) }
        }

        @Test
        fun `removing a person who is not credited is harmless`() {
            val existing = movie().apply { personIds.add(3) }
            whenever(movies.findById(1)).thenReturn(Optional.of(existing))
            assertEquals(setOf(3L), catalog.removeCredit(1, 99).personIds)
            assertEquals(emptySet<Long>(), catalog.removeCredit(1, 3).personIds)
        }

        @Test
        fun `deletes all credits of a person`() {
            catalog.deleteCreditsForPerson(5)
            verify(movies).deleteCreditsForPerson(5)
        }
    }
}
