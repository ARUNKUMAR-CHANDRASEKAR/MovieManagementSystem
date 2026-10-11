package com.govtech.movies.people

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** Person fields as sent by a client, before validation. A null role means none was given. */
data class PersonDraft(
    val name: String,
    val role: Role?,
    val bio: String,
)

/** Business rules for cast members and creators. */
@Service
class PersonDirectory(
    private val people: PersonRepository,
    private val movies: MovieClient,
    private val clock: Clock,
) {
    companion object {
        const val MAX_PAGE_SIZE = 100
        const val MAX_IDS_PER_LOOKUP = 500
        const val MAX_NAME_LENGTH = 150
        const val MAX_BIO_LENGTH = 5000
    }

    /** [role] null lists both cast and creators. */
    @Transactional(readOnly = true)
    fun list(search: String, role: Role?, page: Int, size: Int): Page<Person> {
        if (page < 0) throw InvalidInputException("Page must be 0 or more")
        if (size !in 1..MAX_PAGE_SIZE) throw InvalidInputException("Page size must be between 1 and $MAX_PAGE_SIZE")
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id")))
        val text = search.trim()
        return if (role == null) {
            people.findByNameContainingIgnoreCase(text, pageable)
        } else {
            people.findByRoleAndNameContainingIgnoreCase(role, text, pageable)
        }
    }

    @Transactional(readOnly = true)
    fun get(id: Long): Person = people.findById(id).orElseThrow { NotFoundException("Person $id not found") }

    /** Returns the people that exist, in the order of [ids]. Unknown ids are skipped. */
    @Transactional(readOnly = true)
    fun getByIds(ids: List<Long>): List<Person> {
        if (ids.size > MAX_IDS_PER_LOOKUP) throw InvalidInputException("At most $MAX_IDS_PER_LOOKUP ids per lookup")
        val byId = people.findAllById(ids.distinct()).associateBy { it.id }
        return ids.distinct().mapNotNull { byId[it] }
    }

    @Transactional
    fun create(draft: PersonDraft): Person {
        val clean = validate(draft)
        val now = clock.instant()
        return people.save(Person(clean.name, clean.role!!, clean.bio, createdAt = now, updatedAt = now))
    }

    @Transactional
    fun update(id: Long, draft: PersonDraft): Person {
        val clean = validate(draft)
        return get(id).apply {
            name = clean.name
            role = clean.role!!
            bio = clean.bio
            updatedAt = clock.instant()
        }
    }

    @Transactional
    fun delete(id: Long) {
        val person = get(id)
        // Service-to-service gRPC call. Credits are removed first: if movie-service is down, the
        // call fails, nothing is deleted, and movies never point at a person who no longer exists.
        movies.deleteCreditsForPerson(id)
        people.delete(person)
    }

    /** Trims text fields and checks every rule. Returns the cleaned draft. */
    private fun validate(draft: PersonDraft): PersonDraft {
        val clean = PersonDraft(draft.name.trim(), draft.role, draft.bio.trim())
        when {
            clean.name.isEmpty() -> throw InvalidInputException("Name is required")
            clean.name.length > MAX_NAME_LENGTH ->
                throw InvalidInputException("Name must be $MAX_NAME_LENGTH characters or fewer")
            clean.role == null -> throw InvalidInputException("Role must be CAST or CREATOR")
            clean.bio.length > MAX_BIO_LENGTH ->
                throw InvalidInputException("Bio must be $MAX_BIO_LENGTH characters or fewer")
        }
        return clean
    }
}
