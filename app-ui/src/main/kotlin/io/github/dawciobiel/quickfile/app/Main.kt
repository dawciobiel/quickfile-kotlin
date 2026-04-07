package io.github.dawciobiel.quickfile.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import io.github.dawciobiel.quickfile.app.component.DefaultAppComponent
import javax.swing.SwingUtilities

fun main() {
    val lifecycle = LifecycleRegistry()
    val root =
        runOnUiThread {
            DefaultAppComponent(
                componentContext = DefaultComponentContext(lifecycle = lifecycle),
            )
        }

    application {
        val windowState = rememberWindowState()
        LifecycleController(lifecycleRegistry = lifecycle, windowState = windowState)

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "QuickFile",
        ) {
            MaterialTheme {
                AppContent(component = root)
            }
        }
    }
}

private fun <T> runOnUiThread(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) {
        return block()
    }

    var result: Result<T>? = null
    SwingUtilities.invokeAndWait {
        result = runCatching(block)
    }

    return checkNotNull(result).getOrThrow()
}
