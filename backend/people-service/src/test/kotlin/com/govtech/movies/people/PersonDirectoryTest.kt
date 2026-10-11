package com.govtech.movies.people

import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class PersonDirectoryTest {
    private val now = Instant.parse("2026-01-15T10:00:00Z")
    private val people = mock<PersonRepository>()
    private val movies = mock<MovieClient>()
    private val directory = PersonDirectory(people, movies, Clock.fixed(now, ZoneOffset.UTC))

    private fun person(id: Long, name: String = "Person $id", role: Role = Role.CAST) =
        Person(name, role, "", now, now, id)

    @BeforeEach
    fun saveReturnsArgument() {
        whenever(people.save(any<Person>())).doAnswer { it.arguments[0] as Person }
    }

    @Nested
    inner class List {
        @Test
        fun `without role searches both cast and creators`() {
            whenever(people.findByNameContainingIgnoreCase(any(), any())).thenReturn(PageImpl(listOf(person(1))))
            assertEquals(1, directory.list("  kam ", null, 0, 10).content.size)
            verify(people).findByNameContainingIgnoreCase(eq("kam"), any())
            verify(people, never()).findByRoleAndNameContainingIgnoreCase(any(), any(), any())
        }

        @Test
        fun `with role filters by role`() {
            whenever(people.findByRoleAndNameContainingIgnoreCase(any(), any(), any())).thenReturn(PageImpl(emptyList()))
            directory.list("", Role.CREATOR, 0, 10)
            verify(people).findByRoleAndNameContainingIgnoreCase(eq(Role.CREATOR), eq(""), any())
        }

        @Test
        fun `rejects negative page`() {
            assertThrows<InvalidInputException> { directory.list("", null, -1, 10) }
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 101])
        fun `rejects page size outside 1 to 100`(size: Int) {
            assertThrows<InvalidInputException> { directory.list("", null, 0, size) }
        }
    }

    @Nested
    inner class GetByIds {
        @Test
        fun `keeps request order, drops duplicates and unknown ids`() {
            whenever(people.findAllById(listOf(3L, 1L, 99L))).thenReturn(listOf(person(1), person(3)))
            val result = directory.getByIds(listOf(3, 1, 3, 99))
            assertEquals(listOf(3L, 1L), result.map { it.id })
        }

        @Test
        fun `empty list returns nothing`() {
            whenever(people.findAllById(emptyList<Long>())).thenReturn(emptyList())
            assertEquals(emptyList<Person>(), directory.getByIds(emptyList()))
        }

        @Test
        fun `rejects more than 500 ids`() {
            whenever(people.findAllById(any<Iterable<Long>>())).thenReturn(emptyList())
            directory.getByIds((1L..500L).toList())
            assertThrows<InvalidInputException> { directory.getByIds((1L..501L).toList()) }
        }
    }

    @Nested
    inner class Create {
        @Test
        fun `saves trimmed fields with timestamps`() {
            val created = directory.create(PersonDraft("  Kamal Haasan ", Role.CAST, "  Actor  "))
            assertEquals("Kamal Haasan", created.name)
            assertEquals("Actor", created.bio)
            assertEquals(Role.CAST, created.role)
            assertEquals(now, created.createdAt)
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "  "])
        fun `rejects blank name`(name: String) {
            val e = assertThrows<InvalidInputException> { directory.create(PersonDraft(name, Role.CAST, "")) }
            assertEquals("Name is required", e.message)
        }

        @Test
        fun `accepts name of 150 characters and rejects 151`() {
            directory.create(PersonDraft("n".repeat(150), Role.CAST, ""))
            assertThrows<InvalidInputException> { directory.create(PersonDraft("n".repeat(151), Role.CAST, "")) }
        }

        @Test
        fun `rejects missing role`() {
            val e = assertThrows<InvalidInputException> { directory.create(PersonDraft("Name", null, "")) }
            assertEquals("Role must be CAST or CREATOR", e.message)
            verify(people, never()).save(any<Person>())
        }

        @Test
        fun `rejects bio over 5000 characters`() {
            directory.create(PersonDraft("Name", Role.CREATOR, "b".repeat(5000)))
            assertThrows<InvalidInputException> { directory.create(PersonDraft("Name", Role.CREATOR, "b".repeat(5001))) }
        }
    }

    @Nested
    inner class Update {
        @Test
        fun `changes fields and updatedAt only`() {
            val existing = Person("Old", Role.CAST, "", Instant.EPOCH, Instant.EPOCH, 1)
            whenever(people.findById(1)).thenReturn(Optional.of(existing))

            val updated = directory.update(1, PersonDraft("New", Role.CREATOR, "Bio"))

            assertEquals("New", updated.name)
            assertEquals(Role.CREATOR, updated.role)
            assertEquals(Instant.EPOCH, updated.createdAt)
            assertEquals(now, updated.updatedAt)
        }

        @Test
        fun `unknown person is not found`() {
            whenever(people.findById(4)).thenReturn(Optional.empty())
            val e = assertThrows<NotFoundException> { directory.update(4, PersonDraft("New", Role.CAST, "")) }
            assertEquals("Person 4 not found", e.message)
        }
    }

    @Nested
    inner class Delete {
        @Test
        fun `removes credits in movie-service before deleting`() {
            val existing = person(1)
            whenever(people.findById(1)).thenReturn(Optional.of(existing))

            directory.delete(1)

            inOrder(movies, people) {
                verify(movies).deleteCreditsForPerson(1)
                verify(people).delete(existing)
            }
        }

        @Test
        fun `keeps the person when movie-service is unavailable`() {
            whenever(people.findById(1)).thenReturn(Optional.of(person(1)))
            whenever(movies.deleteCreditsForPerson(1)).thenThrow(StatusRuntimeException(Status.UNAVAILABLE))

            assertThrows<StatusRuntimeException> { directory.delete(1) }
            verify(people, never()).delete(any())
        }

        @Test
        fun `unknown person is not found and movie-service is not called`() {
            whenever(people.findById(1)).thenReturn(Optional.empty())
            assertThrows<NotFoundException> { directory.delete(1) }
            verify(movies, never()).deleteCreditsForPerson(any())
        }
    }
}
