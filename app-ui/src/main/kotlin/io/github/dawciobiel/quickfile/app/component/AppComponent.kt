package io.github.dawciobiel.quickfile.app.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import io.github.dawciobiel.quickfile.core.jobs.InMemoryJobRegistry
import io.github.dawciobiel.quickfile.core.jobs.JobRegistry
import io.github.dawciobiel.quickfile.core.jobs.JobStatus
import io.github.dawciobiel.quickfile.core.jobs.OperationKind
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

    fun copySelectedFromActivePanel()
}

class DefaultAppComponent(
    componentContext: ComponentContext,
    pluginRuntimeFacade: PluginRuntimeFacade = DemoPluginRuntimeFacade(),
) : AppComponent, ComponentContext by componentContext {
    private val jobRegistry: JobRegistry = InMemoryJobRegistry().also(::seedDemoJobs)
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
            jobRegistry = jobRegistry,
        )

    override val settings: SettingsComponent =
        DefaultSettingsComponent(
            componentContext = childContext(key = "settings"),
        )

    override val pluginRuntimeSnapshot: StateFlow<PluginRuntimeSnapshot> = pluginRuntimeFacade.snapshot

    override fun copySelectedFromActivePanel() {
        val sourcePanel = if (leftPanel.state.value.isActive) leftPanel else rightPanel
        val targetPanel = if (sourcePanel === leftPanel) rightPanel else leftPanel
        val sourceState = sourcePanel.state.value
        val targetState = targetPanel.state.value
        val selectedEntry =
            sourceState.entries.firstOrNull { it.name == sourceState.selectedEntryName }
                ?: return

        jobs.launchCopyJob(
            entryName = selectedEntry.name,
            sourceLocation = sourceState.currentLocation,
            destinationLocation = targetState.currentLocation,
        )
    }

    private fun seedDemoJobs(jobRegistry: JobRegistry) {
        val completedJob =
            jobRegistry.createJob(
                kind = OperationKind.COPY,
                initialStatus = JobStatus.COMPLETED,
                initialMessage = "Copied 24 files to Downloads.",
            )
        jobRegistry.appendLog(completedJob.jobId, "Copy job completed successfully.")

        val runningJob =
            jobRegistry.createJob(
                kind = OperationKind.ARCHIVE,
                initialStatus = JobStatus.RUNNING,
                initialMessage = "Archiving selected files...",
            )
        jobRegistry.updateJob(
            jobId = runningJob.jobId,
            status = JobStatus.RUNNING,
            progress = io.github.dawciobiel.quickfile.core.jobs.JobProgress(
                completedUnits = 48,
                totalUnits = 100,
                details = "48%",
            ),
            message = "Compressing files into backup.zip",
        )
    }
}
