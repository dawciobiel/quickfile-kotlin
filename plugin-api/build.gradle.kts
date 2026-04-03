plugins {
    `java-library`
    id("com.google.protobuf")
    `maven-publish`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api("com.google.protobuf:protobuf-java:4.29.3")
    api("io.grpc:grpc-stub:1.69.0")
    api("io.grpc:grpc-protobuf:1.69.0")
    api("javax.annotation:javax.annotation-api:1.3.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.29.3"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.69.0"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                developers {
                    developer {
                        id.set("dawciobiel")
                        name.set("Dawid Bielecki")
                        email.set("3913996+dawciobiel@users.noreply.github.com")
                    }
                }
            }
        }
    }
}