package io.github.dawciobiel.quickfile.app.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import io.github.dawciobiel.quickfile.core.browse.BrowseException
import io.github.dawciobiel.quickfile.core.browse.BrowseGateway
import io.github.dawciobiel.quickfile.core.panel.PanelEntry
import io.github.dawciobiel.quickfile.core.panel.PanelEntryKind
import io.github.dawciobiel.quickfile.core.panel.PanelIntent
import io.github.dawciobiel.quickfile.core.panel.PanelState
import io.github.dawciobiel.quickfile.pluginruntime.PluginProcessBrowseGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Path

interface PanelComponent {
    val state: StateFlow<PanelState>

    fun onIntent(intent: PanelIntent)
}

class DefaultPanelComponent(
    componentContext: ComponentContext,
    panelId: String,
    title: String,
    initiallyActive: Boolean,
    initialLocation: String,
    private val browseGateway: BrowseGateway,
) : PanelComponent, ComponentContext by componentContext {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mutableState =
        MutableStateFlow(
            PanelState(
                panelId = panelId,
                title = title,
                currentLocation = initialLocation,
                entries = emptyList(),
                isActive = initiallyActive,
                isLoading = true,
                selectedEntryName = null,
                error = null,
                statusMessage = "Loading directory entries...",
            ),
        )

    override val state: StateFlow<PanelState> = mutableState.asStateFlow()

    init {
        lifecycle.subscribe(
            object : Lifecycle.Callbacks {
                override fun onDestroy() {
                    scope.cancel()
                    (browseGateway as? PluginProcessBrowseGateway)?.close()
                }
            },
        )

        browse(location = initialLocation)
    }

    override fun onIntent(intent: PanelIntent) {
        when (intent) {
            is PanelIntent.OpenLocation -> browse(location = intent.location)

            PanelIntent.Refresh -> browse(location = state.value.currentLocation)

            is PanelIntent.MoveSelection -> moveSelection(intent.offset)

            PanelIntent.NavigateUp -> {
                parentLocation(state.value.currentLocation)?.let(::browse)
                    ?: mutableState.update { current ->
                        current.copy(statusMessage = "Already at the top-most location.")
                    }
            }

            is PanelIntent.SetActive ->
                mutableState.update { current ->
                    current.copy(isActive = intent.active)
                }

            is PanelIntent.SelectEntry ->
                mutableState.update { current ->
                    current.copy(
                        selectedEntryName = intent.entry.name,
                        statusMessage = describeSelection(intent.entry),
                    )
                }

            PanelIntent.OpenSelectedEntry -> {
                val selectedEntry =
                    state.value.entries.firstOrNull { it.name == state.value.selectedEntryName }
                selectedEntry?.let { entry ->
                    onIntent(PanelIntent.OpenEntry(entry))
                }
            }

            is PanelIntent.OpenEntry -> {
                if (intent.entry.kind == PanelEntryKind.DIRECTORY) {
                    intent.entry.childLocation?.let(::browse)
                } else {
                    mutableState.update { current ->
                        current.copy(statusMessage = "File preview/open is not implemented yet.")
                    }
                }
            }
        }
    }

    private fun browse(location: String) {
        mutableState.update { current ->
                current.copy(
                    currentLocation = location,
                    isLoading = true,
                    selectedEntryName = null,
                    error = null,
                    statusMessage = "Loading $location ...",
                )
        }

        scope.launch {
            runCatching {
                browseGateway.listEntries(location)
            }.onSuccess { result ->
                val firstEntry = result.entries.firstOrNull()
                mutableState.update { current ->
                    current.copy(
                        currentLocation = result.location,
                        entries = result.entries,
                        isLoading = false,
                        selectedEntryName = firstEntry?.name,
                        error = null,
                        statusMessage = "${result.entries.size} entries loaded.",
                    )
                }
            }.onFailure { error ->
                val browseError = (error as? BrowseException)?.error
                mutableState.update { current ->
                    current.copy(
                        isLoading = false,
                        error = browseError,
                        statusMessage =
                            browseError?.let(::describeBrowseError)
                                ?: "Browse failed: ${error.message ?: "Unknown error"}",
                    )
                }
            }
        }
    }

    private fun parentLocation(location: String): String? =
        runCatching {
            Path.of(location).parent?.toString()
        }.getOrNull()

    private fun moveSelection(offset: Int) {
        val entries = state.value.entries
        if (entries.isEmpty()) {
            return
        }

        val currentIndex = entries.indexOfFirst { it.name == state.value.selectedEntryName }
        val nextIndex =
            when {
                currentIndex == -1 && offset > 0 -> 0
                currentIndex == -1 && offset < 0 -> entries.lastIndex
                else -> (currentIndex + offset).coerceIn(0, entries.lastIndex)
            }

        val nextEntry = entries[nextIndex]
        mutableState.update { current ->
            current.copy(
                selectedEntryName = nextEntry.name,
                statusMessage = describeSelection(nextEntry),
            )
        }
    }

    private fun describeSelection(entry: PanelEntry): String =
        when (entry.kind) {
            PanelEntryKind.DIRECTORY -> "Selected directory: ${entry.name}"
            PanelEntryKind.FILE -> "Selected file: ${entry.name}"
        }

    private fun describeBrowseError(error: io.github.dawciobiel.quickfile.core.errors.BrowseError): String =
        when (error) {
            is io.github.dawciobiel.quickfile.core.errors.BrowseError.LocationNotFound ->
                "Location not found: ${error.location}"

            is io.github.dawciobiel.quickfile.core.errors.BrowseError.PermissionDenied ->
                "Permission denied: ${error.location}"

            is io.github.dawciobiel.quickfile.core.errors.BrowseError.PluginUnavailable ->
                "Plugin unavailable: ${error.pluginId}"

            is io.github.dawciobiel.quickfile.core.errors.BrowseError.PluginCrashed ->
                "Plugin crashed: ${error.pluginId} (${error.cause})"

            is io.github.dawciobiel.quickfile.core.errors.BrowseError.Unknown ->
                "Browse failed: ${error.message}"
        }
}
