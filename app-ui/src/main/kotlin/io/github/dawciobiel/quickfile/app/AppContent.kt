package io.github.dawciobiel.quickfile.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.dawciobiel.quickfile.app.component.AppComponent
import io.github.dawciobiel.quickfile.core.errors.BrowseError
import io.github.dawciobiel.quickfile.core.jobs.JobSnapshot
import io.github.dawciobiel.quickfile.core.jobs.JobStatus
import io.github.dawciobiel.quickfile.core.panel.PanelEntry
import io.github.dawciobiel.quickfile.core.panel.PanelEntryKind
import io.github.dawciobiel.quickfile.core.panel.PanelIntent

@Composable
fun AppContent(component: AppComponent) {
    val runtimeSnapshot by component.pluginRuntimeSnapshot.collectAsState()
    val jobsState by component.jobs.state.collectAsState()
    val leftPanelState by component.leftPanel.state.collectAsState()
    val rightPanelState by component.rightPanel.state.collectAsState()
    val activePanelState = if (leftPanelState.isActive) leftPanelState else rightPanelState
    val inactivePanelState = if (leftPanelState.isActive) rightPanelState else leftPanelState
    val hasSelectedEntry = activePanelState.selectedEntryName != null && !activePanelState.isLoading

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF3F4F6)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("QuickFile", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text =
                            "Installed plugins: ${runtimeSnapshot.installedPlugins} | " +
                                "Compatible: ${runtimeSnapshot.compatiblePlugins} | " +
                                "Running: ${runtimeSnapshot.runningPlugins}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Plugins directory: ${runtimeSnapshot.pluginsDirectory}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = runtimeSnapshot.lastScanMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    runtimeSnapshot.discoveredPlugins.forEach { plugin ->
                        Text(
                            text = "- ${plugin.id}: ${plugin.status} (${plugin.message})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text =
                            if (hasSelectedEntry) {
                                "${activePanelState.selectedEntryName} | copy/move -> ${inactivePanelState.currentLocation} | delete in place"
                            } else {
                                "Select an entry in the active panel to queue a file operation job."
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { component.copySelectedFromActivePanel() },
                            enabled = hasSelectedEntry,
                        ) {
                            Text("Copy")
                        }
                        OutlinedButton(
                            onClick = { component.moveSelectedFromActivePanel() },
                            enabled = hasSelectedEntry,
                        ) {
                            Text("Move")
                        }
                        OutlinedButton(
                            onClick = { component.deleteSelectedFromActivePanel() },
                            enabled = hasSelectedEntry,
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PanelCard(
                    modifier = Modifier.weight(1f),
                    component = component.leftPanel,
                    onActivate = {
                        component.leftPanel.onIntent(PanelIntent.SetActive(true))
                        component.rightPanel.onIntent(PanelIntent.SetActive(false))
                    },
                )
                PanelCard(
                    modifier = Modifier.weight(1f),
                    component = component.rightPanel,
                    onActivate = {
                        component.leftPanel.onIntent(PanelIntent.SetActive(false))
                        component.rightPanel.onIntent(PanelIntent.SetActive(true))
                    },
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Jobs", style = MaterialTheme.typography.titleLarge)
                        OutlinedButton(
                            onClick = { component.jobs.launchDemoJob() },
                        ) {
                            Text("Run Demo Job")
                        }
                    }
                    Text(
                        text = jobsState.summary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        jobsState.jobs.forEach { job ->
                            JobRow(
                                job = job,
                                onCancel = { component.jobs.cancelJob(job.jobId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun PanelCard(
    modifier: Modifier = Modifier,
    component: io.github.dawciobiel.quickfile.app.component.PanelComponent,
    onActivate: () -> Unit,
) {
    val state by component.state.collectAsState()
    var locationInput by remember(component) { mutableStateOf(state.currentLocation) }
    val focusRequester = remember(component) { FocusRequester() }

    LaunchedEffect(state.currentLocation) {
        locationInput = state.currentLocation
    }

    LaunchedEffect(state.isActive) {
        if (state.isActive) {
            focusRequester.requestFocus()
        }
    }

    val accent =
        if (state.isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            Color(0xFFD1D5DB)
        }

    Card(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (!state.isActive || event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }

                        when (event.key) {
                            Key.DirectionDown -> {
                                component.onIntent(PanelIntent.MoveSelection(1))
                                true
                            }

                            Key.DirectionUp -> {
                                component.onIntent(PanelIntent.MoveSelection(-1))
                                true
                            }

                            Key.Enter,
                            Key.NumPadEnter,
                            -> {
                                component.onIntent(PanelIntent.OpenSelectedEntry)
                                true
                            }

                            Key.Backspace -> {
                                component.onIntent(PanelIntent.NavigateUp)
                                true
                            }

                            else -> false
                        }
                    }
                    .border(width = 2.dp, color = accent)
                    .background(Color.White)
                    .clickable(onClick = onActivate)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.currentLocation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = locationInput,
                    onValueChange = { locationInput = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Location") },
                )
                OutlinedButton(
                    onClick = {
                        onActivate()
                        component.onIntent(PanelIntent.OpenLocation(locationInput))
                    },
                    enabled = locationInput.isNotBlank() && !state.isLoading,
                ) {
                    Text("Go")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onActivate()
                        component.onIntent(PanelIntent.NavigateUp)
                    },
                    enabled = !state.isLoading,
                ) {
                    Text("Up")
                }
                OutlinedButton(
                    onClick = {
                        onActivate()
                        component.onIntent(PanelIntent.Refresh)
                    },
                ) {
                    Text(if (state.isLoading) "Refreshing..." else "Refresh")
                }
            }
            state.statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.error != null) Color(0xFFB42318) else MaterialTheme.colorScheme.primary,
                )
            }
            state.error?.let { error ->
                Text(
                    text = formatBrowseError(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB42318),
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "__parent__") {
                    ParentDirectoryRow(
                        enabled = !state.isLoading,
                        onActivate = {
                            onActivate()
                            component.onIntent(PanelIntent.NavigateUp)
                        },
                    )
                }
                items(state.entries) { entry ->
                    val isSelected = state.selectedEntryName == entry.name
                    PanelEntryRow(
                        entry = entry,
                        isSelected = isSelected,
                        onSelect = {
                            onActivate()
                            component.onIntent(PanelIntent.SelectEntry(entry))
                        },
                        onOpen = {
                            onActivate()
                            component.onIntent(PanelIntent.OpenEntry(entry))
                        },
                    )
                }
            }
            Text(
                text = buildPanelFooter(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun ParentDirectoryRow(
    enabled: Boolean,
    onActivate: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF3F4F6))
                .combinedClickable(
                    interactionSource = null,
                    indication = null,
                    enabled = enabled,
                    onClick = onActivate,
                    onDoubleClick = onActivate,
                ).padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "[UP]",
                modifier = Modifier.width(48.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "..",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun PanelEntryRow(
    entry: PanelEntry,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    val background = if (isSelected) Color(0xFFE0F2FE) else Color(0xFFF9FAFB)
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val kindLabel =
        when (entry.kind) {
            PanelEntryKind.DIRECTORY -> "[DIR]"
            PanelEntryKind.FILE -> "[FILE]"
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(background)
                .border(width = if (isSelected) 1.dp else 0.dp, color = borderColor)
                .combinedClickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onSelect,
                    onDoubleClick = onOpen,
                ).padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = kindLabel,
                modifier = Modifier.width(48.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (entry.kind == PanelEntryKind.DIRECTORY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (entry.kind == PanelEntryKind.DIRECTORY) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    text = if (entry.kind == PanelEntryKind.DIRECTORY) "Directory" else "File",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatBrowseError(error: BrowseError): String =
    when (error) {
        is BrowseError.LocationNotFound -> "Location not found."
        is BrowseError.PermissionDenied -> "Permission denied."
        is BrowseError.PluginUnavailable -> "Plugin is unavailable."
        is BrowseError.PluginCrashed -> "Plugin crashed or stopped responding."
        is BrowseError.Unknown -> error.message
    }

private fun buildPanelFooter(state: io.github.dawciobiel.quickfile.core.panel.PanelState): String {
    val selection =
        state.selectedEntryName?.let { "Selected: $it" } ?: "Selected: none"
    return "${state.entries.size} entries | $selection"
}

@Composable
private fun JobRow(
    job: JobSnapshot,
    onCancel: () -> Unit,
) {
    val statusColor =
        when (job.status) {
            JobStatus.COMPLETED -> Color(0xFF067647)
            JobStatus.RUNNING -> Color(0xFF175CD3)
            JobStatus.FAILED -> Color(0xFFB42318)
            JobStatus.CANCELLED -> Color(0xFF7A5AF8)
            JobStatus.CANCELLING -> Color(0xFFB54708)
            JobStatus.PENDING -> Color(0xFF475467)
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF9FAFB))
                .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${job.kind} · ${job.status}",
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                )
                job.progress?.let { progress ->
                    Text(
                        text = "Progress: ${progress.completedUnits}/${progress.totalUnits} ${progress.details.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                job.recentLogLines.lastOrNull()?.let { logLine ->
                    Text(
                        text = logLine.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                job.errorMessage?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB42318),
                    )
                }
            }
            if (job.status == JobStatus.RUNNING || job.status == JobStatus.PENDING) {
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
            } else if (job.status == JobStatus.CANCELLING) {
                Text(
                    text = "Cancelling...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
