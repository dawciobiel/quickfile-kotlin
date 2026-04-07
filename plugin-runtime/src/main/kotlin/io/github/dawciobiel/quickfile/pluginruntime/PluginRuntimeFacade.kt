package io.github.dawciobiel.quickfile.pluginruntime

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
}

class DemoPluginRuntimeFacade : PluginRuntimeFacade {
    private val mutableSnapshot =
        MutableStateFlow(
            PluginRuntimeSnapshot(
                installedPlugins = 0,
                runningPlugins = 0,
                lastScanMessage = "Plugin runtime not initialized yet.",
            ),
        )

    override val snapshot: StateFlow<PluginRuntimeSnapshot> = mutableSnapshot.asStateFlow()
}
