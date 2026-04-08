package io.github.dawciobiel.quickfile.app.component

import com.arkivanov.decompose.ComponentContext
import io.github.dawciobiel.quickfile.app.keymap.AppKeymap
import io.github.dawciobiel.quickfile.app.keymap.KeyBindingView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsState(
    val summary: String,
    val keyBindings: List<KeyBindingView>,
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
                summary = "Active shortcuts are loaded from the bundled defaults and optional user keymap overrides.",
                keyBindings = AppKeymap.bindings(),
            ),
        )

    override val state: StateFlow<SettingsState> = mutableState.asStateFlow()
}
