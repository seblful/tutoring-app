package com.seblful.klas.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A row that runs [onConfirm] when it is dragged past the mark **and let go there** — ticking at the mark,
 * and taking the action back if the finger slides home again.
 *
 * The gesture is a promise made and then kept, so it is three moments rather than one:
 * - **the mark** — [swipeArmed] becomes true, the phone ticks ([rememberArmedFeedback]), the [reveal] is
 *   told so, and from here letting go will act;
 * - **the way back** — dragging under the mark again disarms it, in silence, and the row is just a row;
 * - **the lift** — the answer at that instant is the one that counts: confirm buzz plus [onConfirm], or
 *   nothing at all.
 *
 * **The mark is felt and seen, off one flag.** A buzz alone is deniable — a thumb rests on the row, the
 * phone may have haptics turned off, and the tutor is left guessing whether the promise was made. So
 * `armed` is handed to the [reveal], which draws it with [armedPop]; both the tick and the pop read the one
 * flag, so no phone setting can leave the gesture saying nothing, and nothing can pop where the release
 * would decline.
 *
 * The row always springs home, so the [reveal] behind it is a promise of what will happen and never the
 * aftermath of it: nothing is being removed from the list, and the caller's own state — a status badge —
 * is what says the thing is done.
 *
 * **Why not `SwipeToDismissBox`.** Material's box decides on release with a *velocity* path as well as a
 * positional one: a fast flick commits from anywhere, having never crossed the mark. Its live
 * `targetValue` is a third thing again — the closest anchor, so half the width. The tick would then fire
 * where the release does not act, and the release would act where nothing ticked, which is precisely the
 * promise this gesture exists to make. Here one function answers both, so they cannot disagree, and a
 * flick that travels nowhere does nothing: marking a lesson taught is what *bills* it, and that is worth a
 * deliberate drag.
 *
 * One direction, towards the end of the line, because the app is drawn in Russian and reads one way.
 */
@Composable
fun SwipeToConfirmBox(
    onConfirm: () -> Unit,
    reveal: @Composable (armed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Read at the lift rather than captured once: `pointerInput(Unit)` keeps its first lambda, and a caller
    // whose action depends on the list the row is in hands over a new one when that changes.
    val confirm by rememberUpdatedState(onConfirm)
    val armedFeedback = rememberArmedFeedback()
    val confirmFeedback = rememberConfirmFeedback()
    val scope = rememberCoroutineScope()
    // Written straight from the drag callback, not through a coroutine. An `Animatable` here read stale:
    // its `snapTo` is a suspend call, so a slide home and an immediate lift left the release deciding
    // against an offset the finger had already left — which confirmed a gesture that had been taken back.
    var dragged by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    var goingHome by remember { mutableStateOf<Job?>(null) }

    // **Derived, not computed in the body.** The offset changes every frame of a drag, so reading it here
    // directly would recompose the row sixty times a second to answer the same yes or no; a `derivedStateOf`
    // notifies only when the *answer* flips, which is twice per gesture at most.
    val armed by remember { derivedStateOf { swipeArmed(dragged, width) } }

    // One flag, two ways of saying it: the pop is drawn from `armed` below, the tick fires on its edge.
    // `snapshotFlow` emits on change, which is what makes the tick one per crossing rather than one per
    // frame — and silence on the way back, where nothing has happened to announce.
    LaunchedEffect(Unit) {
        snapshotFlow { armed }.collect { if (it) armedFeedback() }
    }

    fun springHome() {
        goingHome?.cancel()
        goingHome = scope.launch {
            animate(dragged, 0f, animationSpec = SpringHome) { value, _ -> dragged = value }
        }
    }

    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat() }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    // Taking hold during the spring home stops it there, so a second drag continues from
                    // where the row is rather than fighting an animation for the offset.
                    onDragStart = { goingHome?.cancel() },
                    onHorizontalDrag = { _, delta ->
                        dragged = (dragged + delta).coerceIn(0f, width)
                    },
                    onDragEnd = {
                        if (swipeArmed(dragged, width)) {
                            confirmFeedback()
                            confirm()
                        }
                        springHome()
                    },
                    // A gesture the app never saw the end of is not a lift: the system took the pointer
                    // (a shade pulled down, a call arriving), and the tutor let go of nothing.
                    onDragCancel = { springHome() },
                )
            },
        propagateMinConstraints = true,
    ) {
        Box(Modifier.matchParentSize()) { reveal(armed) }
        // Offset in the layout phase, not by recomposing: the row is moved, not rebuilt, sixty times a
        // second.
        Box(Modifier.offset { IntOffset(dragged.roundToInt(), 0) }) { content() }
    }
}

/**
 * Whether letting go now would confirm: the row has been dragged at least [CommitFraction] of its own
 * width.
 *
 * **The only copy of the rule.** The tick at the mark and the action on release both read it, so the phone
 * cannot promise something the release then declines. An unmeasured row (width 0, before layout) arms
 * nothing — every offset is past a mark at zero, and a card confirming itself into existence would be a
 * lesson billed by scrolling.
 *
 * Pure and un-composable on purpose, so `SwipeToConfirmTest` can ask it on the JVM: a rule that lives
 * inside a `@Composable` is a rule only a device can check.
 */
internal fun swipeArmed(dragged: Float, width: Float): Boolean =
    width > 0f && dragged >= width * CommitFraction

/**
 * How far the row travels before the mark: **a third of its own width**.
 *
 * Material's half is a long drag for something done after every lesson, and the mark has to be crossable
 * with a thumb on a 360dp phone without the hand leaving the row.
 */
private const val CommitFraction = 0.33f

/**
 * The spring the row rides home on — the same one whether it confirmed or was taken back.
 *
 * **Not bouncy**, which the default damping gives us: a row that overshot on the way home would cross the
 * mark a second time and tick at nobody, the finger having already left.
 */
private val SpringHome = spring<Float>(stiffness = Spring.StiffnessMediumLow)
