package com.govtech.movies.gateway

import com.govtech.movies.grpc.movie.MovieIdRequest
import com.govtech.movies.grpc.movie.MovieIdsRequest
import com.govtech.movies.grpc.showtime.BookSeatsRequest
import com.govtech.movies.grpc.showtime.Country
import com.govtech.movies.grpc.showtime.CountryRequest
import com.govtech.movies.grpc.showtime.CreateShowtimeRequest
import com.govtech.movies.grpc.showtime.ListShowtimesRequest
import com.govtech.movies.grpc.showtime.ShowtimeIdRequest
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

data class ShowtimeInputArg(val movieId: String, val theatreId: String, val startsAt: String, val priceCents: Int)

@Controller
class ShowtimeController(private val grpc: GrpcClients) {

    /** Two gRPC calls: showtime-service knows which movies are showing, movie-service has their details. */
    @QueryMapping
    fun moviesShowing(@Argument country: Country): List<MovieView> {
        val ids = grpc.showtimes().listMovieIdsShowing(CountryRequest.newBuilder().setCountry(country).build()).movieIdsList
        if (ids.isEmpty()) return emptyList()
        return grpc.movies().getMoviesByIds(MovieIdsRequest.newBuilder().addAllIds(ids).build())
            .moviesList.map { it.toView() }.sortedBy { it.title.lowercase() }
    }

    @QueryMapping
    fun theatres(@Argument country: Country?): List<TheatreView> =
        grpc.showtimes().listTheatres(countryRequest(country)).theatresList.map { it.toView() }

    @QueryMapping
    fun showtimes(@Argument movieId: String, @Argument country: Country?): List<ShowtimeView> {
        val request = ListShowtimesRequest.newBuilder()
            .setMovieId(parseId(movieId))
            .setCountry(country ?: Country.COUNTRY_UNSPECIFIED)
            .build()
        return grpc.showtimes().listShowtimes(request).showtimesList.map { it.toView() }
    }

    @QueryMapping
    fun showtime(@Argument id: String): ShowtimeView =
        grpc.showtimes().getShowtime(ShowtimeIdRequest.newBuilder().setId(parseId(id)).build()).toView()

    @MutationMapping
    fun createShowtime(@Argument input: ShowtimeInputArg): ShowtimeView {
        val startsAt = try {
            OffsetDateTime.parse(input.startsAt).toInstant()
        } catch (e: DateTimeParseException) {
            throw BadInputException("startsAt must be a date-time with offset, e.g. 2026-10-12T19:30:00+08:00")
        }
        val request = CreateShowtimeRequest.newBuilder()
            .setMovieId(parseId(input.movieId))
            .setTheatreId(parseId(input.theatreId))
            .setStartsAtEpochMs(startsAt.toEpochMilli())
            .setPriceCents(input.priceCents)
            .build()
        return grpc.showtimes().createShowtime(request).toView()
    }

    @MutationMapping
    fun bookSeats(
        @Argument showtimeId: String,
        @Argument seats: List<String>,
        @Argument customerName: String,
    ): BookingView {
        val request = BookSeatsRequest.newBuilder()
            .setShowtimeId(parseId(showtimeId))
            .addAllSeats(seats)
            .setCustomerName(customerName)
            .build()
        val booking = grpc.showtimes().bookSeats(request)
        return BookingView(
            reference = booking.reference,
            seats = booking.seatsList,
            totalCents = booking.totalCents,
            currency = booking.currency,
            customerName = booking.customerName,
            showtime = booking.showtime.toView(),
        )
    }

    /** Showtime.movie batched to avoid N+1 queries. */
    @BatchMapping(typeName = "Showtime")
    fun movie(showtimes: List<ShowtimeView>): Map<ShowtimeView, MovieView?> {
        val ids = showtimes.map { it.movieId }.distinct()
        val movies = grpc.movies().getMoviesByIds(MovieIdsRequest.newBuilder().addAllIds(ids).build())
            .moviesList.associateBy { it.id }
        return showtimes.associateWith { movies[it.movieId]?.toView() }
    }

    private fun countryRequest(country: Country?) =
        CountryRequest.newBuilder().setCountry(country ?: Country.COUNTRY_UNSPECIFIED).build()
}
