package io.github.dawciobiel.quickfile.pluginruntime.discovery

import io.github.dawciobiel.quickfile.pluginapi.PluginApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText

data class PluginRuntimeDiscoverySnapshot(
    val pluginsDirectory: Path,
    val discoveredPlugins: List<DiscoveredPlugin>,
) {
    val compatiblePlugins: List<DiscoveredPlugin>
        get() = discoveredPlugins.filter { it.status == PluginCompatibilityStatus.COMPATIBLE }
}

data class DiscoveredPlugin(
    val directory: Path,
    val manifest: PluginManifest?,
    val status: PluginCompatibilityStatus,
    val message: String,
)

enum class PluginCompatibilityStatus {
    COMPATIBLE,
    INCOMPATIBLE,
    INVALID_MANIFEST,
    MISSING_MANIFEST,
}

data class PluginManifest(
    val id: String,
    val version: String,
    val apiVersion: Int,
    val capabilities: List<String>,
    val entrypoint: PluginEntrypoint,
    val name: String? = null,
    val description: String? = null,
    val author: String? = null,
    val homepage: String? = null,
    val license: String? = null,
)

data class PluginEntrypoint(
    val type: String,
    val mainClass: String,
    val classpath: List<String>,
)

interface PluginDiscovery {
    fun discover(): PluginRuntimeDiscoverySnapshot
}

class FileSystemPluginDiscovery(
    private val pluginsDirectory: Path,
    private val hostApiVersion: Int = PluginApi.VERSION,
) : PluginDiscovery {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    override fun discover(): PluginRuntimeDiscoverySnapshot {
        if (!pluginsDirectory.exists()) {
            return PluginRuntimeDiscoverySnapshot(
                pluginsDirectory = pluginsDirectory,
                discoveredPlugins = emptyList(),
            )
        }

        val plugins =
            Files.list(pluginsDirectory).use { paths ->
                paths
                    .filter { it.isDirectory() }
                    .sorted(compareBy { it.name.lowercase() })
                    .map(::inspectPluginDirectory)
                    .toList()
            }

        return PluginRuntimeDiscoverySnapshot(
            pluginsDirectory = pluginsDirectory,
            discoveredPlugins = plugins,
        )
    }

    private fun inspectPluginDirectory(directory: Path): DiscoveredPlugin {
        val manifestPath = directory.resolve("plugin.json")
        if (!manifestPath.exists()) {
            return DiscoveredPlugin(
                directory = directory,
                manifest = null,
                status = PluginCompatibilityStatus.MISSING_MANIFEST,
                message = "Missing plugin.json",
            )
        }

        val manifest =
            runCatching {
                parseManifest(manifestPath)
            }.getOrElse { error ->
                return DiscoveredPlugin(
                    directory = directory,
                    manifest = null,
                    status = PluginCompatibilityStatus.INVALID_MANIFEST,
                    message = "Invalid plugin.json: ${error.message ?: "Unknown error"}",
                )
            }

        return if (manifest.apiVersion == hostApiVersion) {
            DiscoveredPlugin(
                directory = directory,
                manifest = manifest,
                status = PluginCompatibilityStatus.COMPATIBLE,
                message = "Compatible with host API $hostApiVersion",
            )
        } else {
            DiscoveredPlugin(
                directory = directory,
                manifest = manifest,
                status = PluginCompatibilityStatus.INCOMPATIBLE,
                message =
                    "Plugin requires API version ${manifest.apiVersion}, " +
                        "but host implements version $hostApiVersion",
            )
        }
    }

    private fun parseManifest(manifestPath: Path): PluginManifest {
        val rawManifest = json.decodeFromString<PluginManifestDto>(manifestPath.readText())

        require(rawManifest.id.isNotBlank()) { "Field 'id' must not be blank." }
        require(rawManifest.version.isNotBlank()) { "Field 'version' must not be blank." }
        require(rawManifest.capabilities.isNotEmpty()) { "Field 'capabilities' must not be empty." }
        require(rawManifest.entrypoint.type == "jvm") { "Only entrypoint.type='jvm' is supported in v1." }
        require(rawManifest.entrypoint.mainClass.isNotBlank()) { "Field 'entrypoint.mainClass' must not be blank." }
        require(rawManifest.entrypoint.classpath.isNotEmpty()) { "Field 'entrypoint.classpath' must not be empty." }

        val apiVersion =
            rawManifest.apiVersion.toIntOrNull()
                ?: error("Field 'apiVersion' must be an integer string.")

        val normalizedClasspath =
            rawManifest.entrypoint.classpath.map { classpathEntry ->
                require(classpathEntry.isNotBlank()) { "Classpath entries must not be blank." }
                classpathEntry
            }

        return PluginManifest(
            id = rawManifest.id,
            version = rawManifest.version,
            apiVersion = apiVersion,
            capabilities = rawManifest.capabilities,
            entrypoint =
                PluginEntrypoint(
                    type = rawManifest.entrypoint.type,
                    mainClass = rawManifest.entrypoint.mainClass,
                    classpath = normalizedClasspath,
                ),
            name = rawManifest.name,
            description = rawManifest.description,
            author = rawManifest.author,
            homepage = rawManifest.homepage,
            license = rawManifest.license,
        )
    }
}

object PluginDirectories {
    fun defaultPluginsDirectory(): Path {
        val osName = System.getProperty("os.name").lowercase()
        val userHome = Path.of(System.getProperty("user.home"))

        return when {
            osName.contains("win") -> {
                val appData = System.getenv("APPDATA") ?: userHome.resolve("AppData/Roaming").toString()
                Path.of(appData).resolve("QuickFile").resolve("plugins")
            }

            osName.contains("mac") -> {
                userHome.resolve("Library/Application Support/QuickFile/plugins")
            }

            else -> {
                val xdgDataHome = System.getenv("XDG_DATA_HOME")
                val base =
                    if (xdgDataHome.isNullOrBlank()) {
                        userHome.resolve(".local/share")
                    } else {
                        Path.of(xdgDataHome)
                    }
                base.resolve("quickfile/plugins")
            }
        }
    }
}

@Serializable
private data class PluginManifestDto(
    val id: String,
    val version: String,
    val name: String? = null,
    val description: String? = null,
    val author: String? = null,
    val homepage: String? = null,
    val license: String? = null,
    @SerialName("apiVersion")
    val apiVersion: String,
    val capabilities: List<String>,
    val entrypoint: PluginEntrypointDto,
)

@Serializable
private data class PluginEntrypointDto(
    val type: String,
    val mainClass: String,
    val classpath: List<String>,
)
