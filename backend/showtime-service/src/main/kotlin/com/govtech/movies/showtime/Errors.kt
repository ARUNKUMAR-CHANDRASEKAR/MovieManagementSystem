package com.govtech.movies.showtime

import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory

class NotFoundException(message: String) : RuntimeException(message)

class InvalidInputException(message: String) : RuntimeException(message)

private val log = LoggerFactory.getLogger("com.govtech.movies.showtime.Grpc")

/**
 * Runs [block] and sends its result to [observer]. Domain exceptions become gRPC status codes,
 * so callers can tell "not found" and "bad input" apart from real failures.
 */
fun <T> respond(observer: StreamObserver<T>, block: () -> T) {
    val result = try {
        block()
    } catch (e: NotFoundException) {
        return observer.onError(Status.NOT_FOUND.withDescription(e.message).asRuntimeException())
    } catch (e: InvalidInputException) {
        return observer.onError(Status.INVALID_ARGUMENT.withDescription(e.message).asRuntimeException())
    } catch (e: StatusRuntimeException) {
        // A downstream gRPC call failed (for example people-service is down). Pass its status on.
        return observer.onError(e)
    } catch (e: Exception) {
        log.error("Unexpected error in gRPC call", e)
        return observer.onError(Status.INTERNAL.withDescription("Internal error").asRuntimeException())
    }
    observer.onNext(result)
    observer.onCompleted()
}
