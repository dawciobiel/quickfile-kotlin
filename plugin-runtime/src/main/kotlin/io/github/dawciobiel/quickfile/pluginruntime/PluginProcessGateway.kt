package io.github.dawciobiel.quickfile.pluginruntime

import com.google.protobuf.ByteString
import io.github.dawciobiel.quickfile.core.browse.BrowseException
import io.github.dawciobiel.quickfile.core.browse.BrowseGateway
import io.github.dawciobiel.quickfile.core.browse.BrowseResult
import io.github.dawciobiel.quickfile.core.errors.BrowseError
import io.github.dawciobiel.quickfile.core.panel.PanelEntry
import io.github.dawciobiel.quickfile.core.panel.PanelEntryKind
import io.github.dawciobiel.quickfile.pluginruntime.discovery.DiscoveredPlugin
import io.grpc.ManagedChannel
import io.grpc.StatusRuntimeException
import quickfile.pluginapi.v1.EntryKind
import quickfile.pluginapi.v1.ListEntriesRequest
import quickfile.pluginapi.v1.LocationRef
import java.nio.charset.StandardCharsets
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
            launchPluginClient(plugin).also { launched ->
                client = launched
            }
        }
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
