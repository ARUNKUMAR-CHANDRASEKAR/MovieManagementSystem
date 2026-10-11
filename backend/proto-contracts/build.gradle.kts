import com.google.protobuf.gradle.id

plugins {
    `java-library`
    id("com.google.protobuf")
}

val grpcVersion: String by rootProject.extra
val protobufVersion: String by rootProject.extra

java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }

dependencies {
    api("io.grpc:grpc-protobuf:$grpcVersion")
    api("io.grpc:grpc-stub:$grpcVersion")
    api("com.google.protobuf:protobuf-java:$protobufVersion")
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" }
    plugins { id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion" } }
    generateProtoTasks {
        // "@generated=omit" drops the javax.annotation.Generated annotation, so no extra dependency is needed.
        all().forEach { it.plugins { id("grpc") { option("@generated=omit") } } }
    }
}
