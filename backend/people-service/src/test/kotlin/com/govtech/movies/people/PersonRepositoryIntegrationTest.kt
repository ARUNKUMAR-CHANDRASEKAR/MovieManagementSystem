package com.govtech.movies.people

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

/**
 * Runs the repository against a real PostgreSQL (Docker), with the real Flyway migrations.
 * Each test runs in a transaction that is rolled back afterwards.
 */
@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=validate"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PersonRepositoryIntegrationTest {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }

    @Autowired lateinit var people: PersonRepository
    @Autowired lateinit var entities: TestEntityManager
    @Autowired lateinit var jdbc: JdbcTemplate

    private val page = PageRequest.of(0, 10, Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id")))

    private fun save(name: String, role: Role): Person {
        val now = Instant.now()
        return people.save(Person(name, role, "", now, now))
    }

    private fun flushAndClear() = entities.flush().also { entities.clear() }

    @Test
    fun `name search is partial, case-insensitive and sorted`() {
        save("Vijay Sethupathi", Role.CAST)
        save("vijay", Role.CAST)
        save("Anirudh", Role.CREATOR)
        flushAndClear()

        val result = people.findByNameContainingIgnoreCase("VIJAY", page)

        assertEquals(listOf("vijay", "Vijay Sethupathi"), result.content.map { it.name })
        assertEquals(2, result.totalElements)
    }

    @Test
    fun `role filter combines with search`() {
        save("Lokesh", Role.CREATOR)
        save("Lokesh Actor", Role.CAST)
        save("Nelson", Role.CREATOR)
        flushAndClear()

        assertEquals(listOf("Lokesh"), people.findByRoleAndNameContainingIgnoreCase(Role.CREATOR, "lok", page).content.map { it.name })
        assertEquals(listOf("Lokesh", "Nelson"), people.findByRoleAndNameContainingIgnoreCase(Role.CREATOR, "", page).content.map { it.name })
    }

    @Test
    fun `role is stored as text`() {
        val person = save("Karthi", Role.CAST)
        flushAndClear()
        assertEquals("CAST", jdbc.queryForObject("select role from person where id = ?", String::class.java, person.id))
    }

    @Test
    fun `lookup by ids skips ids that do not exist`() {
        val a = save("A", Role.CAST)
        val b = save("B", Role.CREATOR)
        flushAndClear()

        assertEquals(setOf("A", "B"), people.findAllById(listOf(a.id!!, b.id!!, 999)).map { it.name }.toSet())
        assertEquals(emptyList<Person>(), people.findAllById(emptyList()))
    }

    @Test
    fun `database rejects an unknown role`() {
        assertThrows<DataIntegrityViolationException> {
            jdbc.update("insert into person (name, role) values ('X', 'DIRECTOR')")
        }
    }
}
