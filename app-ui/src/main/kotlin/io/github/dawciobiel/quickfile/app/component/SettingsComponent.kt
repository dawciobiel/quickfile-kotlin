package io.github.dawciobiel.quickfile.app.component

import com.arkivanov.decompose.ComponentContext
import io.github.dawciobiel.quickfile.app.keymap.ActionId
import io.github.dawciobiel.quickfile.app.keymap.AppKeymap
import io.github.dawciobiel.quickfile.app.keymap.KeymapUpdateResult
import io.github.dawciobiel.quickfile.app.keymap.KeyBindingView
import io.github.dawciobiel.quickfile.app.keymap.Shortcut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsState(
    val summary: String,
    val keymapPath: String,
    val keyBindings: List<KeyBindingView>,
    val overrideCount: Int,
    val editingActionId: ActionId? = null,
    val message: String? = null,
)

interface SettingsComponent {
    val state: StateFlow<SettingsState>

    fun startEditing(actionId: ActionId)

    fun cancelEditing()

    fun applyShortcut(shortcut: Shortcut)

    fun showUnsupportedShortcut(keyLabel: String)

    fun resetShortcut(actionId: ActionId)

    fun resetAllShortcuts()
}

class DefaultSettingsComponent(
    componentContext: ComponentContext,
) : SettingsComponent, ComponentContext by componentContext {
    private val mutableState =
        MutableStateFlow(
            SettingsState(
                summary = "Active shortcuts are loaded from the bundled defaults and optional user keymap overrides.",
                keymapPath = AppKeymap.keymapPathString(),
                keyBindings = AppKeymap.bindings(),
                overrideCount = AppKeymap.userOverrideCount(),
            ),
        )

    override val state: StateFlow<SettingsState> = mutableState.asStateFlow()

    override fun startEditing(actionId: ActionId) {
        mutableState.value =
            mutableState.value.copy(
                editingActionId = actionId,
                message = null,
            )
    }

    override fun cancelEditing() {
        mutableState.value =
            mutableState.value.copy(
                editingActionId = null,
                message = null,
            )
    }

    override fun applyShortcut(shortcut: Shortcut) {
        val actionId = mutableState.value.editingActionId ?: return
        when (val result = AppKeymap.updateShortcut(actionId, shortcut)) {
            KeymapUpdateResult.Saved -> {
                mutableState.value =
                    mutableState.value.copy(
                        keyBindings = AppKeymap.bindings(),
                        overrideCount = AppKeymap.userOverrideCount(),
                        editingActionId = null,
                        message = "Saved ${actionId.displayName.lowercase()} as ${shortcut.label()}.",
                    )
            }

            is KeymapUpdateResult.Conflict -> {
                mutableState.value =
                    mutableState.value.copy(
                        message = "Conflict: ${shortcut.label()} is already assigned to ${result.conflictingAction.displayName.lowercase()}.",
                    )
            }

            is KeymapUpdateResult.UnsupportedKey -> {
                mutableState.value =
                    mutableState.value.copy(
                        message = "Unsupported shortcut: ${result.keyLabel}.",
                    )
            }
        }
    }

    override fun showUnsupportedShortcut(keyLabel: String) {
        mutableState.value =
            mutableState.value.copy(
                message = "Unsupported shortcut: $keyLabel.",
            )
    }

    override fun resetShortcut(actionId: ActionId) {
        AppKeymap.resetShortcut(actionId)
        mutableState.value =
            mutableState.value.copy(
                keyBindings = AppKeymap.bindings(),
                overrideCount = AppKeymap.userOverrideCount(),
                editingActionId = null,
                message = "Reset ${actionId.displayName.lowercase()} to the bundled default.",
            )
    }

    override fun resetAllShortcuts() {
        AppKeymap.resetAllShortcuts()
        mutableState.value =
            mutableState.value.copy(
                keyBindings = AppKeymap.bindings(),
                overrideCount = AppKeymap.userOverrideCount(),
                editingActionId = null,
                message = "Reset all shortcut overrides to the bundled defaults.",
            )
    }
}
