package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A second tap on the tab already shown: its page goes back to the top, as tabs do on Android. Whatever is on
 * show claims it ([ScrollsToTopOnReselect]) — the tab's lists and the bar above them — and every claim runs.
 * A screen pushed over the tab is closed by the first tap instead (`KlasNavigator.navigateToTab`).
 */
class TabReselect {
    private val handlers = mutableListOf<suspend () -> Unit>()

    /** Sends everything on show back to its top. */
    suspend fun reselected() = coroutineScope {
        handlers.toList().forEach { launch { it() } }
    }

    internal fun claim(handler: suspend () -> Unit) {
        handlers += handler
    }

    internal fun release(handler: suspend () -> Unit) {
        handlers -= handler
    }
}

/** The app's [TabReselect]; null under previews and tests that draw no tabs. */
val LocalTabReselect = staticCompositionLocalOf<TabReselect?> { null }

/** Runs [toTop] whenever the tab this is shown under is tapped again. */
@Composable
fun ScrollsToTopOnReselect(toTop: suspend () -> Unit) {
    val reselect = LocalTabReselect.current ?: return
    val latest by rememberUpdatedState(toTop)
    DisposableEffect(reselect) {
        val handler: suspend () -> Unit = { latest() }
        reselect.claim(handler)
        onDispose { reselect.release(handler) }
    }
}
