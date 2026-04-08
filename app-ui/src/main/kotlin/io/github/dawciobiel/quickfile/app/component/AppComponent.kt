package io.github.dawciobiel.quickfile.app.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import io.github.dawciobiel.quickfile.core.jobs.InMemoryJobRegistry
import io.github.dawciobiel.quickfile.core.jobs.JobRegistry
import io.github.dawciobiel.quickfile.core.jobs.JobStatus
import io.github.dawciobiel.quickfile.core.jobs.OperationKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.dawciobiel.quickfile.pluginruntime.DemoPluginRuntimeFacade
import io.github.dawciobiel.quickfile.pluginruntime.PluginRuntimeFacade
import io.github.dawciobiel.quickfile.pluginruntime.PluginRuntimeSnapshot
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Files
import java.nio.file.Path
import java.util.prefs.Preferences

interface AppComponent {
    val leftPanel: PanelComponent
    val rightPanel: PanelComponent
    val jobs: JobsComponent
    val settings: SettingsComponent
    val pluginRuntimeSnapshot: StateFlow<PluginRuntimeSnapshot>
    val pendingConfirmation: StateFlow<PendingConfirmation?>
    val pendingRename: StateFlow<PendingRename?>

    fun copySelectedFromActivePanel()
    fun moveSelectedFromActivePanel()
    fun renameSelectedFromActivePanel()
    fun deleteSelectedFromActivePanel()
    fun confirmRename(newName: String)
    fun dismissPendingRename()
    fun confirmPendingOperation()
    fun dismissPendingConfirmation()
    fun switchActivePanel()
}

data class PendingConfirmation(
    val operationKind: OperationKind,
    val entryName: String,
    val message: String,
)

data class PendingRename(
    val entryName: String,
    val sourceLocation: String,
    val parentLocation: String,
)

