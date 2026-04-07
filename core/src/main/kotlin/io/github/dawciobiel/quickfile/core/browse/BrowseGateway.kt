package io.github.dawciobiel.quickfile.core.browse

import io.github.dawciobiel.quickfile.core.panel.PanelEntry

data class BrowseResult(
    val location: String,
    val entries: List<PanelEntry>,
)

interface BrowseGateway {
    suspend fun listEntries(location: String): BrowseResult
}
