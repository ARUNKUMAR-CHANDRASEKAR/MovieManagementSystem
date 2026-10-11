import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm") version "2.2.21" apply false
    kotlin("plugin.spring") version "2.2.21" apply false
    kotlin("plugin.jpa") version "2.2.21" apply false
    id("org.springframework.boot") version "3.5.7" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.google.protobuf") version "0.9.5" apply false
}

allprojects {
    group = "com.govtech.movies"
    version = "0.1.0"
    repositories { mavenCentral() }
}

// Shared versions, read by the sub-projects as rootProject.extra["..."].
extra["grpcVersion"] = "1.68.1"
extra["protobufVersion"] = "3.25.5"

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> { jvmToolchain(17) }
        tasks.withType<Test> { useJUnitPlatform() }
    }
}
