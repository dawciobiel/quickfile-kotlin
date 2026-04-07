package io.github.dawciobiel.quickfile.core.browse

import io.github.dawciobiel.quickfile.core.errors.BrowseError
import io.github.dawciobiel.quickfile.core.panel.PanelEntry

data class BrowseResult(
    val location: String,
    val entries: List<PanelEntry>,
)

class BrowseException(
    val error: BrowseError,
) : RuntimeException(error.toString())

interface BrowseGateway {
    suspend fun listEntries(location: String): BrowseResult
}
