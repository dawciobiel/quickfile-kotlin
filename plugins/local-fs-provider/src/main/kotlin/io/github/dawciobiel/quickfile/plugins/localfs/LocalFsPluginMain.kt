package io.github.dawciobiel.quickfile.plugins.localfs

import io.grpc.Server
import io.grpc.ServerBuilder
import java.io.File

private const val PLUGIN_ID = "io.github.dawciobiel.local-fs"
private const val PLUGIN_VERSION = "0.1.0-SNAPSHOT"

fun main() {
    val server =
        ServerBuilder
            .forPort(0)
            .addService(LocalFsPluginControlService(pluginId = PLUGIN_ID, pluginVersion = PLUGIN_VERSION))
            .addService(LocalFsFileSourceService())
            .build()
            .start()

    println("PORT=${server.port}")
    System.out.flush()

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.shutdown()
        },
    )

    server.awaitTermination()
}

internal fun defaultBrowseRoot(): File =
    File(System.getProperty("user.home")).absoluteFile.normalize()
