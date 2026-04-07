package io.github.dawciobiel.quickfile.pluginruntime

import io.github.dawciobiel.quickfile.core.panel.PanelEntryKind
import io.github.dawciobiel.quickfile.pluginruntime.discovery.FileSystemPluginDiscovery
import io.github.dawciobiel.quickfile.pluginruntime.discovery.PluginCompatibilityStatus
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginRuntimeIntegrationTest {
    @Test
    fun `discovery finds installed local fs plugin and browse gateway returns entries`() {
        val pluginInstallDir = installReferencePlugin()

        val discovery =
            FileSystemPluginDiscovery(
                pluginsDirectory = pluginInstallDir.parent,
            ).discover()

        val discoveredPlugin =
            discovery.discoveredPlugins.singleOrNull()
        assertNotNull(discoveredPlugin)
        assertEquals(PluginCompatibilityStatus.COMPATIBLE, discoveredPlugin.status)
        assertEquals("io.github.dawciobiel.local-fs", discoveredPlugin.manifest?.id)
        assertTrue(discoveredPlugin.manifest?.capabilities?.contains("file-source") == true)

        val gateway = PluginProcessBrowseGateway(discoveredPlugin)
        try {
            val result =
                runBlocking {
                    gateway.listEntries(System.getProperty("user.home"))
                }

            assertEquals(System.getProperty("user.home"), result.location)
            assertTrue(result.entries.isNotEmpty())
            assertTrue(result.entries.any { it.kind == PanelEntryKind.DIRECTORY || it.kind == PanelEntryKind.FILE })
        } finally {
            gateway.close()
        }
    }

    private fun installReferencePlugin(): Path {
        val zipPath =
            Path.of(
                requireNotNull(System.getProperty("quickfile.localFsPluginZip")) {
                    "System property quickfile.localFsPluginZip is missing."
                },
            )
        require(Files.exists(zipPath)) {
            "Reference plugin ZIP does not exist. Run :plugins:local-fs-provider:pluginZip before tests."
        }

        val tempRoot = Files.createTempDirectory("quickfile-plugin-test")
        val installDir = tempRoot.resolve("io.github.dawciobiel.local-fs")
        installDir.createDirectories()

        java.util.zip.ZipFile(zipPath.toFile()).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val target = installDir.resolve(entry.name)
                if (entry.isDirectory) {
                    target.createDirectories()
                } else {
                    target.parent?.createDirectories()
                    zip.getInputStream(entry).use { input ->
                        Files.copy(input, target)
                    }
                }
            }
        }

        return installDir
    }
}
