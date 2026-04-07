package io.github.dawciobiel.quickfile.app.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import io.github.dawciobiel.quickfile.core.browse.BrowseGateway
import io.github.dawciobiel.quickfile.core.panel.PanelEntryKind
import io.github.dawciobiel.quickfile.core.panel.PanelIntent
import io.github.dawciobiel.quickfile.core.panel.PanelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
                statusMessage = "Loading directory entries...",
            ),
        )

    override val state: StateFlow<PanelState> = mutableState.asStateFlow()

    init {
        lifecycle.subscribe(
            object : Lifecycle.Callbacks {
                override fun onDestroy() {
                    scope.cancel()
                }
            },
        )

        browse(location = initialLocation)
    }

    override fun onIntent(intent: PanelIntent) {
        when (intent) {
            is PanelIntent.OpenLocation -> browse(location = intent.location)

            PanelIntent.Refresh -> browse(location = state.value.currentLocation)

            is PanelIntent.SetActive ->
                mutableState.update { current ->
                    current.copy(isActive = intent.active)
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
                statusMessage = "Loading $location ...",
            )
        }

        scope.launch {
            runCatching {
                browseGateway.listEntries(location)
            }.onSuccess { result ->
                mutableState.update { current ->
                    current.copy(
                        currentLocation = result.location,
                        entries = result.entries,
                        isLoading = false,
                        statusMessage = "${result.entries.size} entries loaded.",
                    )
                }
            }.onFailure { error ->
                mutableState.update { current ->
                    current.copy(
                        isLoading = false,
                        statusMessage = "Browse failed: ${error.message ?: "Unknown error"}",
                    )
                }
            }
        }
    }
}
