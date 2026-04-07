plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

dependencies {
    api(project(":core"))
    api(project(":plugin-api"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("io.grpc:grpc-netty-shaded:1.69.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    dependsOn(":plugins:local-fs-provider:pluginZip")
    systemProperty(
        "quickfile.localFsPluginZip",
        project.rootProject.layout.projectDirectory
            .file("plugins/local-fs-provider/build/distributions/local-fs-provider-${project.version}.zip")
            .asFile
            .absolutePath,
    )
}
