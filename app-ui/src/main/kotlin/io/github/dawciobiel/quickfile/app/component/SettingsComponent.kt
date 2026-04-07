package io.github.dawciobiel.quickfile.app.component

import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsState(
    val summary: String,
)

interface SettingsComponent {
    val state: StateFlow<SettingsState>
}

class DefaultSettingsComponent(
    componentContext: ComponentContext,
) : SettingsComponent, ComponentContext by componentContext {
    private val mutableState =
        MutableStateFlow(
            SettingsState(
                summary = "Settings navigation is reserved for a later milestone.",
            ),
        )

    override val state: StateFlow<SettingsState> = mutableState.asStateFlow()
}
