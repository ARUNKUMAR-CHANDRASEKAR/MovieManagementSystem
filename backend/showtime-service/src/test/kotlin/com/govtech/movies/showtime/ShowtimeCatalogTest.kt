package com.govtech.movies.showtime

import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class ShowtimeCatalogTest {
    private val now = Instant.parse("2026-10-12T04:00:00Z")
    private val theatres = mock<TheatreRepository>()
    private val showtimes = mock<ShowtimeRepository>()
    private val bookings = mock<BookingRepository>()
    private val movies = mock<MovieClient>()
    private val catalog = ShowtimeCatalog(theatres, showtimes, bookings, movies, Clock.fixed(now, ZoneOffset.UTC))

    private val orchard = Theatre("Orchard Cineplex", Country.SINGAPORE, "Orchard", 8, 12, id = 1)
    private val later = now.plus(Duration.ofHours(3))
    private fun show(startsAt: Instant = later, price: Int = 1350) = Showtime(7, orchard, startsAt, price, id = 50)

    @BeforeEach
    fun saveAssignsIds() {
        whenever(bookings.save(any<Booking>())).doAnswer { (it.arguments[0] as Booking).apply { id = 42 } }
        whenever(showtimes.save(any<Showtime>())).doAnswer { (it.arguments[0] as Showtime).apply { id = 51 } }
    }

    @Nested
    inner class Book {
        @BeforeEach
        fun showExists() {
            whenever(showtimes.findById(50)).thenReturn(Optional.of(show()))
            whenever(bookings.findSoldSeats(50)).thenReturn(listOf("A1"))
        }

        @Test
        fun `books seats, totals the price and normalises labels`() {
            whenever(bookings.findSoldSeats(50)).thenReturn(listOf("A1"), listOf("A1", "b2", "C10").map { it.uppercase() })

            val result = catalog.book(50, listOf(" c10", "b2 "), "  Arun  ")

            assertEquals(listOf("B2", "C10"), result.seats)
            assertEquals(2700, result.booking.totalCents)
            assertEquals("Arun", result.booking.customerName)
            assertEquals(now, result.booking.createdAt)
            verify(bookings).insertSeat(50, "C10", 42)
            verify(bookings).insertSeat(50, "B2", 42)
            assertEquals(listOf("A1", "B2", "C10"), result.showtime.soldSeats)
            assertEquals(96 - 3, result.showtime.seatsLeft)
        }

        @Test
        fun `rejects seats that are already sold`() {
            val e = assertThrows<InvalidInputException> { catalog.book(50, listOf("A2", "A1"), "Arun") }
            assertEquals("Already sold: A1", e.message)
            verify(bookings, never()).save(any<Booking>())
        }

        @Test
        fun `seat sold by someone else at the same moment rolls back with a clear message`() {
            doThrow(DataIntegrityViolationException("duplicate key")).whenever(bookings).insertSeat(eq(50), eq("A3"), any())
            val e = assertThrows<InvalidInputException> { catalog.book(50, listOf("A2", "A3"), "Arun") }
            assertEquals("Some of these seats were just booked by someone else. Please choose again.", e.message)
        }

        @Test
        fun `requires a name`() {
            assertEquals("Name is required", assertThrows<InvalidInputException> { catalog.book(50, listOf("A2"), "  ") }.message)
        }

        @Test
        fun `accepts a 100 character name and rejects 101`() {
            whenever(bookings.findSoldSeats(50)).thenReturn(emptyList())
            catalog.book(50, listOf("A2"), "n".repeat(100))
            assertThrows<InvalidInputException> { catalog.book(50, listOf("A3"), "n".repeat(101)) }
        }

        @Test
        fun `requires at least one seat`() {
            assertEquals("Choose at least one seat", assertThrows<InvalidInputException> { catalog.book(50, emptyList(), "Arun") }.message)
        }

        @Test
        fun `allows 8 seats and rejects 9`() {
            whenever(bookings.findSoldSeats(50)).thenReturn(emptyList())
            catalog.book(50, (1..8).map { "B$it" }, "Arun")
            val e = assertThrows<InvalidInputException> { catalog.book(50, (1..9).map { "C$it" }, "Arun") }
            assertEquals("You can book at most 8 seats at a time", e.message)
        }

        @Test
        fun `rejects the same seat twice, even with different case`() {
            val e = assertThrows<InvalidInputException> { catalog.book(50, listOf("a2", "A2"), "Arun") }
            assertEquals("Seat A2 is listed more than once", e.message)
        }

        @Test
        fun `rejects seats outside the theatre`() {
            val e = assertThrows<InvalidInputException> { catalog.book(50, listOf("A2", "I1"), "Arun") }
            assertEquals("Seat I1 does not exist in this theatre", e.message)
        }

        @Test
        fun `rejects shows that have started`() {
            whenever(showtimes.findById(50)).thenReturn(Optional.of(show(startsAt = now)))
            val e = assertThrows<InvalidInputException> { catalog.book(50, listOf("A2"), "Arun") }
            assertEquals("This show has already started", e.message)
        }

        @Test
        fun `unknown show is not found`() {
            whenever(showtimes.findById(9)).thenReturn(Optional.empty())
            assertEquals("Show 9 not found", assertThrows<NotFoundException> { catalog.book(9, listOf("A2"), "Arun") }.message)
        }
    }

    @Nested
    inner class Create {
        @BeforeEach
        fun theatreAndMovieExist() {
            whenever(theatres.findById(1)).thenReturn(Optional.of(orchard))
            whenever(movies.exists(7)).thenReturn(true)
        }

        @Test
        fun `creates a show with no seats sold`() {
            val info = catalog.create(7, 1, later, 1350)
            assertEquals(7, info.showtime.movieId)
            assertEquals(96, info.seatsLeft)
        }

        @Test
        fun `rejects unknown movie, checked with movie-service`() {
            whenever(movies.exists(8)).thenReturn(false)
            assertEquals("Movie 8 not found", assertThrows<NotFoundException> { catalog.create(8, 1, later, 1350) }.message)
            verify(showtimes, never()).save(any<Showtime>())
        }

        @Test
        fun `movie-service failure is passed on`() {
            whenever(movies.exists(7)).thenThrow(StatusRuntimeException(Status.UNAVAILABLE))
            assertThrows<StatusRuntimeException> { catalog.create(7, 1, later, 1350) }
        }

        @Test
        fun `rejects unknown theatre without calling movie-service`() {
            whenever(theatres.findById(2)).thenReturn(Optional.empty())
            assertThrows<NotFoundException> { catalog.create(7, 2, later, 1350) }
            verify(movies, never()).exists(any())
        }

        @Test
        fun `rejects past or current start time`() {
            assertThrows<InvalidInputException> { catalog.create(7, 1, now, 1350) }
            assertThrows<InvalidInputException> { catalog.create(7, 1, now.minusSeconds(60), 1350) }
        }

        @Test
        fun `price must be between 1 cent and 1000 dollars`() {
            catalog.create(7, 1, later, 1)
            catalog.create(7, 1, later, 100_000)
            assertThrows<InvalidInputException> { catalog.create(7, 1, later, 0) }
            assertThrows<InvalidInputException> { catalog.create(7, 1, later, 100_001) }
        }
    }

    @Nested
    inner class Listing {
        @Test
        fun `country is required to find movies showing`() {
            assertThrows<InvalidInputException> { catalog.movieIdsShowing(null) }
        }

        @Test
        fun `movies showing only counts shows after now`() {
            whenever(showtimes.findMovieIdsShowing(Country.MALAYSIA, now)).thenReturn(listOf(9, 3))
            assertEquals(listOf(3L, 9L), catalog.movieIdsShowing(Country.MALAYSIA))
        }

        @Test
        fun `showtimes include seats left`() {
            whenever(showtimes.findByMovieIdAndTheatreCountryAndStartsAtAfter(eq(7), eq(Country.SINGAPORE), eq(now), any()))
                .thenReturn(listOf(show()))
            whenever(bookings.countSoldSeats(listOf(50L))).thenReturn(listOf(soldCount(50, 10)))

            assertEquals(86, catalog.showtimes(7, Country.SINGAPORE).single().seatsLeft)
        }

        @Test
        fun `no shows means no seat count query`() {
            whenever(showtimes.findByMovieIdAndStartsAtAfter(eq(7), eq(now), any())).thenReturn(emptyList())
            assertEquals(emptyList<ShowtimeInfo>(), catalog.showtimes(7, null))
            verify(bookings, times(0)).countSoldSeats(anyOrNull())
        }

        private fun soldCount(id: Long, count: Long) = object : SoldCount {
            override val showtimeId = id
            override val sold = count
        }
    }
}
