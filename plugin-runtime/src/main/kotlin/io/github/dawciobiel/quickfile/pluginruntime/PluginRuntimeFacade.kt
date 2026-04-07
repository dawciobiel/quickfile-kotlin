package io.github.dawciobiel.quickfile.pluginruntime

import io.github.dawciobiel.quickfile.core.browse.BrowseGateway
import io.github.dawciobiel.quickfile.core.browse.BrowseResult
import io.github.dawciobiel.quickfile.core.panel.PanelEntry
import io.github.dawciobiel.quickfile.core.panel.PanelEntryKind
import io.github.dawciobiel.quickfile.pluginruntime.discovery.DiscoveredPlugin
import io.github.dawciobiel.quickfile.pluginruntime.discovery.FileSystemPluginDiscovery
import io.github.dawciobiel.quickfile.pluginruntime.discovery.PluginCompatibilityStatus
import io.github.dawciobiel.quickfile.pluginruntime.discovery.PluginDirectories
import io.github.dawciobiel.quickfile.pluginruntime.discovery.PluginRuntimeDiscoverySnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PluginRuntimeSnapshot(
    val installedPlugins: Int,
    val compatiblePlugins: Int,
    val runningPlugins: Int,
    val lastScanMessage: String,
    val pluginsDirectory: String,
    val discoveredPlugins: List<PluginRuntimePluginSummary>,
)

data class PluginRuntimePluginSummary(
    val id: String,
    val status: PluginCompatibilityStatus,
    val message: String,
)

interface PluginRuntimeFacade {
    val snapshot: StateFlow<PluginRuntimeSnapshot>
    val browseGateway: BrowseGateway
    val fileOperationsGateway: FileOperationsGateway
    val discoverySnapshot: PluginRuntimeDiscoverySnapshot
}

class DemoPluginRuntimeFacade : PluginRuntimeFacade {
    override val discoverySnapshot: PluginRuntimeDiscoverySnapshot =
        FileSystemPluginDiscovery(
            pluginsDirectory = PluginDirectories.defaultPluginsDirectory(),
        ).discover()

    private val selectedBrowsePlugin: DiscoveredPlugin? =
        discoverySnapshot.compatiblePlugins.firstOrNull { plugin ->
            plugin.manifest?.capabilities?.contains("file-source") == true
        }
    private val selectedFileOperationsPlugin: DiscoveredPlugin? =
        discoverySnapshot.compatiblePlugins.firstOrNull { plugin ->
            plugin.manifest?.capabilities?.contains("file-operations") == true
        }

    private val mutableSnapshot =
        MutableStateFlow(
            PluginRuntimeSnapshot(
                installedPlugins = discoverySnapshot.discoveredPlugins.size,
                compatiblePlugins = discoverySnapshot.compatiblePlugins.size,
                runningPlugins = if (selectedBrowsePlugin != null) 1 else 0,
                lastScanMessage = buildScanMessage(discoverySnapshot),
                pluginsDirectory = discoverySnapshot.pluginsDirectory.toString(),
                discoveredPlugins =
                    discoverySnapshot.discoveredPlugins.map { plugin ->
                        PluginRuntimePluginSummary(
                            id = plugin.manifest?.id ?: plugin.directory.fileName.toString(),
                            status = plugin.status,
                            message = plugin.message,
                        )
                    },
            ),
        )

    override val snapshot: StateFlow<PluginRuntimeSnapshot> = mutableSnapshot.asStateFlow()
    override val browseGateway: BrowseGateway =
        selectedBrowsePlugin?.let(::PluginProcessBrowseGateway) ?: DemoBrowseGateway()
    override val fileOperationsGateway: FileOperationsGateway =
        selectedFileOperationsPlugin?.let(::PluginProcessFileOperationsGateway) ?: UnsupportedFileOperationsGateway()

    private fun buildScanMessage(discovery: PluginRuntimeDiscoverySnapshot): String {
        if (discovery.discoveredPlugins.isEmpty()) {
            return "No installed plugins found. Using demo browse gateway until runtime launch is implemented."
        }

        if (selectedBrowsePlugin != null) {
            val operationsMessage =
                if (selectedFileOperationsPlugin != null) {
                    " File operations provided by ${selectedFileOperationsPlugin.manifest?.id}."
                } else {
                    " No compatible file-operations plugin detected yet."
                }
            return "Using file-source plugin ${selectedBrowsePlugin.manifest?.id} for browse requests.$operationsMessage"
        }

        val compatible = discovery.compatiblePlugins.size
        val total = discovery.discoveredPlugins.size
        return "Discovered $total plugin(s), $compatible compatible with host API, but none provide file-source browse."
    }
}

private class UnsupportedFileOperationsGateway : FileOperationsGateway {
    override suspend fun startCopy(
        jobId: String,
        sourceLocation: String,
        destinationLocation: String,
        onEvent: (FileOperationEvent) -> Unit,
    ) {
        error("No compatible file-operations plugin is installed.")
    }

    override suspend fun cancel(jobId: String): Boolean = false
}

private class DemoBrowseGateway : BrowseGateway {
    private val tree =
        mapOf(
            "/" to
                listOf(
                    directory("workspace", "/workspace"),
                    directory("downloads", "/downloads"),
                    file("README.md"),
                ),
            "/workspace" to
                listOf(
                    directory("docs", "/workspace/docs"),
                    directory("plugin-api", "/workspace/plugin-api"),
                    file("gradle.properties"),
                ),
            "/workspace/docs" to
                listOf(
                    file("architecture.md"),
                    file("roadmap.md"),
                ),
            "/workspace/plugin-api" to
                listOf(
                    file("file_source.proto"),
                    file("common.proto"),
                ),
            "/downloads" to
                listOf(
                    file("archive.zip"),
                    directory("photos", "/downloads/photos"),
                    file("notes.txt"),
                ),
            "/downloads/photos" to
                listOf(
                    file("spring-trip.jpg"),
                    file("diagram.png"),
                ),
        )

    override suspend fun listEntries(location: String): BrowseResult {
        delay(120)

        val normalizedLocation = location.ifBlank { "/" }
        val entries = tree[normalizedLocation] ?: error("Unknown demo location: $normalizedLocation")

        return BrowseResult(
            location = normalizedLocation,
            entries = entries,
        )
    }

    private fun directory(
        name: String,
        childLocation: String,
    ): PanelEntry =
        PanelEntry(
            name = name,
            kind = PanelEntryKind.DIRECTORY,
            childLocation = childLocation,
        )

    private fun file(name: String): PanelEntry =
        PanelEntry(
            name = name,
            kind = PanelEntryKind.FILE,
        )
}
