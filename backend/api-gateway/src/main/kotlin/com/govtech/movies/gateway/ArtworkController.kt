package com.govtech.movies.gateway

import com.govtech.movies.grpc.movie.MovieIdRequest
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

/**
 * Serves artwork images. This is the one plain HTTP endpoint: an <img> tag needs a URL it can
 * GET, and GraphQL returns JSON. All data reads and writes still go through GraphQL.
 */
@RestController
class ArtworkController(private val grpc: GrpcClients) {

    @GetMapping("/api/movies/{id}/artwork")
    fun artwork(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val artwork = try {
            grpc.movies().getArtwork(MovieIdRequest.newBuilder().setId(id).build())
        } catch (e: StatusRuntimeException) {
            if (e.status.code == Status.Code.NOT_FOUND) return ResponseEntity.notFound().build()
            throw e
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(artwork.contentType))
            // The URL contains the artwork version, so the image at a URL never changes.
            .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
            .header("X-Content-Type-Options", "nosniff")
            .body(artwork.data.toByteArray())
    }
}
