package io.github.dawciobiel.quickfile.pluginruntime

import io.github.dawciobiel.quickfile.pluginapi.PluginApi
import io.github.dawciobiel.quickfile.pluginruntime.discovery.DiscoveredPlugin
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import quickfile.pluginapi.v1.ApiVersion
import quickfile.pluginapi.v1.FileOperationsGrpc
import quickfile.pluginapi.v1.FileSourceGrpc
import quickfile.pluginapi.v1.HandshakeRequest
import quickfile.pluginapi.v1.PluginControlGrpc
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class LaunchedPluginClient(
    val process: Process,
    val channel: ManagedChannel,
    val fileSource: FileSourceGrpc.FileSourceBlockingStub,
    val fileOperations: FileOperationsGrpc.FileOperationsBlockingStub,
)

internal fun launchPluginClient(plugin: DiscoveredPlugin): LaunchedPluginClient {
    val manifest = requireNotNull(plugin.manifest) { "Cannot launch plugin without a valid manifest." }
    val entrypoint = manifest.entrypoint
    val classpath =
        entrypoint.classpath.joinToString(System.getProperty("path.separator")) { relativePath ->
            plugin.directory.resolve(relativePath).toAbsolutePath().normalize().toString()
        }

    val javaBinary =
        ProcessHandle.current().info().command().orElseGet {
            Path.of(System.getProperty("java.home"), "bin", "java").toString()
        }

    val process =
        ProcessBuilder(
            javaBinary,
            "-cp",
            classpath,
            entrypoint.mainClass,
        ).directory(plugin.directory.toFile())
            .redirectErrorStream(true)
            .apply {
                environment()["QUICKFILE_PLUGIN_TOKEN"] = UUID.randomUUID().toString()
            }.start()

    val port = readStartupPort(process)
    val channel =
        ManagedChannelBuilder
            .forAddress("127.0.0.1", port)
            .usePlaintext()
            .build()

    val pluginControl = PluginControlGrpc.newBlockingStub(channel)
    pluginControl.handshake(
        HandshakeRequest
            .newBuilder()
            .setPluginId(manifest.id)
            .setPluginVersion(manifest.version)
            .setClientApiVersion(
                ApiVersion
                    .newBuilder()
                    .setMajor(PluginApi.VERSION)
                    .setMinor(0)
                    .build(),
            ).build(),
    )

    return LaunchedPluginClient(
        process = process,
        channel = channel,
        fileSource = FileSourceGrpc.newBlockingStub(channel),
        fileOperations = FileOperationsGrpc.newBlockingStub(channel),
    )
}

private fun readStartupPort(process: Process): Int {
    val reader = BufferedReader(InputStreamReader(process.inputStream))
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)

    while (System.nanoTime() < deadline) {
        if (!process.isAlive) {
            error("Plugin process exited before reporting its gRPC port.")
        }

        if (reader.ready()) {
            val line = reader.readLine() ?: continue
            if (line.startsWith("PORT=")) {
                return line.removePrefix("PORT=").trim().toInt()
            }
        } else {
            Thread.sleep(25)
        }
    }

    process.destroyForcibly()
    error("Plugin did not report PORT=<number> within the startup timeout.")
}
