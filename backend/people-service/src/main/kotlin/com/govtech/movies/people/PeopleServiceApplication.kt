package com.govtech.movies.people

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.time.Clock

@SpringBootApplication
class PeopleServiceApplication {
    // Injected instead of calling Instant.now() directly, so tests can fix the time.
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

fun main(args: Array<String>) {
    runApplication<PeopleServiceApplication>(*args)
}
