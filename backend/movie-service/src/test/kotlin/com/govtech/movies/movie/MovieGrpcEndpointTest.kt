package com.govtech.movies.movie

import com.govtech.movies.grpc.movie.MovieIdRequest
import com.govtech.movies.grpc.movie.MovieInput
import com.govtech.movies.grpc.movie.MovieServiceGrpc
import com.govtech.movies.grpc.movie.ListMoviesRequest
import com.govtech.movies.grpc.movie.Region as RegionMessage
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant

/** Runs the real gRPC endpoint in memory to check message mapping and status codes. */
class MovieGrpcEndpointTest {
    private val catalog = mock<MovieCatalog>()
    private val name = InProcessServerBuilder.generateName()
    private val server: Server = InProcessServerBuilder.forName(name).directExecutor()
        .addService(MovieGrpcEndpoint(catalog)).build().start()
    private val channel: ManagedChannel = InProcessChannelBuilder.forName(name).directExecutor().build()
    private val stub = MovieServiceGrpc.newBlockingStub(channel)

    @AfterEach
    fun close() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    private fun movie(year: Int?, artworkAt: Instant?) =
        Movie("Leo", year, "Action", "", Region.INDIA, artworkAt, Instant.EPOCH, Instant.EPOCH, id = 5).apply { personIds.addAll(listOf(9, 2)) }

    @Test
    fun `maps a movie to a message`() {
        whenever(catalog.get(5)).thenReturn(movie(2023, Instant.ofEpochMilli(1234)))

        val reply = stub.getMovie(MovieIdRequest.newBuilder().setId(5).build())

        assertEquals("Leo", reply.title)
        assertEquals(2023, reply.releaseYear)
        assertEquals(1234, reply.artworkUpdatedAtMs)
        assertEquals(listOf(2L, 9L), reply.personIdsList)
        assertEquals(RegionMessage.INDIA, reply.region)
    }

    @Test
    fun `missing year and artwork are sent as unset`() {
        whenever(catalog.get(5)).thenReturn(movie(null, null))
        val reply = stub.getMovie(MovieIdRequest.newBuilder().setId(5).build())
        assertFalse(reply.hasReleaseYear())
        assertEquals(0, reply.artworkUpdatedAtMs)
    }

    @Test
    fun `unset release year in input becomes null`() {
        val draft = MovieDraft("Leo", null, "", "")
        whenever(catalog.create(draft)).thenReturn(movie(null, null))
        stub.createMovie(MovieInput.newBuilder().setTitle("Leo").build())
        verify(catalog).create(draft)
    }

    @Test
    fun `list returns page metadata`() {
        whenever(catalog.list("le", null, 1, 2)).thenReturn(PageImpl(listOf(movie(null, null)), PageRequest.of(1, 2), 5))
        val reply = stub.listMovies(ListMoviesRequest.newBuilder().setSearch("le").setPage(1).setSize(2).build())
        assertEquals(5, reply.totalCount)
        assertEquals(1, reply.moviesCount)
    }

    @Test
    fun `region filter is passed to the catalog`() {
        whenever(catalog.list("", Region.SINGAPORE, 0, 5)).thenReturn(PageImpl(emptyList()))
        stub.listMovies(ListMoviesRequest.newBuilder().setRegion(RegionMessage.SINGAPORE).setSize(5).build())
        verify(catalog).list("", Region.SINGAPORE, 0, 5)
    }

    @Test
    fun `region mapping covers every proto value`() {
        assertEquals(null, RegionMessage.REGION_UNSPECIFIED.toRegion())
        assertEquals(null, RegionMessage.UNRECOGNIZED.toRegion())
        Region.entries.forEach { assertEquals(it, it.toMessage().toRegion()) }
        assertEquals(RegionMessage.REGION_UNSPECIFIED, (null as Region?).toMessage())
    }

    @Test
    fun `not found becomes NOT_FOUND`() {
        whenever(catalog.get(5)).thenThrow(NotFoundException("Movie 5 not found"))
        val e = assertThrows<StatusRuntimeException> { stub.getMovie(MovieIdRequest.newBuilder().setId(5).build()) }
        assertEquals(Status.Code.NOT_FOUND, e.status.code)
        assertEquals("Movie 5 not found", e.status.description)
    }

    @Test
    fun `invalid input becomes INVALID_ARGUMENT`() {
        whenever(catalog.create(MovieDraft("", null, "", ""))).thenThrow(InvalidInputException("Title is required"))
        val e = assertThrows<StatusRuntimeException> { stub.createMovie(MovieInput.getDefaultInstance()) }
        assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
    }

    @Test
    fun `unexpected errors become INTERNAL without leaking details`() {
        whenever(catalog.get(5)).thenThrow(IllegalStateException("db password is hunter2"))
        val e = assertThrows<StatusRuntimeException> { stub.getMovie(MovieIdRequest.newBuilder().setId(5).build()) }
        assertEquals(Status.Code.INTERNAL, e.status.code)
        assertEquals("Internal error", e.status.description)
    }

    @Test
    fun `downstream gRPC errors keep their status`() {
        whenever(catalog.addCredit(1, 2)).thenThrow(StatusRuntimeException(Status.UNAVAILABLE))
        val e = assertThrows<StatusRuntimeException> {
            stub.addCredit(com.govtech.movies.grpc.movie.CreditRequest.newBuilder().setMovieId(1).setPersonId(2).build())
        }
        assertEquals(Status.Code.UNAVAILABLE, e.status.code)
    }
}
