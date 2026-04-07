package io.github.dawciobiel.quickfile.app.component

import com.arkivanov.decompose.ComponentContext
import io.github.dawciobiel.quickfile.core.panel.PanelCoordinator
import io.github.dawciobiel.quickfile.core.panel.PanelIntent
import io.github.dawciobiel.quickfile.core.panel.PanelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface PanelComponent {
    val state: StateFlow<PanelState>

    fun onIntent(intent: PanelIntent)
}

class DefaultPanelComponent(
    componentContext: ComponentContext,
    panelId: String,
    title: String,
    initiallyActive: Boolean,
    private val panelCoordinator: PanelCoordinator,
) : PanelComponent, ComponentContext by componentContext {
    private val mutableState =
        MutableStateFlow(
            panelCoordinator.initialState(
                panelId = panelId,
                title = title,
                active = initiallyActive,
            ),
        )

    override val state: StateFlow<PanelState> = mutableState.asStateFlow()

    override fun onIntent(intent: PanelIntent) {
        when (intent) {
            is PanelIntent.OpenLocation ->
                mutableState.update { current ->
                    current.copy(
                        currentLocation = intent.location,
                        statusMessage = "Panel skeleton received an open-location intent.",
                    )
                }

            PanelIntent.Refresh ->
                mutableState.update { current ->
                    current.copy(
                        statusMessage = "Refresh requested. Runtime wiring comes in Milestone 2.",
                    )
                }

            is PanelIntent.SetActive ->
                mutableState.update { current ->
                    current.copy(isActive = intent.active)
                }
        }
    }
}
