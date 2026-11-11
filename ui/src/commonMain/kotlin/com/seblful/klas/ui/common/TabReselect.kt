package com.seblful.klas.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A tap on the tab already shown takes its page one step back to where the tab starts: first to the top
 * ([ScrollsToTopOnReselect] — the tab's lists and the bar above them), and once there to its first view
 * ([OpensFirstViewOnReselect] — the schedule's day, People's students). Whatever is on show claims a step, and
 * every claim on the step taken runs. A screen pushed over the tab is closed by the first tap instead
 * (`KlasNavigator.navigateToTab`).
 */
class TabReselect {
    private val claims = mutableListOf<Claim>()

    /** Takes the page on show one step back: to its top, or, already there, to its first view. */
    suspend fun reselected() = coroutineScope {
        val shown = claims.toList()
        val step = Step.entries.firstOrNull { step -> shown.any { it.step == step && !it.atStart() } }
        shown.filter { it.step == step }.forEach { launch { it.toStart() } }
    }

    internal fun claim(claim: Claim) {
        claims += claim
    }

    internal fun release(claim: Claim) {
        claims -= claim
    }

    /** The steps back, in the order taps take them. */
    internal enum class Step { Top, FirstView }

    internal class Claim(val step: Step, val atStart: () -> Boolean, val toStart: suspend () -> Unit)
}

/** The app's [TabReselect]; null under previews and tests that draw no tabs. */
val LocalTabReselect = staticCompositionLocalOf<TabReselect?> { null }

/** Runs [toTop] whenever the tab this is shown under is tapped again while not [atTop]. */
@Composable
fun ScrollsToTopOnReselect(atTop: () -> Boolean, toTop: suspend () -> Unit) {
    ClaimsReselect(TabReselect.Step.Top, atTop, toTop)
}

@Composable
fun ScrollsToTopOnReselect(list: LazyListState) {
    ScrollsToTopOnReselect(atTop = { !list.canScrollBackward }) { list.animateScrollToItem(0) }
}

@Composable
fun ScrollsToTopOnReselect(grid: LazyGridState) {
    ScrollsToTopOnReselect(atTop = { !grid.canScrollBackward }) { grid.animateScrollToItem(0) }
}

@Composable
fun ScrollsToTopOnReselect(scroll: ScrollState) {
    ScrollsToTopOnReselect(atTop = { scroll.value == 0 }) { scroll.animateScrollTo(0) }
}

/** Runs [toFirst] when the tab this is shown under is tapped again with its page already at the top. */
@Composable
fun OpensFirstViewOnReselect(onFirst: Boolean, toFirst: () -> Unit) {
    val first by rememberUpdatedState(onFirst)
    ClaimsReselect(TabReselect.Step.FirstView, atStart = { first }, toStart = toFirst)
}

@Composable
private fun ClaimsReselect(step: TabReselect.Step, atStart: () -> Boolean, toStart: suspend () -> Unit) {
    val reselect = LocalTabReselect.current ?: return
    val latestAtStart by rememberUpdatedState(atStart)
    val latestToStart by rememberUpdatedState(toStart)
    DisposableEffect(reselect, step) {
        val claim = TabReselect.Claim(step, atStart = { latestAtStart() }, toStart = { latestToStart() })
        reselect.claim(claim)
        onDispose { reselect.release(claim) }
    }
}
