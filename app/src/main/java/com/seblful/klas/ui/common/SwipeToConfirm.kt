package com.seblful.klas.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A row that runs [onConfirm] when it is dragged past the mark **and let go there** — ticking at the mark,
 * and taking the action back if the finger slides home again.
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
        // Always composed behind the row, so cleared: a screen reader read «Проведено» before every lesson
        // still to come. What the gesture does is offered as the row's own action instead — [swipeAction].
        Box(Modifier.matchParentSize().clearAndSetSemantics { }) { reveal(armed) }
        // Offset in the layout phase, not by recomposing: the row is moved, not rebuilt, sixty times a
        // second.
        Box(Modifier.offset { IntOffset(dragged.roundToInt(), 0) }) { content() }
    }
}

/**
 * What a [SwipeToConfirmBox] promises, drawn behind its row: a filled panel with the act's glyph and its
 * word — «Проведено» behind a lesson, «Сделано» or «Вернуть» behind homework.
 *
 * @param horizontalPadding where the glyph starts — the gutter of whatever the row sits in, which is not
 * one value: a sheet's is wider than a page's.
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
) {
    Row(
        Modifier.fillMaxSize().background(container, shape).padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.armedPop(armed))
        Spacer(Modifier.width(Spacing.inline))
        Text(label, style = MaterialTheme.typography.labelLarge, color = content)
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
