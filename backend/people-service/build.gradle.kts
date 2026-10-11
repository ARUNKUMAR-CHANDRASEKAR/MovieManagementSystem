plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val grpcVersion: String by rootProject.extra

// JPA entities must be open so Hibernate can create lazy-loading proxies.
allOpen { annotation("jakarta.persistence.Entity") }

dependencies {
    implementation(project(":proto-contracts"))
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("io.grpc:grpc-inprocess:$grpcVersion")
}
