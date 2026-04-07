package io.github.dawciobiel.quickfile.app.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import io.github.dawciobiel.quickfile.pluginruntime.DemoPluginRuntimeFacade
import io.github.dawciobiel.quickfile.pluginruntime.PluginRuntimeFacade
import io.github.dawciobiel.quickfile.pluginruntime.PluginRuntimeSnapshot
import kotlinx.coroutines.flow.StateFlow

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
    override val leftPanel: PanelComponent =
        DefaultPanelComponent(
            componentContext = childContext(key = "left-panel"),
            panelId = "left",
            title = "Left Panel",
            initiallyActive = true,
            initialLocation = "/workspace",
            browseGateway = pluginRuntimeFacade.browseGateway,
        )

    override val rightPanel: PanelComponent =
        DefaultPanelComponent(
            componentContext = childContext(key = "right-panel"),
            panelId = "right",
            title = "Right Panel",
            initiallyActive = false,
            initialLocation = "/downloads",
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
