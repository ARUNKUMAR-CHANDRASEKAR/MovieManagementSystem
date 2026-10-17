package com.govtech.movies.showtime

import com.govtech.movies.grpc.showtime.BookSeatsRequest
import com.govtech.movies.grpc.showtime.CountryRequest
import com.govtech.movies.grpc.showtime.CreateShowtimeRequest
import com.govtech.movies.grpc.showtime.ListShowtimesRequest
import com.govtech.movies.grpc.showtime.MovieIdList
import com.govtech.movies.grpc.showtime.ShowtimeIdRequest
import com.govtech.movies.grpc.showtime.ShowtimeList
import com.govtech.movies.grpc.showtime.ShowtimeServiceGrpc
import com.govtech.movies.grpc.showtime.TheatreList
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Component
import java.time.Instant
import com.govtech.movies.grpc.showtime.Booking as BookingMessage
import com.govtech.movies.grpc.showtime.Country as CountryMessage
import com.govtech.movies.grpc.showtime.Showtime as ShowtimeMessage
import com.govtech.movies.grpc.showtime.Theatre as TheatreMessage

/** gRPC adapter: converts protobuf messages to and from domain objects and calls [ShowtimeCatalog]. */
@Component
class ShowtimeGrpcEndpoint(private val catalog: ShowtimeCatalog) : ShowtimeServiceGrpc.ShowtimeServiceImplBase() {

    override fun listTheatres(request: CountryRequest, observer: StreamObserver<TheatreList>) = respond(observer) {
        TheatreList.newBuilder().addAllTheatres(catalog.theatres(request.country.toCountry()).map { it.toMessage() }).build()
    }

    override fun listMovieIdsShowing(request: CountryRequest, observer: StreamObserver<MovieIdList>) =
        respond(observer) {
            MovieIdList.newBuilder().addAllMovieIds(catalog.movieIdsShowing(request.country.toCountry())).build()
        }

    override fun listShowtimes(request: ListShowtimesRequest, observer: StreamObserver<ShowtimeList>) =
        respond(observer) {
            val list = catalog.showtimes(request.movieId, request.country.toCountry())
            ShowtimeList.newBuilder().addAllShowtimes(list.map { it.toMessage() }).build()
        }

    override fun getShowtime(request: ShowtimeIdRequest, observer: StreamObserver<ShowtimeMessage>) =
        respond(observer) { catalog.get(request.id).toMessage() }

    override fun createShowtime(request: CreateShowtimeRequest, observer: StreamObserver<ShowtimeMessage>) =
        respond(observer) {
            val startsAt = Instant.ofEpochMilli(request.startsAtEpochMs)
            catalog.create(request.movieId, request.theatreId, startsAt, request.priceCents).toMessage()
        }

    override fun bookSeats(request: BookSeatsRequest, observer: StreamObserver<BookingMessage>) = respond(observer) {
        val result = catalog.book(request.showtimeId, request.seatsList, request.customerName)
        BookingMessage.newBuilder()
            .setId(result.booking.id!!)
            .setReference("BK-%06d".format(result.booking.id))
            .setShowtime(result.showtime.toMessage())
            .addAllSeats(result.seats)
            .setTotalCents(result.booking.totalCents)
            .setCurrency(result.showtime.showtime.theatre.country.currency)
            .setCustomerName(result.booking.customerName)
            .build()
    }
}

/** COUNTRY_UNSPECIFIED (and unknown values from newer clients) map to null. */
fun CountryMessage.toCountry(): Country? = when (this) {
    CountryMessage.SINGAPORE -> Country.SINGAPORE
    CountryMessage.MALAYSIA -> Country.MALAYSIA
    else -> null
}

fun Country.toMessage(): CountryMessage = when (this) {
    Country.SINGAPORE -> CountryMessage.SINGAPORE
    Country.MALAYSIA -> CountryMessage.MALAYSIA
}

fun Theatre.toMessage(): TheatreMessage = TheatreMessage.newBuilder()
    .setId(id!!)
    .setName(name)
    .setCountry(country.toMessage())
    .setLocation(location)
    .setSeatRows(seatRows)
    .setSeatsPerRow(seatsPerRow)
    .build()

fun ShowtimeInfo.toMessage(): ShowtimeMessage = ShowtimeMessage.newBuilder()
    .setId(showtime.id!!)
    .setMovieId(showtime.movieId)
    .setTheatre(showtime.theatre.toMessage())
    .setStartsAtEpochMs(showtime.startsAt.toEpochMilli())
    .setPriceCents(showtime.priceCents)
    .setCurrency(showtime.theatre.country.currency)
    .setSeatsLeft(seatsLeft)
    .addAllSoldSeats(soldSeats)
    .build()
