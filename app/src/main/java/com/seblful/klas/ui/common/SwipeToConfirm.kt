package com.seblful.klas.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A row that runs [onConfirm] when it is dragged past the mark **and let go there** — ticking at the mark,
 * and taking the action back if the finger slides home again.
 *
 * @param toward the one way the row slides. A drag the other way is left to whatever holds the row — a
 * [SegmentedPage] turning — so a list of swipeable rows does not lock the page it is on.
 */
@Composable
fun SwipeToConfirmBox(
    onConfirm: () -> Unit,
    reveal: @Composable (armed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    toward: SwipeToward = SwipeToward.End,
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
    val armed by remember { derivedStateOf { swipeArmed(abs(dragged), width) } }
    // The panel exists only while the row is off its rest. A nested row's background stops 8 dp short of
    // each side (`insetRowPress` clips it), so a panel always drawn behind it showed through as a green bar
    // down both edges of every student's and group's homework.
    val moved by remember { derivedStateOf { dragged != 0f } }

    fun springHome() {
        goingHome?.cancel()
        goingHome = scope.launch {
            animate(dragged, 0f, animationSpec = SpringHome) { value, _ -> dragged = value }
        }
    }

    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat() }
            .pointerInput(toward) {
                // One flag, two ways of saying it: the pop is drawn from `armed`, the tick fires here as a drag
                // crosses the mark — once per crossing, and silent on the way back, where nothing has happened
                // to announce. From the drag itself, not a watcher on `armed`: every row on screen kept one.
                fun slide(delta: Float) {
                    val wasArmed = swipeArmed(abs(dragged), width)
                    dragged = toward.clamp(dragged + delta, width)
                    if (!wasArmed && swipeArmed(abs(dragged), width)) armedFeedback()
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Claimed only past the slop and only this way: unconsumed, the other way reaches the
                    // page's own detector, and once that consumes it this one gives the gesture up.
                    val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        if (toward.claims(overSlop)) {
                            change.consume()
                            slide(overSlop)
                        }
                    } ?: return@awaitEachGesture
                    // Taking hold during the spring home stops it there, so a second drag continues from
                    // where the row is rather than fighting an animation for the offset.
                    goingHome?.cancel()
                    val lifted = horizontalDrag(start.id) { change ->
                        slide(change.positionChange().x)
                        change.consume()
                    }
                    // A gesture the app never saw the end of is not a lift: the system took the pointer
                    // (a shade pulled down, a call arriving), and the tutor let go of nothing.
                    if (lifted && swipeArmed(abs(dragged), width)) {
                        confirmFeedback()
                        confirm()
                    }
                    springHome()
                }
            },
        propagateMinConstraints = true,
    ) {
        // Cleared: it sits behind a row a screen reader is reading, and would read «Проведено» before a lesson
        // still to come. What the gesture does is offered as the row's own action instead — [swipeAction].
        if (moved) Box(Modifier.matchParentSize().clearAndSetSemantics { }) { reveal(armed) }
        // Offset in the layout phase, not by recomposing: the row is moved, not rebuilt, sixty times a
        // second.
        Box(Modifier.offset { IntOffset(dragged.roundToInt(), 0) }) { content() }
    }
}

/** Which way a [SwipeToConfirmBox] slides: [End] is left to right. */
enum class SwipeToward {
    End,
    Start,
    ;

    internal fun claims(delta: Float): Boolean = if (this == End) delta > 0f else delta < 0f

    internal fun clamp(offset: Float, width: Float): Float =
        if (this == End) offset.coerceIn(0f, width) else offset.coerceIn(-width, 0f)
}

/**
 * What a [SwipeToConfirmBox] promises, drawn behind its row: a filled panel with the act's glyph and its
 * word — «Проведено» behind a lesson, «Сделано» or «Вернуть» behind homework.
 *
 * @param horizontalPadding where the glyph starts — the gutter of whatever the row sits in, which is not
 * one value: a sheet's is wider than a page's.
 * @param toward which way the row slides; the glyph sits on the edge it uncovers.
 */
@Composable
fun SwipeReveal(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    armed: Boolean,
    shape: Shape,
    horizontalPadding: Dp,
    toward: SwipeToward = SwipeToward.End,
) {
    Row(
        Modifier.fillMaxSize().background(container, shape).padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(
            Spacing.inline,
            if (toward == SwipeToward.End) Alignment.Start else Alignment.End,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val glyph = @Composable {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.armedPop(armed))
        }
        // The glyph at the edge either way, the word inside it.
        if (toward == SwipeToward.End) glyph()
        Text(label, style = MaterialTheme.typography.labelLarge, color = content)
        if (toward == SwipeToward.Start) glyph()
    }
}

/**
 * The swipe, as an action a screen reader can reach: put it on the row's clickable content, so the same
 * node that is read and tapped also offers [label]. A drag is the one gesture TalkBack cannot make, and
 * without this a lesson could not be marked taught — nor homework done — by anyone using it.
 */
fun Modifier.swipeAction(label: String, onAction: () -> Unit): Modifier =
    semantics { customActions = listOf(CustomAccessibilityAction(label) { onAction(); true }) }

internal fun swipeArmed(dragged: Float, width: Float): Boolean =
    width > 0f && dragged >= width * CommitFraction

private const val CommitFraction = 0.33f

private val SpringHome = spring<Float>(stiffness = Spring.StiffnessMediumLow)
