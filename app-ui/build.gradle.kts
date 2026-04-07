plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":plugin-api"))
    implementation(project(":plugin-runtime"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    implementation("com.arkivanov.decompose:decompose:3.5.0")
    implementation("com.arkivanov.decompose:extensions-compose:3.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

compose.desktop {
    application {
        mainClass = "io.github.dawciobiel.quickfile.app.MainKt"
    }
}
