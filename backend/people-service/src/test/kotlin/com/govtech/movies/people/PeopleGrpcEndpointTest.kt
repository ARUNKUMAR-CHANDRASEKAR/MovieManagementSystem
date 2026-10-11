package com.govtech.movies.people

import com.govtech.movies.grpc.people.ListPeopleRequest
import com.govtech.movies.grpc.people.PeopleServiceGrpc
import com.govtech.movies.grpc.people.PersonIdRequest
import com.govtech.movies.grpc.people.PersonIdsRequest
import com.govtech.movies.grpc.people.PersonInput
import com.govtech.movies.grpc.people.PersonRole
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import java.time.Instant

/** Runs the real gRPC endpoint in memory to check message mapping and status codes. */
class PeopleGrpcEndpointTest {
    private val directory = mock<PersonDirectory>()
    private val name = InProcessServerBuilder.generateName()
    private val server = InProcessServerBuilder.forName(name).directExecutor()
        .addService(PeopleGrpcEndpoint(directory)).build().start()
    private val channel = InProcessChannelBuilder.forName(name).directExecutor().build()
    private val stub = PeopleServiceGrpc.newBlockingStub(channel)

    @AfterEach
    fun close() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    private fun person(id: Long, role: Role) = Person("P$id", role, "bio", Instant.EPOCH, Instant.EPOCH, id)

    @Test
    fun `maps person and role`() {
        whenever(directory.get(1)).thenReturn(person(1, Role.CREATOR))
        val reply = stub.getPerson(PersonIdRequest.newBuilder().setId(1).build())
        assertEquals("P1", reply.name)
        assertEquals(PersonRole.CREATOR, reply.role)
    }

    @Test
    fun `unspecified role lists everyone`() {
        whenever(directory.list(any(), eq(null), any(), any())).thenReturn(PageImpl(listOf(person(1, Role.CAST))))
        val reply = stub.listPeople(ListPeopleRequest.newBuilder().setSize(10).build())
        assertEquals(1, reply.peopleCount)
        verify(directory).list("", null, 0, 10)
    }

    @Test
    fun `unspecified role on create reaches validation as null`() {
        whenever(directory.create(PersonDraft("X", null, ""))).thenThrow(InvalidInputException("Role must be CAST or CREATOR"))
        val e = assertThrows<StatusRuntimeException> { stub.createPerson(PersonInput.newBuilder().setName("X").build()) }
        assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
        assertEquals("Role must be CAST or CREATOR", e.status.description)
    }

    @Test
    fun `batch lookup returns people`() {
        whenever(directory.getByIds(listOf(2, 1))).thenReturn(listOf(person(2, Role.CAST), person(1, Role.CAST)))
        val reply = stub.getPeopleByIds(PersonIdsRequest.newBuilder().addAllIds(listOf(2, 1)).build())
        assertEquals(listOf(2L, 1L), reply.peopleList.map { it.id })
    }

    @Test
    fun `not found becomes NOT_FOUND`() {
        whenever(directory.get(9)).thenThrow(NotFoundException("Person 9 not found"))
        val e = assertThrows<StatusRuntimeException> { stub.getPerson(PersonIdRequest.newBuilder().setId(9).build()) }
        assertEquals(Status.Code.NOT_FOUND, e.status.code)
    }

    @Test
    fun `role mapping covers every proto value`() {
        assertEquals(Role.CAST, PersonRole.CAST.toRole())
        assertEquals(Role.CREATOR, PersonRole.CREATOR.toRole())
        assertNull(PersonRole.PERSON_ROLE_UNSPECIFIED.toRole())
        assertNull(PersonRole.UNRECOGNIZED.toRole())
        Role.entries.forEach { assertEquals(it, it.toMessage().toRole()) }
    }
}
