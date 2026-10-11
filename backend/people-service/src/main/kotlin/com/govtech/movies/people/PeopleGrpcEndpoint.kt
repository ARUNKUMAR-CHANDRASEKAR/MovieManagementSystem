package com.govtech.movies.people

import com.govtech.movies.grpc.people.ListPeopleRequest
import com.govtech.movies.grpc.people.PeopleServiceGrpc
import com.govtech.movies.grpc.people.PersonIdRequest
import com.govtech.movies.grpc.people.PersonIdsRequest
import com.govtech.movies.grpc.people.PersonInput
import com.govtech.movies.grpc.people.PersonList
import com.govtech.movies.grpc.people.PersonPage
import com.govtech.movies.grpc.people.PersonRole
import com.govtech.movies.grpc.people.UpdatePersonRequest
import com.google.protobuf.Empty
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Component
import com.govtech.movies.grpc.people.Person as PersonMessage

/** gRPC adapter: converts protobuf messages to and from domain objects and calls [PersonDirectory]. */
@Component
class PeopleGrpcEndpoint(private val directory: PersonDirectory) : PeopleServiceGrpc.PeopleServiceImplBase() {

    override fun listPeople(request: ListPeopleRequest, observer: StreamObserver<PersonPage>) = respond(observer) {
        val page = directory.list(request.search, request.role.toRole(), request.page, request.size)
        PersonPage.newBuilder()
            .addAllPeople(page.content.map { it.toMessage() })
            .setTotalCount(page.totalElements)
            .setPage(request.page)
            .setSize(request.size)
            .build()
    }

    override fun getPerson(request: PersonIdRequest, observer: StreamObserver<PersonMessage>) = respond(observer) {
        directory.get(request.id).toMessage()
    }

    override fun getPeopleByIds(request: PersonIdsRequest, observer: StreamObserver<PersonList>) = respond(observer) {
        PersonList.newBuilder().addAllPeople(directory.getByIds(request.idsList).map { it.toMessage() }).build()
    }

    override fun createPerson(request: PersonInput, observer: StreamObserver<PersonMessage>) = respond(observer) {
        directory.create(request.toDraft()).toMessage()
    }

    override fun updatePerson(request: UpdatePersonRequest, observer: StreamObserver<PersonMessage>) =
        respond(observer) { directory.update(request.id, request.person.toDraft()).toMessage() }

    override fun deletePerson(request: PersonIdRequest, observer: StreamObserver<Empty>) = respond(observer) {
        directory.delete(request.id)
        Empty.getDefaultInstance()
    }
}

/** PERSON_ROLE_UNSPECIFIED (and unknown values from newer clients) map to null. */
fun PersonRole.toRole(): Role? = when (this) {
    PersonRole.CAST -> Role.CAST
    PersonRole.CREATOR -> Role.CREATOR
    else -> null
}

fun Role.toMessage(): PersonRole = when (this) {
    Role.CAST -> PersonRole.CAST
    Role.CREATOR -> PersonRole.CREATOR
}

fun Person.toMessage(): PersonMessage = PersonMessage.newBuilder()
    .setId(id!!)
    .setName(name)
    .setRole(role.toMessage())
    .setBio(bio)
    .build()

fun PersonInput.toDraft() = PersonDraft(name = name, role = role.toRole(), bio = bio)
