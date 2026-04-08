package io.github.dawciobiel.quickfile.app.keymap

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ActionId(
    val configKey: String,
) {
    PANEL_SWITCH("panel.switch"),
    PANEL_COPY("panel.copy"),
    PANEL_MOVE("panel.move"),
    PANEL_DELETE("panel.delete"),
    PANEL_SELECT_NEXT("panel.selectNext"),
    PANEL_SELECT_PREVIOUS("panel.selectPrevious"),
    PANEL_OPEN_SELECTED("panel.openSelected"),
    PANEL_NAVIGATE_UP("panel.navigateUp"),
    ;

    companion object {
        fun fromConfigKey(configKey: String): ActionId? = entries.firstOrNull { it.configKey == configKey }
    }
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
    private val json = Json { ignoreUnknownKeys = true }

    private val shortcuts: Map<ActionId, Shortcut> =
        loadFromResource("/default-keymap.json")
            ?.takeIf { loaded -> loaded.keys.containsAll(ActionId.entries) }
            ?: fallbackShortcuts()

    fun shortcutFor(actionId: ActionId): Shortcut = requireNotNull(shortcuts[actionId])

    fun matches(
        actionId: ActionId,
        event: KeyEvent,
    ): Boolean = shortcutFor(actionId).matches(event)

    private fun loadFromResource(resourcePath: String): Map<ActionId, Shortcut>? {
        val rawJson =
            AppKeymap::class.java.getResourceAsStream(resourcePath)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return null

        return runCatching {
            json
                .decodeFromString<Map<String, ShortcutConfig>>(rawJson)
                .mapNotNull { (actionKey, config) ->
                    val actionId = ActionId.fromConfigKey(actionKey) ?: return@mapNotNull null
                    val key = config.key.toComposeKey() ?: return@mapNotNull null
                    actionId to Shortcut(key = key, ctrl = config.ctrl)
                }.toMap()
        }.getOrNull()
    }

    private fun fallbackShortcuts(): Map<ActionId, Shortcut> =
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
}

@Serializable
private data class ShortcutConfig(
    val key: String,
    val ctrl: Boolean = false,
)

private fun String.toComposeKey(): Key? =
    when (this) {
        "Tab" -> Key.Tab
        "C" -> Key.C
        "M" -> Key.M
        "Delete" -> Key.Delete
        "DirectionDown" -> Key.DirectionDown
        "DirectionUp" -> Key.DirectionUp
        "Enter" -> Key.Enter
        "Backspace" -> Key.Backspace
        else -> null
    }
