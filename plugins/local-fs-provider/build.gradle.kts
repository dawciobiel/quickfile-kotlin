import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

plugins {
    application
    kotlin("jvm")
}

group = "${project.group}.plugins"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(project(":plugin-api"))
    implementation("io.grpc:grpc-netty-shaded:1.69.0")
}

application {
    mainClass.set("io.github.dawciobiel.quickfile.plugins.localfs.LocalFsPluginMainKt")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("local-fs-provider")
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

val fatJar by tasks.registering(Jar::class) {
    group = "distribution"
    description = "Builds a runnable fat jar for the local filesystem plugin."

    archiveBaseName.set("local-fs-provider")
    archiveVersion.set("")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map(::zipTree)
    })
}

val pluginZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Packages the local filesystem plugin in the v1 plugin ZIP format."

    archiveFileName.set("local-fs-provider-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    dependsOn(fatJar)

    from(layout.projectDirectory.file("src/main/dist/plugin.json"))
    into("lib") {
        from(fatJar.flatMap { it.archiveFile })
    }
}

val installPluginToUserDir by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Installs the extracted plugin into the current user's QuickFile plugins directory."

    dependsOn(pluginZip)

    val pluginInstallDir =
        provider {
            val osName = System.getProperty("os.name").lowercase()
            val userHome = System.getProperty("user.home")
            when {
                osName.contains("win") -> {
                    val appData = System.getenv("APPDATA") ?: "$userHome/AppData/Roaming"
                    file("$appData/QuickFile/plugins/io.github.dawciobiel.local-fs")
                }

                osName.contains("mac") -> {
                    file("$userHome/Library/Application Support/QuickFile/plugins/io.github.dawciobiel.local-fs")
                }

                else -> {
                    val xdgDataHome = System.getenv("XDG_DATA_HOME") ?: "$userHome/.local/share"
                    file("$xdgDataHome/quickfile/plugins/io.github.dawciobiel.local-fs")
                }
            }
        }

    into(pluginInstallDir)
    from({
        zipTree(pluginZip.get().archiveFile.get().asFile)
    })
}
