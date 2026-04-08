package io.github.dawciobiel.quickfile.app.keymap

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

enum class ActionId(
    val configKey: String,
    val displayName: String,
) {
    PANEL_SWITCH("panel.switch", "Switch active panel"),
    PANEL_COPY("panel.copy", "Copy selected entry"),
    PANEL_MOVE("panel.move", "Move selected entry"),
    PANEL_DELETE("panel.delete", "Delete selected entry"),
    PANEL_SELECT_NEXT("panel.selectNext", "Select next entry"),
    PANEL_SELECT_PREVIOUS("panel.selectPrevious", "Select previous entry"),
    PANEL_OPEN_SELECTED("panel.openSelected", "Open selected entry"),
    PANEL_NAVIGATE_UP("panel.navigateUp", "Open parent directory"),
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

enum class ShortcutSource {
    DEFAULT,
    USER_OVERRIDE,
}

data class KeyBindingView(
    val actionId: ActionId,
    val shortcut: Shortcut,
    val source: ShortcutSource,
)

sealed interface KeymapUpdateResult {
    data object Saved : KeymapUpdateResult

    data class Conflict(
        val conflictingAction: ActionId,
    ) : KeymapUpdateResult

    data class UnsupportedKey(
        val keyLabel: String,
    ) : KeymapUpdateResult
}

object AppKeymap {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    private val supportedKeys: Map<String, Key> =
        buildMap {
            put("Tab", Key.Tab)
            put("Delete", Key.Delete)
            put("Backspace", Key.Backspace)
            put("Enter", Key.Enter)
            put("NumPadEnter", Key.NumPadEnter)
            put("DirectionUp", Key.DirectionUp)
            put("DirectionDown", Key.DirectionDown)
            put("DirectionLeft", Key.DirectionLeft)
            put("DirectionRight", Key.DirectionRight)
            put("Home", Key.Home)
            put("End", Key.MoveEnd)
            put("PageUp", Key.PageUp)
            put("PageDown", Key.PageDown)
            put("Insert", Key.Insert)
            put("Escape", Key.Escape)
            put("Spacebar", Key.Spacebar)

            ('A'..'Z').forEach { letter ->
                val label = letter.toString()
                put(label, label.toComposeLetterKey())
            }

            ('0'..'9').forEach { digit ->
                val label = digit.toString()
                put(label, label.toComposeDigitKey())
            }

            (1..12).forEach { functionIndex ->
                val label = "F$functionIndex"
                put(label, label.toComposeFunctionKey())
            }
        }

    private val supportedKeyLabels: Map<Key, String> = supportedKeys.entries.associate { (label, key) -> key to label }

    private val defaultShortcuts: Map<ActionId, Shortcut> =
        loadDefaultShortcuts()
            ?.takeIf { loaded -> loaded.keys.containsAll(ActionId.entries) }
            ?: fallbackShortcuts()

    private val keymapPath: Path = userKeymapPath()

    @Volatile
    private var userOverrides: Map<ActionId, Shortcut> = loadUserOverrides(keymapPath)

    @Volatile
    private var shortcuts: Map<ActionId, Shortcut> = defaultShortcuts + userOverrides

    fun shortcutFor(actionId: ActionId): Shortcut = requireNotNull(shortcuts[actionId])

    fun keymapPathString(): String = keymapPath.toString()

    fun bindings(): List<KeyBindingView> =
        ActionId.entries.map { actionId ->
            KeyBindingView(
                actionId = actionId,
                shortcut = shortcutFor(actionId),
                source = if (userOverrides.containsKey(actionId)) ShortcutSource.USER_OVERRIDE else ShortcutSource.DEFAULT,
            )
        }

    fun matches(
        actionId: ActionId,
        event: KeyEvent,
    ): Boolean = shortcutFor(actionId).matches(event)

    fun shortcutFrom(event: KeyEvent): Shortcut? {
        val key = event.key
        if (!supportedKeyLabels.containsKey(key)) {
            return null
        }

        return Shortcut(key = key, ctrl = event.isCtrlPressed)
    }

    fun shortcutLabel(key: Key): String = supportedKeyLabels[key] ?: key.toString().removePrefix("Key.")

    fun shortcutConfigKey(key: Key): String? = supportedKeyLabels[key]

    fun userOverrideCount(): Int = userOverrides.size

    @Synchronized
    fun updateShortcut(
        actionId: ActionId,
        shortcut: Shortcut,
    ): KeymapUpdateResult {
        if (shortcut.toShortcutConfig() == null) {
            return KeymapUpdateResult.UnsupportedKey(shortcut.label())
        }

        val conflictingAction =
            shortcuts.entries.firstOrNull { (existingAction, existingShortcut) ->
                existingAction != actionId && existingShortcut == shortcut
            }?.key
        if (conflictingAction != null) {
            return KeymapUpdateResult.Conflict(conflictingAction)
        }

        val updatedOverrides =
            if (defaultShortcuts[actionId] == shortcut) {
                userOverrides - actionId
            } else {
                userOverrides + (actionId to shortcut)
            }

        persistUserOverrides(updatedOverrides)
        userOverrides = updatedOverrides
        shortcuts = defaultShortcuts + userOverrides
        return KeymapUpdateResult.Saved
    }

    @Synchronized
    fun resetShortcut(actionId: ActionId) {
        if (!userOverrides.containsKey(actionId)) {
            return
        }

        val updatedOverrides = userOverrides - actionId
        persistUserOverrides(updatedOverrides)
        userOverrides = updatedOverrides
        shortcuts = defaultShortcuts + userOverrides
    }

    @Synchronized
    fun resetAllShortcuts() {
        if (userOverrides.isEmpty()) {
            return
        }

        persistUserOverrides(emptyMap())
        userOverrides = emptyMap()
        shortcuts = defaultShortcuts
    }

    private fun loadDefaultShortcuts(): Map<ActionId, Shortcut>? =
        AppKeymap::class.java.getResourceAsStream("/default-keymap.json")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?.let(::parseShortcutMap)

    private fun loadUserOverrides(path: Path): Map<ActionId, Shortcut> {
        if (!Files.exists(path)) {
            return emptyMap()
        }

        return runCatching { Files.readString(path) }
            .map(::parseShortcutMap)
            .getOrNull()
            ?: emptyMap()
    }

    private fun parseShortcutMap(rawJson: String): Map<ActionId, Shortcut> {
        return json
            .decodeFromString<Map<String, ShortcutConfig>>(rawJson)
            .mapNotNull { (actionKey, config) ->
                val actionId = ActionId.fromConfigKey(actionKey) ?: return@mapNotNull null
                val key = supportedKeys[config.key] ?: return@mapNotNull null
                actionId to Shortcut(key = key, ctrl = config.ctrl)
            }.toMap()
    }

    private fun persistUserOverrides(overrides: Map<ActionId, Shortcut>) {
        Files.createDirectories(keymapPath.parent)
        val serialized =
            overrides
                .mapValues { (_, shortcut) -> shortcut.toShortcutConfig() }
                .filterValues { it != null }
                .mapValues { (_, config) -> requireNotNull(config) }
                .entries
                .sortedBy { it.key.configKey }
                .associate { (actionId, config) -> actionId.configKey to config }

        Files.writeString(
            keymapPath,
            json.encodeToString(serialized),
        )
    }

    private fun userKeymapPath(): Path {
        val userHome = Path.of(System.getProperty("user.home"))
        val osName = System.getProperty("os.name").lowercase()

        return when {
            osName.contains("win") -> {
                val appData = System.getenv("APPDATA") ?: userHome.resolve("AppData/Roaming").toString()
                Path.of(appData).resolve("QuickFile").resolve("keymap.json")
            }

            osName.contains("mac") -> {
                userHome.resolve("Library/Application Support/QuickFile/keymap.json")
            }

            else -> {
                val xdgConfigHome = System.getenv("XDG_CONFIG_HOME")
                val base =
                    if (xdgConfigHome.isNullOrBlank()) {
                        userHome.resolve(".config")
                    } else {
                        Path.of(xdgConfigHome)
                    }
                base.resolve("quickfile/keymap.json")
            }
        }
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

private fun Shortcut.toShortcutConfig(): ShortcutConfig? {
    val keyLabel = AppKeymap.shortcutConfigKey(key) ?: return null
    return ShortcutConfig(
        key = keyLabel,
        ctrl = ctrl,
    )
}

private fun String.toComposeLetterKey(): Key =
    when (this) {
        "A" -> Key.A
        "B" -> Key.B
        "C" -> Key.C
        "D" -> Key.D
        "E" -> Key.E
        "F" -> Key.F
        "G" -> Key.G
        "H" -> Key.H
        "I" -> Key.I
        "J" -> Key.J
        "K" -> Key.K
        "L" -> Key.L
        "M" -> Key.M
        "N" -> Key.N
        "O" -> Key.O
        "P" -> Key.P
        "Q" -> Key.Q
        "R" -> Key.R
        "S" -> Key.S
        "T" -> Key.T
        "U" -> Key.U
        "V" -> Key.V
        "W" -> Key.W
        "X" -> Key.X
        "Y" -> Key.Y
        "Z" -> Key.Z
        else -> error("Unsupported letter key: $this")
    }

private fun String.toComposeDigitKey(): Key =
    when (this) {
        "0" -> Key.Zero
        "1" -> Key.One
        "2" -> Key.Two
        "3" -> Key.Three
        "4" -> Key.Four
        "5" -> Key.Five
        "6" -> Key.Six
        "7" -> Key.Seven
        "8" -> Key.Eight
        "9" -> Key.Nine
        else -> error("Unsupported digit key: $this")
    }

private fun String.toComposeFunctionKey(): Key =
    when (this) {
        "F1" -> Key.F1
        "F2" -> Key.F2
        "F3" -> Key.F3
        "F4" -> Key.F4
        "F5" -> Key.F5
        "F6" -> Key.F6
        "F7" -> Key.F7
        "F8" -> Key.F8
        "F9" -> Key.F9
        "F10" -> Key.F10
        "F11" -> Key.F11
        "F12" -> Key.F12
        else -> error("Unsupported function key: $this")
    }
