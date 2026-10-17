package com.govtech.movies.showtime

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/** A show with how many seats are sold. [soldSeats] is only filled when the seat map is needed. */
data class ShowtimeInfo(
    val showtime: Showtime,
    val soldCount: Int,
    val soldSeats: List<String> = emptyList(),
) {
    val seatsLeft get() = showtime.theatre.capacity - soldCount
}

data class BookingResult(
    val booking: Booking,
    val showtime: ShowtimeInfo,
    val seats: List<String>,
)

/** Business rules for theatres, shows and seat bookings. */
@Service
class ShowtimeCatalog(
    private val theatres: TheatreRepository,
    private val showtimes: ShowtimeRepository,
    private val bookings: BookingRepository,
    private val movies: MovieClient,
    private val clock: Clock,
) {
    companion object {
        const val MAX_SEATS_PER_BOOKING = 8
        const val MAX_NAME_LENGTH = 100
        const val MAX_PRICE_CENTS = 100_000
        private val BY_PLACE_THEN_TIME = Sort.by("theatre.country", "theatre.location", "startsAt", "id")
    }

    /** [country] null lists all theatres. */
    @Transactional(readOnly = true)
    fun theatres(country: Country?): List<Theatre> {
        val sort = Sort.by("country", "location")
        return if (country == null) theatres.findAll(sort) else theatres.findByCountry(country, sort)
    }

    @Transactional(readOnly = true)
    fun movieIdsShowing(country: Country?): List<Long> {
        if (country == null) throw InvalidInputException("Country is required")
        return showtimes.findMovieIdsShowing(country, clock.instant()).sorted()
    }

    /** Upcoming shows of a movie. [country] null lists all countries. */
    @Transactional(readOnly = true)
    fun showtimes(movieId: Long, country: Country?): List<ShowtimeInfo> {
        val now = clock.instant()
        val list = if (country == null) {
            showtimes.findByMovieIdAndStartsAtAfter(movieId, now, BY_PLACE_THEN_TIME)
        } else {
            showtimes.findByMovieIdAndTheatreCountryAndStartsAtAfter(movieId, country, now, BY_PLACE_THEN_TIME)
        }
        if (list.isEmpty()) return emptyList()
        val sold = bookings.countSoldSeats(list.map { it.id!! }).associate { it.showtimeId to it.sold.toInt() }
        return list.map { ShowtimeInfo(it, sold[it.id] ?: 0) }
    }

    /** One show with its sold seats, for the seat map. */
    @Transactional(readOnly = true)
    fun get(id: Long): ShowtimeInfo {
        val showtime = showtimes.findById(id).orElseThrow { NotFoundException("Show $id not found") }
        val sold = bookings.findSoldSeats(id).sortedWith(seatOrder)
        return ShowtimeInfo(showtime, sold.size, sold)
    }

    @Transactional
    fun create(movieId: Long, theatreId: Long, startsAt: Instant, priceCents: Int): ShowtimeInfo {
        if (priceCents !in 1..MAX_PRICE_CENTS) throw InvalidInputException("Price must be between 0.01 and 1000.00")
        if (!startsAt.isAfter(clock.instant())) throw InvalidInputException("Show time must be in the future")
        val theatre = theatres.findById(theatreId).orElseThrow { NotFoundException("Theatre $theatreId not found") }
        // Service-to-service gRPC call: movies live in movie-service's database.
        if (!movies.exists(movieId)) throw NotFoundException("Movie $movieId not found")
        return ShowtimeInfo(showtimes.save(Showtime(movieId, theatre, startsAt, priceCents)), soldCount = 0)
    }

    /** Sample booking: no payment. All seats are booked, or none (one transaction). */
    @Transactional
    fun book(showtimeId: Long, seats: List<String>, customerName: String): BookingResult {
        val name = customerName.trim()
        if (name.isEmpty()) throw InvalidInputException("Name is required")
        if (name.length > MAX_NAME_LENGTH) throw InvalidInputException("Name must be $MAX_NAME_LENGTH characters or fewer")
        val wanted = seats.map { it.trim().uppercase() }
        if (wanted.isEmpty()) throw InvalidInputException("Choose at least one seat")
        if (wanted.size > MAX_SEATS_PER_BOOKING) {
            throw InvalidInputException("You can book at most $MAX_SEATS_PER_BOOKING seats at a time")
        }
        wanted.groupBy { it }.filterValues { it.size > 1 }.keys.firstOrNull()?.let {
            throw InvalidInputException("Seat $it is listed more than once")
        }

        val showtime = showtimes.findById(showtimeId).orElseThrow { NotFoundException("Show $showtimeId not found") }
        if (showtime.startsAt <= clock.instant()) throw InvalidInputException("This show has already started")
        val theatre = showtime.theatre
        wanted.firstOrNull { !isValidSeat(it, theatre.seatRows, theatre.seatsPerRow) }?.let {
            throw InvalidInputException("Seat $it does not exist in this theatre")
        }
        val soldBefore = bookings.findSoldSeats(showtimeId)
        val alreadySold = soldBefore.toSet().intersect(wanted.toSet())
        if (alreadySold.isNotEmpty()) {
            throw InvalidInputException("Already sold: ${alreadySold.sortedWith(seatOrder).joinToString()}")
        }

        val booking = bookings.save(Booking(showtimeId, name, showtime.priceCents * wanted.size, clock.instant()))
        try {
            wanted.forEach { bookings.insertSeat(showtimeId, it, booking.id!!) }
        } catch (e: DataIntegrityViolationException) {
            // Someone else booked one of these seats between our check and our insert.
            // Throwing rolls back the whole booking.
            throw InvalidInputException("Some of these seats were just booked by someone else. Please choose again.")
        }
        val sold = (soldBefore + wanted).sortedWith(seatOrder)
        return BookingResult(booking, ShowtimeInfo(showtime, sold.size, sold), wanted.sortedWith(seatOrder))
    }
}