class DefaultAppComponent(
    componentContext: ComponentContext,
    pluginRuntimeFacade: PluginRuntimeFacade = DemoPluginRuntimeFacade(),
) : AppComponent, ComponentContext by componentContext {
    private val jobRegistry: JobRegistry = InMemoryJobRegistry().also(::seedDemoJobs)
    private val preferences = Preferences.userRoot().node("io/github/dawciobiel/quickfile/app-ui")
    private val defaultHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize()
    private val defaultDownloads =
        defaultHome.resolve("Downloads").takeIf { Files.isDirectory(it) } ?: defaultHome
    private val initialLeftLocation = preferences.get("leftPanelLocation", defaultHome.toString())
    private val initialRightLocation = preferences.get("rightPanelLocation", defaultDownloads.toString())
    private val mutablePendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    private val mutablePendingRename = MutableStateFlow<PendingRename?>(null)
    private var pendingOperation: (() -> Unit)? = null

    override val leftPanel: PanelComponent =
        DefaultPanelComponent(
            componentContext = childContext(key = "left-panel"),
            panelId = "left",
            title = "Left Panel",
            initiallyActive = true,
            initialLocation = initialLeftLocation,
            browseGateway = pluginRuntimeFacade.browseGateway,
            onLocationChanged = { preferences.put("leftPanelLocation", it) },
        )

    override val rightPanel: PanelComponent =
        DefaultPanelComponent(
            componentContext = childContext(key = "right-panel"),
            panelId = "right",
            title = "Right Panel",
            initiallyActive = false,
            initialLocation = initialRightLocation,
            browseGateway = pluginRuntimeFacade.browseGateway,
            onLocationChanged = { preferences.put("rightPanelLocation", it) },
        )

    override val jobs: JobsComponent =
        DefaultJobsComponent(
            componentContext = childContext(key = "jobs"),
            jobRegistry = jobRegistry,
            fileOperationsGateway = pluginRuntimeFacade.fileOperationsGateway,
            onOperationTerminal = ::refreshPanelsAfterOperation,
        )

    override val settings: SettingsComponent =
        DefaultSettingsComponent(
            componentContext = childContext(key = "settings"),
        )

    override val pluginRuntimeSnapshot: StateFlow<PluginRuntimeSnapshot> = pluginRuntimeFacade.snapshot
    override val pendingConfirmation: StateFlow<PendingConfirmation?> = mutablePendingConfirmation.asStateFlow()
    override val pendingRename: StateFlow<PendingRename?> = mutablePendingRename.asStateFlow()

    override fun copySelectedFromActivePanel() {
        prepareOperationOrLaunch(OperationKind.COPY)
    }

    override fun moveSelectedFromActivePanel() {
        prepareOperationOrLaunch(OperationKind.MOVE)
    }

    override fun renameSelectedFromActivePanel() {
        val sourcePanel = if (leftPanel.state.value.isActive) leftPanel else rightPanel
        val sourceState = sourcePanel.state.value
        val selectedEntry =
            sourceState.entries.firstOrNull { it.name == sourceState.selectedEntryName }
                ?: return
        val sourceLocation =
            selectedEntry.childLocation
                ?: Path.of(sourceState.currentLocation).resolve(selectedEntry.name).toString()

        mutablePendingRename.value =
            PendingRename(
                entryName = selectedEntry.name,
                sourceLocation = sourceLocation,
                parentLocation = sourceState.currentLocation,
            )
    }

    override fun deleteSelectedFromActivePanel() {
        prepareOperationOrLaunch(OperationKind.DELETE)
    }

    override fun confirmRename(newName: String) {
        val rename = mutablePendingRename.value ?: return
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) {
            return
        }

        mutablePendingRename.value = null
        prepareRenameOrLaunch(
            rename = rename,
            newName = trimmedName,
        )
    }

    override fun dismissPendingRename() {
        mutablePendingRename.value = null
    }

    override fun confirmPendingOperation() {
        val action = pendingOperation ?: return
        pendingOperation = null
        mutablePendingConfirmation.value = null
        action()
    }

    override fun dismissPendingConfirmation() {
        pendingOperation = null
        mutablePendingConfirmation.value = null
    }

    override fun switchActivePanel() {
        val leftIsActive = leftPanel.state.value.isActive
        leftPanel.onIntent(io.github.dawciobiel.quickfile.core.panel.PanelIntent.SetActive(!leftIsActive))
        rightPanel.onIntent(io.github.dawciobiel.quickfile.core.panel.PanelIntent.SetActive(leftIsActive))
    }

    private fun launchSelectedOperationFromActivePanel(kind: OperationKind) {
        val sourcePanel = if (leftPanel.state.value.isActive) leftPanel else rightPanel
        val targetPanel = if (sourcePanel === leftPanel) rightPanel else leftPanel
        val sourceState = sourcePanel.state.value
        val targetState = targetPanel.state.value
        val selectedEntry =
            sourceState.entries.firstOrNull { it.name == sourceState.selectedEntryName }
                ?: return

        jobs.launchFileOperationJob(
            kind = kind,
            entryName = selectedEntry.name,
            sourceLocation =
                selectedEntry.childLocation
                    ?: sourceState.currentLocation.let { currentLocation ->
                        Path.of(currentLocation).resolve(selectedEntry.name).toString()
                    },
            destinationLocation =
                if (kind == OperationKind.DELETE) {
                    null
                } else {
                    targetState.currentLocation
                },
        )
    }

    private fun launchRename(
        sourceLocation: String,
        currentName: String,
        targetLocation: String,
    ) {
        jobs.launchFileOperationJob(
            kind = OperationKind.RENAME,
            entryName = currentName,
            sourceLocation = sourceLocation,
            destinationLocation = targetLocation,
        )
    }

    private fun prepareOperationOrLaunch(kind: OperationKind) {
        val sourcePanel = if (leftPanel.state.value.isActive) leftPanel else rightPanel
        val targetPanel = if (sourcePanel === leftPanel) rightPanel else leftPanel
        val sourceState = sourcePanel.state.value
        val targetState = targetPanel.state.value
        val selectedEntry =
            sourceState.entries.firstOrNull { it.name == sourceState.selectedEntryName }
                ?: return

        val targetPath = targetState.currentLocation.let { Path.of(it).resolve(selectedEntry.name) }
        val confirmationMessage =
            when {
                kind == OperationKind.DELETE ->
                    "Delete ${selectedEntry.name} from ${sourceState.currentLocation}?"

                kind in setOf(OperationKind.COPY, OperationKind.MOVE) && Files.exists(targetPath) ->
                    "${kind.name.lowercase().replaceFirstChar(Char::uppercase)} will overwrite ${targetPath.fileName} in ${targetState.currentLocation}. Continue?"

                else -> null
            }

        if (confirmationMessage == null) {
            launchSelectedOperationFromActivePanel(kind)
            return
        }

        pendingOperation = { launchSelectedOperationFromActivePanel(kind) }
        mutablePendingConfirmation.value =
            PendingConfirmation(
                operationKind = kind,
                entryName = selectedEntry.name,
                message = confirmationMessage,
            )
    }

    private fun prepareRenameOrLaunch(
        rename: PendingRename,
        newName: String,
    ) {
        val sourcePath = Path.of(rename.sourceLocation)
        val targetPath = Path.of(rename.parentLocation).resolve(newName).normalize()
        if (sourcePath == targetPath) {
            return
        }

        val confirmationMessage =
            if (Files.exists(targetPath)) {
                "Rename will overwrite ${targetPath.fileName} in ${rename.parentLocation}. Continue?"
            } else {
                null
            }

        if (confirmationMessage == null) {
            launchRename(
                sourceLocation = rename.sourceLocation,
                currentName = rename.entryName,
                targetLocation = targetPath.toString(),
            )
            return
        }

        pendingOperation = {
            launchRename(
                sourceLocation = rename.sourceLocation,
                currentName = rename.entryName,
                targetLocation = targetPath.toString(),
            )
        }
        mutablePendingConfirmation.value =
            PendingConfirmation(
                operationKind = OperationKind.RENAME,
                entryName = rename.entryName,
                message = confirmationMessage,
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

    private fun refreshPanelsAfterOperation() {
        leftPanel.onIntent(io.github.dawciobiel.quickfile.core.panel.PanelIntent.Refresh)
        rightPanel.onIntent(io.github.dawciobiel.quickfile.core.panel.PanelIntent.Refresh)
    }
}
