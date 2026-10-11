package com.govtech.movies.people

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

enum class Role { CAST, CREATOR }

@Entity
@Table(name = "person")
class Person(
    var name: String,
    @Enumerated(EnumType.STRING)
    var role: Role,
    var bio: String,
    var createdAt: Instant,
    var updatedAt: Instant,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)

interface PersonRepository : JpaRepository<Person, Long> {
    // "Containing" escapes % and _ in the search text, so users can search for them literally.
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Person>

    fun findByRoleAndNameContainingIgnoreCase(role: Role, name: String, pageable: Pageable): Page<Person>
}
