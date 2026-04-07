package io.github.dawciobiel.quickfile.core.panel

data class PanelState(
    val panelId: String,
    val title: String,
    val currentLocation: String,
    val entries: List<PanelEntry>,
    val isActive: Boolean,
    val statusMessage: String? = null,
)

data class PanelEntry(
    val name: String,
    val kind: PanelEntryKind,
)

enum class PanelEntryKind {
    DIRECTORY,
    FILE,
}

sealed interface PanelIntent {
    data object Refresh : PanelIntent
    data class OpenLocation(val location: String) : PanelIntent
    data class SetActive(val active: Boolean) : PanelIntent
}

interface PanelCoordinator {
    fun initialState(
        panelId: String,
        title: String,
        active: Boolean,
    ): PanelState
}
