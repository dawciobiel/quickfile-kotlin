package io.github.dawciobiel.quickfile.app.support

import io.github.dawciobiel.quickfile.core.panel.PanelCoordinator
import io.github.dawciobiel.quickfile.core.panel.PanelEntry
import io.github.dawciobiel.quickfile.core.panel.PanelEntryKind
import io.github.dawciobiel.quickfile.core.panel.PanelState

class DemoPanelCoordinator : PanelCoordinator {
    override fun initialState(
        panelId: String,
        title: String,
        active: Boolean,
    ): PanelState =
        PanelState(
            panelId = panelId,
            title = title,
            currentLocation = if (panelId == "left") "~/workspace" else "~/downloads",
            entries = demoEntries(panelId = panelId),
            isActive = active,
            statusMessage = "Component tree is live. Plugin-backed browsing comes next.",
        )

    private fun demoEntries(panelId: String): List<PanelEntry> =
        if (panelId == "left") {
            listOf(
                PanelEntry("docs", PanelEntryKind.DIRECTORY),
                PanelEntry("plugin-api", PanelEntryKind.DIRECTORY),
                PanelEntry("README.md", PanelEntryKind.FILE),
            )
        } else {
            listOf(
                PanelEntry("archive.zip", PanelEntryKind.FILE),
                PanelEntry("photos", PanelEntryKind.DIRECTORY),
                PanelEntry("notes.txt", PanelEntryKind.FILE),
            )
        }
}
