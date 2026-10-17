package com.govtech.movies.showtime

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

enum class Country(val currency: String) {
    SINGAPORE("SGD"),
    MALAYSIA("MYR"),
}

@Entity
@Table(name = "theatre")
class Theatre(
    var name: String,
    @Enumerated(EnumType.STRING)
    var country: Country,
    var location: String,
    var seatRows: Int,
    var seatsPerRow: Int,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
    val capacity get() = seatRows * seatsPerRow
}

@Entity
@Table(name = "showtime")
class Showtime(
    var movieId: Long,
    // Every response shows the theatre, so load it with the show.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "theatre_id")
    var theatre: Theatre,
    var startsAt: Instant,
    var priceCents: Int,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)

@Entity
@Table(name = "booking")
class Booking(
    var showtimeId: Long,
    var customerName: String,
    var totalCents: Int,
    var createdAt: Instant,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)

interface TheatreRepository : JpaRepository<Theatre, Long> {
    fun findByCountry(country: Country, sort: Sort): List<Theatre>
}

interface ShowtimeRepository : JpaRepository<Showtime, Long> {
    @Query("select distinct s.movieId from Showtime s where s.theatre.country = :country and s.startsAt > :after")
    fun findMovieIdsShowing(country: Country, after: Instant): List<Long>

    fun findByMovieIdAndStartsAtAfter(movieId: Long, after: Instant, sort: Sort): List<Showtime>

    fun findByMovieIdAndTheatreCountryAndStartsAtAfter(
        movieId: Long,
        country: Country,
        after: Instant,
        sort: Sort,
    ): List<Showtime>
}

/** Row of [BookingRepository.countSoldSeats]. */
interface SoldCount {
    val showtimeId: Long
    val sold: Long
}

interface BookingRepository : JpaRepository<Booking, Long> {
    // A plain INSERT, not save(): save() would silently UPDATE a seat row that already exists.
    // Inserting a seat that is already sold breaks the primary key and throws instead.
    @Modifying
    @Query(
        value = "insert into booked_seat (showtime_id, seat, booking_id) values (:showtimeId, :seat, :bookingId)",
        nativeQuery = true,
    )
    fun insertSeat(showtimeId: Long, seat: String, bookingId: Long)

    @Query(value = "select seat from booked_seat where showtime_id = :showtimeId", nativeQuery = true)
    fun findSoldSeats(showtimeId: Long): List<String>

    @Query(
        value = "select showtime_id as showtimeId, count(*) as sold from booked_seat " +
            "where showtime_id in (:showtimeIds) group by showtime_id",
        nativeQuery = true,
    )
    fun countSoldSeats(showtimeIds: Collection<Long>): List<SoldCount>
}
