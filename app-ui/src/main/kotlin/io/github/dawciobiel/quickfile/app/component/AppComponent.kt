package io.github.dawciobiel.quickfile.app.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import io.github.dawciobiel.quickfile.pluginruntime.DemoPluginRuntimeFacade
import io.github.dawciobiel.quickfile.pluginruntime.PluginRuntimeFacade
import io.github.dawciobiel.quickfile.pluginruntime.PluginRuntimeSnapshot
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Files
import java.nio.file.Path

interface AppComponent {
    val leftPanel: PanelComponent
    val rightPanel: PanelComponent
    val jobs: JobsComponent
    val settings: SettingsComponent
    val pluginRuntimeSnapshot: StateFlow<PluginRuntimeSnapshot>
}

class DefaultAppComponent(
    componentContext: ComponentContext,
    pluginRuntimeFacade: PluginRuntimeFacade = DemoPluginRuntimeFacade(),
) : AppComponent, ComponentContext by componentContext {
    private val defaultHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize()
    private val defaultDownloads =
        defaultHome.resolve("Downloads").takeIf { Files.isDirectory(it) } ?: defaultHome

    override val leftPanel: PanelComponent =
        DefaultPanelComponent(
            componentContext = childContext(key = "left-panel"),
            panelId = "left",
            title = "Left Panel",
            initiallyActive = true,
            initialLocation = defaultHome.toString(),
            browseGateway = pluginRuntimeFacade.browseGateway,
        )

    override val rightPanel: PanelComponent =
        DefaultPanelComponent(
            componentContext = childContext(key = "right-panel"),
            panelId = "right",
            title = "Right Panel",
            initiallyActive = false,
            initialLocation = defaultDownloads.toString(),
            browseGateway = pluginRuntimeFacade.browseGateway,
        )

    override val jobs: JobsComponent =
        DefaultJobsComponent(
            componentContext = childContext(key = "jobs"),
        )

    override val settings: SettingsComponent =
        DefaultSettingsComponent(
            componentContext = childContext(key = "settings"),
        )

    override val pluginRuntimeSnapshot: StateFlow<PluginRuntimeSnapshot> = pluginRuntimeFacade.snapshot
}
