package io.github.dawciobiel.quickfile.pluginruntime

import io.github.dawciobiel.quickfile.core.browse.BrowseGateway
import io.github.dawciobiel.quickfile.core.browse.BrowseResult
import io.github.dawciobiel.quickfile.core.panel.PanelEntry
import io.github.dawciobiel.quickfile.core.panel.PanelEntryKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PluginRuntimeSnapshot(
    val installedPlugins: Int,
    val runningPlugins: Int,
    val lastScanMessage: String,
)

interface PluginRuntimeFacade {
    val snapshot: StateFlow<PluginRuntimeSnapshot>
    val browseGateway: BrowseGateway
}

class DemoPluginRuntimeFacade : PluginRuntimeFacade {
    private val mutableSnapshot =
        MutableStateFlow(
            PluginRuntimeSnapshot(
                installedPlugins = 1,
                runningPlugins = 1,
                lastScanMessage = "Demo file-source runtime is active. Real plugin discovery comes next.",
            ),
        )

    override val snapshot: StateFlow<PluginRuntimeSnapshot> = mutableSnapshot.asStateFlow()
    override val browseGateway: BrowseGateway = DemoBrowseGateway()
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
