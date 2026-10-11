package com.govtech.movies.movie

import com.govtech.movies.grpc.people.PeopleServiceGrpc
import com.govtech.movies.grpc.people.PersonIdRequest
import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Starts a gRPC server with every gRPC service bean when Spring starts, and stops it on shutdown. */
@Component
class GrpcServer(
    services: List<BindableService>,
    @Value("\${grpc.port}") private val port: Int,
    @Value("\${grpc.max-message-bytes}") maxMessageBytes: Int,
) : SmartLifecycle {
    private val log = LoggerFactory.getLogger(javaClass)
    private val server: Server = ServerBuilder.forPort(port)
        .maxInboundMessageSize(maxMessageBytes)
        .apply { services.forEach { addService(it) } }
        .build()

    @Volatile
    private var running = false

    override fun start() {
        server.start()
        running = true
        log.info("gRPC server listening on port {}", port)
        // gRPC threads are daemon threads. This one is not, so the JVM stays up while the server runs.
        thread(name = "grpc-await", isDaemon = false) { server.awaitTermination() }
    }

    override fun stop() {
        server.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        running = false
    }

    override fun isRunning() = running
}

@Configuration
class PeopleClientConfig {
    // Spring calls ManagedChannel.shutdown() on close (inferred destroy method).
    @Bean
    fun peopleChannel(
        @Value("\${people-service.host}") host: String,
        @Value("\${people-service.port}") port: Int,
    ): ManagedChannel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()

    @Bean
    fun peopleStub(peopleChannel: ManagedChannel): PeopleServiceGrpc.PeopleServiceBlockingStub =
        PeopleServiceGrpc.newBlockingStub(peopleChannel)
}

/** gRPC client for people-service. */
@Component
class PeopleClient(private val stub: PeopleServiceGrpc.PeopleServiceBlockingStub) {
    fun exists(personId: Long): Boolean = try {
        stub.withDeadlineAfter(3, TimeUnit.SECONDS).getPerson(PersonIdRequest.newBuilder().setId(personId).build())
        true
    } catch (e: StatusRuntimeException) {
        if (e.status.code == Status.Code.NOT_FOUND) false else throw e
    }
}
