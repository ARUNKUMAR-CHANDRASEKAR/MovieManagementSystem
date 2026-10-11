package com.govtech.movies.gateway

import com.govtech.movies.grpc.movie.PersonRef
import com.govtech.movies.grpc.people.ListPeopleRequest
import com.govtech.movies.grpc.people.PersonIdRequest
import com.govtech.movies.grpc.people.PersonInput
import com.govtech.movies.grpc.people.PersonRole
import com.govtech.movies.grpc.people.UpdatePersonRequest
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

data class PersonInputArg(val name: String, val role: PersonRole, val bio: String?)

@Controller
class PersonController(private val grpc: GrpcClients) {

    @QueryMapping
    fun people(
        @Argument search: String?,
        @Argument role: PersonRole?,
        @Argument page: Int,
        @Argument size: Int,
    ): PageView<PersonView> {
        val request = ListPeopleRequest.newBuilder()
            .setSearch(search.orEmpty())
            .setRole(role ?: PersonRole.PERSON_ROLE_UNSPECIFIED)
            .setPage(page)
            .setSize(size)
            .build()
        val result = grpc.people().listPeople(request)
        return PageView(result.peopleList.map { it.toView() }, result.totalCount, result.page, result.size)
    }

    @QueryMapping
    fun person(@Argument id: String): PersonView = grpc.people().getPerson(personId(id)).toView()

    @MutationMapping
    fun createPerson(@Argument input: PersonInputArg): PersonView =
        grpc.people().createPerson(input.toMessage()).toView()

    @MutationMapping
    fun updatePerson(@Argument id: String, @Argument input: PersonInputArg): PersonView {
        val request = UpdatePersonRequest.newBuilder().setId(parseId(id)).setPerson(input.toMessage()).build()
        return grpc.people().updatePerson(request).toView()
    }

    @MutationMapping
    fun deletePerson(@Argument id: String): Boolean {
        grpc.people().deletePerson(personId(id))
        return true
    }

    /** Person.movies. Only the person detail page asks for it, so one call per person is fine. */
    @SchemaMapping(typeName = "Person")
    fun movies(person: PersonView): List<MovieView> {
        val request = PersonRef.newBuilder().setPersonId(person.id.toLong()).build()
        return grpc.movies().listMoviesByPerson(request).moviesList.map { it.toView() }
    }

    private fun personId(id: String) = PersonIdRequest.newBuilder().setId(parseId(id)).build()

    private fun PersonInputArg.toMessage() =
        PersonInput.newBuilder().setName(name).setRole(role).setBio(bio.orEmpty()).build()
}
