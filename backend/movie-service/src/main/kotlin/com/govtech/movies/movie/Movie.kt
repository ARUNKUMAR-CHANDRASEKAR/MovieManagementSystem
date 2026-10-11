package com.govtech.movies.movie

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

enum class Region { SINGAPORE, INDIA, HOLLYWOOD }

@Entity
@Table(name = "movie")
class Movie(
    var title: String,
    var releaseYear: Int?,
    var genre: String,
    var synopsis: String,
    @Enumerated(EnumType.STRING)
    var region: Region? = null,
    var artworkUpdatedAt: Instant? = null,
    var createdAt: Instant,
    var updatedAt: Instant,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
    // Ids of people (cast and creators) credited on this movie. Loaded eagerly because every
    // response includes them; @BatchSize loads them for a whole page of movies in one query.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "movie_credit", joinColumns = [JoinColumn(name = "movie_id")])
    @Column(name = "person_id")
    @BatchSize(size = 100)
    var personIds: MutableSet<Long> = mutableSetOf()
}

@Entity
@Table(name = "movie_artwork")
class MovieArtwork(
    @Id
    var movieId: Long,
    var contentType: String,
    var data: ByteArray,
)

interface MovieRepository : JpaRepository<Movie, Long> {
    // "Containing" escapes % and _ in the search text, so users can search for them literally.
    fun findByTitleContainingIgnoreCase(title: String, pageable: Pageable): Page<Movie>

    fun findByRegionAndTitleContainingIgnoreCase(region: Region, title: String, pageable: Pageable): Page<Movie>

    @Query("select m from Movie m join m.personIds p where p = :personId order by m.title")
    fun findByPersonId(personId: Long): List<Movie>

    @Modifying
    @Query(value = "delete from movie_credit where person_id = :personId", nativeQuery = true)
    fun deleteCreditsForPerson(personId: Long): Int
}

interface MovieArtworkRepository : JpaRepository<MovieArtwork, Long>
