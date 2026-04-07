package io.github.dawciobiel.quickfile.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.dawciobiel.quickfile.app.component.AppComponent
import io.github.dawciobiel.quickfile.core.errors.BrowseError
import io.github.dawciobiel.quickfile.core.panel.PanelEntryKind
import io.github.dawciobiel.quickfile.core.panel.PanelIntent

@Composable
fun AppContent(component: AppComponent) {
    val runtimeSnapshot by component.pluginRuntimeSnapshot.collectAsState()
    val jobsState by component.jobs.state.collectAsState()

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
                    Text("Jobs", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = jobsState.summary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelCard(
    modifier: Modifier = Modifier,
    component: io.github.dawciobiel.quickfile.app.component.PanelComponent,
    onActivate: () -> Unit,
) {
    val state by component.state.collectAsState()
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
            OutlinedButton(
                onClick = {
                    onActivate()
                    component.onIntent(PanelIntent.Refresh)
                },
            ) {
                Text(if (state.isLoading) "Refreshing..." else "Refresh")
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
                items(state.entries) { entry ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF9FAFB))
                                .clickable {
                                    onActivate()
                                    component.onIntent(PanelIntent.OpenEntry(entry))
                                }
                                .padding(10.dp),
                    ) {
                        val prefix =
                            when (entry.kind) {
                                PanelEntryKind.DIRECTORY -> "[DIR]"
                                PanelEntryKind.FILE -> "[FILE]"
                            }
                        Text("$prefix ${entry.name}")
                    }
                }
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
