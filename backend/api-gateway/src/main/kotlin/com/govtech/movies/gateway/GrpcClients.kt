package com.govtech.movies.gateway

import com.govtech.movies.grpc.movie.MovieServiceGrpc.MovieServiceBlockingStub
import com.govtech.movies.grpc.movie.MovieServiceGrpc
import com.govtech.movies.grpc.people.PeopleServiceGrpc.PeopleServiceBlockingStub
import com.govtech.movies.grpc.people.PeopleServiceGrpc
import com.govtech.movies.grpc.showtime.ShowtimeServiceGrpc
import com.govtech.movies.grpc.showtime.ShowtimeServiceGrpc.ShowtimeServiceBlockingStub
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * gRPC clients for the backend services. Each call gets a fresh deadline, so a hung
 * service makes the request fail after a few seconds instead of hanging the user's page.
 */
@Component
class GrpcClients(
    @Value("\${movie-service.host}") movieHost: String,
    @Value("\${movie-service.port}") moviePort: Int,
    @Value("\${people-service.host}") peopleHost: String,
    @Value("\${people-service.port}") peoplePort: Int,
    @Value("\${showtime-service.host}") showtimeHost: String,
    @Value("\${showtime-service.port}") showtimePort: Int,
    @Value("\${grpc.max-message-bytes}") private val maxMessageBytes: Int,
    @Value("\${grpc.deadline-seconds}") private val deadlineSeconds: Long,
) : AutoCloseable {
    private val movieChannel = channel(movieHost, moviePort)
    private val peopleChannel = channel(peopleHost, peoplePort)
    private val showtimeChannel = channel(showtimeHost, showtimePort)
    private val movieStub = MovieServiceGrpc.newBlockingStub(movieChannel)
    private val peopleStub = PeopleServiceGrpc.newBlockingStub(peopleChannel)
    private val showtimeStub = ShowtimeServiceGrpc.newBlockingStub(showtimeChannel)

    fun movies(): MovieServiceBlockingStub = movieStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)

    fun people(): PeopleServiceBlockingStub = peopleStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)

    fun showtimes(): ShowtimeServiceBlockingStub = showtimeStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)

    private fun channel(host: String, port: Int): ManagedChannel =
        ManagedChannelBuilder.forAddress(host, port).usePlaintext().maxInboundMessageSize(maxMessageBytes).build()

    override fun close() {
        movieChannel.shutdown()
        peopleChannel.shutdown()
        showtimeChannel.shutdown()
    }
}
