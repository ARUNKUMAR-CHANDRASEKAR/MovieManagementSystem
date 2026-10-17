import org.springframework.boot.gradle.plugin.SpringBootPlugin

// End-to-end tests: start all the services in one JVM, with real gRPC between them and two
// real PostgreSQL databases (Testcontainers), and drive them through the gateway's HTTP API.
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    // Spring Boot's versions for the test dependencies only (not for Kotlin's own build tooling).
    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation(platform("org.testcontainers:testcontainers-bom:${rootProject.extra["testcontainers.version"]}"))
    testImplementation(project(":movie-service"))
    testImplementation(project(":people-service"))
    testImplementation(project(":showtime-service"))
    testImplementation(project(":api-gateway"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
