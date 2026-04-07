package io.github.dawciobiel.quickfile.core.panel

import io.github.dawciobiel.quickfile.core.errors.BrowseError

data class PanelState(
    val panelId: String,
    val title: String,
    val currentLocation: String,
    val entries: List<PanelEntry>,
    val isActive: Boolean,
    val isLoading: Boolean = false,
    val error: BrowseError? = null,
    val statusMessage: String? = null,
)

data class PanelEntry(
    val name: String,
    val kind: PanelEntryKind,
    val childLocation: String? = null,
)

enum class PanelEntryKind {
    DIRECTORY,
    FILE,
}

sealed interface PanelIntent {
    data object Refresh : PanelIntent
    data class OpenLocation(val location: String) : PanelIntent
    data class SetActive(val active: Boolean) : PanelIntent
    data class OpenEntry(val entry: PanelEntry) : PanelIntent
}
