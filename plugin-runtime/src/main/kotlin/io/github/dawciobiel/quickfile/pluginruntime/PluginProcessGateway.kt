package io.github.dawciobiel.quickfile.pluginruntime

import com.google.protobuf.ByteString
import io.github.dawciobiel.quickfile.core.browse.BrowseException
import io.github.dawciobiel.quickfile.core.browse.BrowseGateway
import io.github.dawciobiel.quickfile.core.browse.BrowseResult
import io.github.dawciobiel.quickfile.core.errors.BrowseError
import io.github.dawciobiel.quickfile.core.panel.PanelEntry
import io.github.dawciobiel.quickfile.core.panel.PanelEntryKind
import io.github.dawciobiel.quickfile.pluginapi.PluginApi
import io.github.dawciobiel.quickfile.pluginruntime.discovery.DiscoveredPlugin
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import quickfile.pluginapi.v1.ApiVersion
import quickfile.pluginapi.v1.EntryKind
import quickfile.pluginapi.v1.FileSourceGrpc
import quickfile.pluginapi.v1.HandshakeRequest
import quickfile.pluginapi.v1.ListEntriesRequest
import quickfile.pluginapi.v1.LocationRef
import quickfile.pluginapi.v1.PluginControlGrpc
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

class PluginProcessBrowseGateway(
    private val plugin: DiscoveredPlugin,
) : BrowseGateway, AutoCloseable {
    @Volatile
    private var client: LaunchedPluginClient? = null

    override suspend fun listEntries(location: String): BrowseResult {
        return runCatching {
            val launchedClient = ensureClient()

            val response =
                launchedClient.fileSource
                    .listEntries(
                        ListEntriesRequest
                            .newBuilder()
                            .setLocation(
                                LocationRef
                                    .newBuilder()
                                    .setOpaque(ByteString.copyFrom(location, StandardCharsets.UTF_8))
                                    .build(),
                            ).build(),
                    )

            BrowseResult(
                location = location,
                entries =
                    response.entriesList.map { entry ->
                        PanelEntry(
                            name = entry.name,
                            kind =
                                when (entry.kind) {
                                    EntryKind.ENTRY_KIND_DIRECTORY -> PanelEntryKind.DIRECTORY
                                    else -> PanelEntryKind.FILE
                                },
                            childLocation =
                                if (entry.hasChildLocation()) {
                                    entry.childLocation.opaque.toString(StandardCharsets.UTF_8)
                                } else {
                                    null
                                },
                        )
                    },
            )
        }.getOrElse { error ->
            throw mapToBrowseException(location = location, error = error)
        }
    }

    private fun ensureClient(): LaunchedPluginClient {
        client?.takeIf { it.process.isAlive }?.let { return it }

        return synchronized(this) {
            client?.takeIf { it.process.isAlive }?.let { return@synchronized it }
            launchPlugin(plugin).also { launched ->
                client = launched
            }
        }
    }

    private fun launchPlugin(plugin: DiscoveredPlugin): LaunchedPluginClient {
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

    override fun close() {
        val launchedClient =
            synchronized(this) {
                client.also {
                    client = null
                }
            } ?: return

        launchedClient.channel.shutdownNow()
        launchedClient.process.destroy()
        if (!launchedClient.process.waitFor(1, TimeUnit.SECONDS)) {
            launchedClient.process.destroyForcibly()
        }
    }

    private fun mapToBrowseException(
        location: String,
        error: Throwable,
    ): BrowseException {
        if (error is BrowseException) {
            return error
        }

        val pluginId = plugin.manifest?.id ?: plugin.directory.fileName.toString()
        val browseError =
            when (error) {
                is StatusRuntimeException ->
                    when (error.status.code) {
                        io.grpc.Status.Code.PERMISSION_DENIED -> BrowseError.PermissionDenied(location)
                        io.grpc.Status.Code.NOT_FOUND -> BrowseError.LocationNotFound(location)
                        io.grpc.Status.Code.UNAVAILABLE,
                        io.grpc.Status.Code.DEADLINE_EXCEEDED,
                        -> BrowseError.PluginCrashed(pluginId, error.status.description ?: "Plugin unavailable.")
                        else -> BrowseError.Unknown(error.status.description ?: error.message ?: "Unknown browse error.")
                    }

                is IllegalStateException ->
                    BrowseError.PluginUnavailable(pluginId)

                else -> BrowseError.Unknown(error.message ?: "Unknown browse error.")
            }

        return BrowseException(browseError)
    }
}

internal data class LaunchedPluginClient(
    val process: Process,
    val channel: ManagedChannel,
    val fileSource: FileSourceGrpc.FileSourceBlockingStub,
)
