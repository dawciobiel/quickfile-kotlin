package io.github.dawciobiel.quickfile.pluginruntime

import io.github.dawciobiel.quickfile.core.jobs.JobStatus
import io.github.dawciobiel.quickfile.core.jobs.OperationKind
import io.github.dawciobiel.quickfile.core.panel.PanelEntryKind
import io.github.dawciobiel.quickfile.pluginruntime.discovery.FileSystemPluginDiscovery
import io.github.dawciobiel.quickfile.pluginruntime.discovery.PluginCompatibilityStatus
import kotlinx.coroutines.runBlocking
import java.nio.file.StandardOpenOption
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.readText
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

    @Test
    fun `file operations gateway copies file into target directory`() {
        val pluginInstallDir = installReferencePlugin()

        val discovery =
            FileSystemPluginDiscovery(
                pluginsDirectory = pluginInstallDir.parent,
            ).discover()
        val discoveredPlugin = discovery.discoveredPlugins.single()

        val sourceRoot = Files.createTempDirectory("quickfile-copy-source")
        val targetRoot = Files.createTempDirectory("quickfile-copy-target")
        val sourceFile = sourceRoot.resolve("sample.txt")
            Files.writeString(
                sourceFile,
                "QuickFile integration copy test",
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )

        val gateway = PluginProcessFileOperationsGateway(discoveredPlugin)
        val events = mutableListOf<FileOperationEvent>()
        try {
            runBlocking {
                gateway.startOperation(
                    jobId = "copy-test-job",
                    kind = OperationKind.COPY,
                    sourceLocation = sourceFile.toString(),
                    destinationLocation = targetRoot.toString(),
                    onEvent = events::add,
                )
            }

            val copiedFile = targetRoot.resolve("sample.txt")
            assertTrue(copiedFile.exists())
            assertEquals("QuickFile integration copy test", copiedFile.readText())
            assertTrue(events.any { it is FileOperationEvent.StatusChanged && it.status == JobStatus.COMPLETED })
            assertTrue(events.any { it is FileOperationEvent.ProgressReported })
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `file operations gateway moves file into target directory`() {
        val discoveredPlugin = discoverReferencePlugin()
        val sourceRoot = Files.createTempDirectory("quickfile-move-source")
        val targetRoot = Files.createTempDirectory("quickfile-move-target")
        val sourceFile = sourceRoot.resolve("move-me.txt")
        Files.writeString(sourceFile, "QuickFile integration move test")

        val gateway = PluginProcessFileOperationsGateway(discoveredPlugin)
        try {
            runBlocking {
                gateway.startOperation(
                    jobId = "move-test-job",
                    kind = OperationKind.MOVE,
                    sourceLocation = sourceFile.toString(),
                    destinationLocation = targetRoot.toString(),
                    onEvent = {},
                )
            }

            val movedFile = targetRoot.resolve("move-me.txt")
            assertTrue(movedFile.exists())
            assertEquals("QuickFile integration move test", movedFile.readText())
            assertTrue(sourceFile.notExists())
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `file operations gateway deletes file`() {
        val discoveredPlugin = discoverReferencePlugin()
        val sourceRoot = Files.createTempDirectory("quickfile-delete-source")
        val sourceFile = sourceRoot.resolve("delete-me.txt")
        Files.writeString(sourceFile, "QuickFile integration delete test")

        val gateway = PluginProcessFileOperationsGateway(discoveredPlugin)
        try {
            runBlocking {
                gateway.startOperation(
                    jobId = "delete-test-job",
                    kind = OperationKind.DELETE,
                    sourceLocation = sourceFile.toString(),
                    destinationLocation = null,
                    onEvent = {},
                )
            }

            assertTrue(sourceFile.notExists())
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `file operations gateway renames file in place`() {
        val discoveredPlugin = discoverReferencePlugin()
        val sourceRoot = Files.createTempDirectory("quickfile-rename-source")
        val sourceFile = sourceRoot.resolve("rename-me.txt")
        val renamedFile = sourceRoot.resolve("renamed.txt")
        Files.writeString(sourceFile, "QuickFile integration rename test")

        val gateway = PluginProcessFileOperationsGateway(discoveredPlugin)
        try {
            runBlocking {
                gateway.startOperation(
                    jobId = "rename-test-job",
                    kind = OperationKind.RENAME,
                    sourceLocation = sourceFile.toString(),
                    destinationLocation = renamedFile.toString(),
                    onEvent = {},
                )
            }

            assertTrue(sourceFile.notExists())
            assertTrue(renamedFile.exists())
            assertEquals("QuickFile integration rename test", renamedFile.readText())
        } finally {
            gateway.close()
        }
    }

    private fun discoverReferencePlugin() =
        FileSystemPluginDiscovery(
            pluginsDirectory = installReferencePlugin().parent,
        ).discover().discoveredPlugins.single()

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
