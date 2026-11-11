package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * The add button a tab hands to the navigation rail, which carries it in its header as Material places a
 * screen's FAB once the window is wide enough for a rail.
 *
 * Screens offer their button and withdraw it when they leave; the latest offer is the one shown, so during
 * a fade between two tabs the arriving tab's button is already the rail's.
 */
@Stable
class RailAction {
    private val offers = mutableStateListOf<@Composable () -> Unit>()

    /** The button the rail draws now, if any screen on show has one. */
    val content: (@Composable () -> Unit)? get() = offers.lastOrNull()

    /** Puts [button] on top, offered for the first time or by a screen shown again. */
    internal fun offer(button: @Composable () -> Unit) {
        offers -= button
        offers += button
    }

    internal fun withdraw(button: @Composable () -> Unit) {
        offers -= button
    }
}

/** The rail's [RailAction] while one is showing beside a tab; `null` where a screen draws its own button. */
val LocalRailAction = compositionLocalOf<RailAction?> { null }

/**
 * Hands [button] to [rail] for as long as this is composed, always drawing the latest [button], and offers it
 * again whenever the screen resumes: a tab come back to before the one it replaced has left keeps the rail.
 */
@Composable
internal fun OfferToRail(rail: RailAction, button: @Composable () -> Unit) {
    val latest by rememberUpdatedState(button)
    val offered: @Composable () -> Unit = remember(rail) { { latest() } }
    DisposableEffect(rail) {
        rail.offer(offered)
        onDispose { rail.withdraw(offered) }
    }
    LifecycleResumeEffect(rail) {
        rail.offer(offered)
        onPauseOrDispose { }
    }
}
