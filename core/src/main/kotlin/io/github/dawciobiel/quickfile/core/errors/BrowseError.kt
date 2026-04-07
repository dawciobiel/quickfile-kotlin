package io.github.dawciobiel.quickfile.core.errors

sealed interface BrowseError {
    data class PermissionDenied(
        val location: String,
    ) : BrowseError

    data class LocationNotFound(
        val location: String,
    ) : BrowseError

    data class PluginUnavailable(
        val pluginId: String,
    ) : BrowseError

    data class PluginCrashed(
        val pluginId: String,
        val cause: String,
    ) : BrowseError

    data class Unknown(
        val message: String,
    ) : BrowseError
}
