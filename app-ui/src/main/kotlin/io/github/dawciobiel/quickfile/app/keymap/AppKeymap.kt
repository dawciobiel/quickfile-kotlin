package io.github.dawciobiel.quickfile.app.keymap

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key

enum class ActionId {
    PANEL_SWITCH,
    PANEL_COPY,
    PANEL_MOVE,
    PANEL_DELETE,
    PANEL_SELECT_NEXT,
    PANEL_SELECT_PREVIOUS,
    PANEL_OPEN_SELECTED,
    PANEL_NAVIGATE_UP,
}

data class Shortcut(
    val key: Key,
    val ctrl: Boolean = false,
) {
    fun matches(event: KeyEvent): Boolean = event.key == key && event.isCtrlPressed == ctrl

    fun label(): String {
        val keyLabel =
            when (key) {
                Key.DirectionDown -> "Down"
                Key.DirectionUp -> "Up"
                Key.Backspace -> "Backspace"
                Key.Delete -> "Delete"
                Key.Enter, Key.NumPadEnter -> "Enter"
                Key.Tab -> "Tab"
                else -> key.toString().removePrefix("Key.")
            }
        return if (ctrl) "Ctrl+$keyLabel" else keyLabel
    }
}

object AppKeymap {
    private val shortcuts =
        mapOf(
            ActionId.PANEL_SWITCH to Shortcut(Key.Tab),
            ActionId.PANEL_COPY to Shortcut(Key.C, ctrl = true),
            ActionId.PANEL_MOVE to Shortcut(Key.M, ctrl = true),
            ActionId.PANEL_DELETE to Shortcut(Key.Delete),
            ActionId.PANEL_SELECT_NEXT to Shortcut(Key.DirectionDown),
            ActionId.PANEL_SELECT_PREVIOUS to Shortcut(Key.DirectionUp),
            ActionId.PANEL_OPEN_SELECTED to Shortcut(Key.Enter),
            ActionId.PANEL_NAVIGATE_UP to Shortcut(Key.Backspace),
        )

    fun shortcutFor(actionId: ActionId): Shortcut = requireNotNull(shortcuts[actionId])

    fun matches(
        actionId: ActionId,
        event: KeyEvent,
    ): Boolean = shortcutFor(actionId).matches(event)
}
