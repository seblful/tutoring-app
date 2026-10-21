package com.seblful.klas.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationEventState
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Material's in-app predictive back, for back that `NavDisplay` does not animate (a step inside one screen):
 * while the gesture is held the content shrinks toward 90% and drifts away from the edge it started at, so
 * letting go reads as leaving. A cancelled gesture eases back.
 *
 * [state] must be the one given to the screen's `NavigationBackHandler`.
 *
 * The gesture is read in a `snapshotFlow` and drawn in `graphicsLayer`, never in composition: it moves every
 * frame it is held, and a read here would recompose the caller's whole content with it.
 */
@Composable
fun Modifier.predictiveBack(state: NavigationEventState<*>): Modifier {
    val pull = remember { Animatable(0f) }
    LaunchedEffect(state) {
        // A launch per target, as `animateFloatAsState` does: each takes over the last one's velocity.
        snapshotFlow { backPull(state.transitionState) }.collect { target -> launch { pull.animateTo(target) } }
    }
    return this.graphicsLayer {
        val amount = pull.value
        val scale = 1f - MaxScaleDrop * abs(amount)
        scaleX = scale
        scaleY = scale
        translationX = amount * (size.width / ShiftDivisor - ShiftInset.toPx())
    }
}

/**
 * The gesture as one number: how far it has gone, negative when it started at the right edge. Signed, so the
 * ease back after a release keeps drifting the way the swipe went rather than flipping sides.
 */
internal fun backPull(transition: NavigationEventTransitionState): Float {
    val event = (transition as? NavigationEventTransitionState.InProgress)?.latestEvent ?: return 0f
    return if (event.swipeEdge == NavigationEvent.EDGE_RIGHT) -event.progress else event.progress
}

private const val MaxScaleDrop = 0.1f

// Material's in-app shift: a twentieth of the width, less 8 dp.
private const val ShiftDivisor = 20f
private val ShiftInset = 8.dp
