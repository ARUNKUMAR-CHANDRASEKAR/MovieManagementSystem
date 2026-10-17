package com.govtech.movies.showtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Sort
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Runs the repositories against a real PostgreSQL (Docker) with the real migrations, including the sample theatres. */
@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=validate"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ShowtimeRepositoryIntegrationTest {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }

    @Autowired lateinit var theatres: TheatreRepository
    @Autowired lateinit var showtimes: ShowtimeRepository
    @Autowired lateinit var bookings: BookingRepository
    @Autowired lateinit var entities: TestEntityManager

    private val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)

    private fun theatre(location: String) = theatres.findAll().single { it.location == location }

    private fun show(movieId: Long, location: String, hoursFromNow: Long) =
        showtimes.save(Showtime(movieId, theatre(location), now.plus(hoursFromNow, ChronoUnit.HOURS), 1350))

    @Test
    fun `sample theatres are loaded, four per country`() {
        val singapore = theatres.findByCountry(Country.SINGAPORE, Sort.by("location")).map { it.location }
        assertEquals(listOf("Bishan", "Orchard", "Tampines", "Yishun"), singapore)
        assertEquals(4, theatres.findByCountry(Country.MALAYSIA, Sort.by("location")).size)
        assertEquals(96, theatre("Orchard").capacity)
    }

    @Test
    fun `movies showing are per country and only upcoming`() {
        show(1, "Orchard", 2)
        show(1, "Yishun", 5)
        show(2, "Penang", 2)
        show(3, "Bishan", -2) // already started
        entities.flush()

        assertEquals(listOf(1L), showtimes.findMovieIdsShowing(Country.SINGAPORE, now).sorted())
        assertEquals(listOf(2L), showtimes.findMovieIdsShowing(Country.MALAYSIA, now))
    }

    @Test
    fun `showtimes of a movie are filtered by country and sorted by location then time`() {
        show(1, "Yishun", 5)
        show(1, "Orchard", 9)
        show(1, "Orchard", 2)
        show(1, "Penang", 1)
        show(2, "Orchard", 3)
        entities.flush()
        val sort = Sort.by("theatre.country", "theatre.location", "startsAt")

        val singapore = showtimes.findByMovieIdAndTheatreCountryAndStartsAtAfter(1, Country.SINGAPORE, now, sort)

        assertEquals(listOf("Orchard", "Orchard", "Yishun"), singapore.map { it.theatre.location })
        assertEquals(true, singapore[0].startsAt < singapore[1].startsAt)
        assertEquals(4, showtimes.findByMovieIdAndStartsAtAfter(1, now, sort).size)
    }

    @Test
    fun `sold seats are listed and counted per show`() {
        val a = show(1, "Orchard", 2)
        val b = show(1, "Orchard", 4)
        val booking = bookings.save(Booking(a.id!!, "Arun", 2700, now))
        bookings.insertSeat(a.id!!, "A1", booking.id!!)
        bookings.insertSeat(a.id!!, "A2", booking.id!!)
        entities.flush()

        assertEquals(setOf("A1", "A2"), bookings.findSoldSeats(a.id!!).toSet())
        assertEquals(emptyList<String>(), bookings.findSoldSeats(b.id!!))
        val counts = bookings.countSoldSeats(listOf(a.id!!, b.id!!)).associate { it.showtimeId to it.sold }
        assertEquals(mapOf(a.id!! to 2L), counts)
    }

    @Test
    fun `database refuses to sell the same seat twice`() {
        val a = show(1, "Orchard", 2)
        val first = bookings.save(Booking(a.id!!, "First", 1350, now))
        val second = bookings.save(Booking(a.id!!, "Second", 1350, now))
        bookings.insertSeat(a.id!!, "C5", first.id!!)

        assertThrows<DataIntegrityViolationException> { bookings.insertSeat(a.id!!, "C5", second.id!!) }
    }

    @Test
    fun `the same seat can be sold for different shows`() {
        val a = show(1, "Orchard", 2)
        val b = show(1, "Orchard", 4)
        val booking = bookings.save(Booking(a.id!!, "Arun", 1350, now))
        bookings.insertSeat(a.id!!, "C5", booking.id!!)
        bookings.insertSeat(b.id!!, "C5", booking.id!!)
        assertEquals(listOf("C5"), bookings.findSoldSeats(b.id!!))
    }
}
