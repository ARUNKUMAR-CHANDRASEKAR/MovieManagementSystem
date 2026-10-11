package com.govtech.movies.gateway

import com.govtech.movies.grpc.movie.Artwork
import com.govtech.movies.grpc.movie.MovieServiceGrpc.MovieServiceBlockingStub
import com.google.protobuf.ByteString
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

class ArtworkControllerTest {
    private val stub = mock<MovieServiceBlockingStub>()
    private val grpc = mock<GrpcClients>()
    private val controller = ArtworkController(grpc)

    @Test
    fun `serves image with type, long cache and nosniff`() {
        whenever(grpc.movies()).thenReturn(stub)
        whenever(stub.getArtwork(any())).thenReturn(
            Artwork.newBuilder().setContentType("image/png").setData(ByteString.copyFrom(byteArrayOf(1, 2))).build(),
        )

        val response = controller.artwork(1)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MediaType.IMAGE_PNG, response.headers.contentType)
        assertEquals("nosniff", response.headers.getFirst("X-Content-Type-Options"))
        assertEquals("max-age=31536000, public, immutable", response.headers.cacheControl)
        assertArrayEquals(byteArrayOf(1, 2), response.body)
    }

    @Test
    fun `missing artwork is 404`() {
        whenever(grpc.movies()).thenReturn(stub)
        whenever(stub.getArtwork(any())).thenThrow(StatusRuntimeException(Status.NOT_FOUND))
        assertEquals(HttpStatus.NOT_FOUND, controller.artwork(1).statusCode)
    }

    @Test
    fun `other errors are rethrown`() {
        whenever(grpc.movies()).thenReturn(stub)
        whenever(stub.getArtwork(any())).thenThrow(StatusRuntimeException(Status.UNAVAILABLE))
        assertThrows<StatusRuntimeException> { controller.artwork(1) }
    }
}
