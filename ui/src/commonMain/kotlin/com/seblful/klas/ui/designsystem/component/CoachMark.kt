package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import com.seblful.klas.lock.LocalAppLocked
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * [content] with a tip about it in a bubble — Material's rich tooltip, kept until «Got it», a tap elsewhere or
 * Back, each of which is [onDismiss]: the tutor has read it. It opens [CoachMarkDefaults.ShowAfter] after
 * [visible] turns true, on a screen that has settled rather than with the screen, and closes unread when
 * [visible] turns false or the content leaves — to be shown again next time.
 *
 * A long-press on the content stays the content's: a lesson card is picked by one, so the bubble never opens
 * on it the way a held icon's name does ([com.seblful.klas.ui.common.HoldToName]).
 *
 * @param side where the bubble goes. A tip about an item of a list opens [CoachMarkSide.Above] it, so it never
 * covers the items after — the next lesson the tip is about; with no room above, it opens below after all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachMark(
    text: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    side: CoachMarkSide = CoachMarkSide.Below,
    content: @Composable () -> Unit,
) {
    val state = rememberTooltipState(isPersistent = true)
    val hidden = rememberTooltipState()
    val locked = LocalAppLocked.current
    val dismissed by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    LaunchedEffect(visible, locked) {
        if (!visible || locked) return@LaunchedEffect
        delay(CoachMarkDefaults.ShowAfter)
        // Suspends while the bubble is up; cancelled — the content gone, or no longer to be shown — it closes.
        state.show()
    }
    TooltipBox(
        positionProvider = rememberCoachMarkPosition(side),
        tooltip = {
            if (!locked) RichTooltip(
                action = {
                    KlasButton(
                        onClick = {
                            scope.launch { state.dismiss() }
                            dismissed()
                        },
                        kind = ButtonKind.Text,
                    ) { Text(stringResource(Strings.tip_got_it)) }
                },
                caretShape = TooltipDefaults.caretShape(),
            ) { Text(text) }
        },
        state = if (locked) hidden else state,
        modifier = modifier,
        onDismissRequest = {
            scope.launch { state.dismiss() }
            dismissed()
        },
        // So a screen reader moves to the tip, and «Got it», as it opens.
        focusable = true,
        enableUserInput = false,
        hasAction = true,
        content = content,
    )
}

/** Which side of what it is about a [CoachMark] opens on. */
enum class CoachMarkSide { Above, Below }

/**
 * Below, or above while the bubble fits between the status bar and the anchor — decided here rather than left to
 * the provider, so a bubble never opens under the status bar nor, flipped, over the anchor itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberCoachMarkPosition(side: CoachMarkSide): PopupPositionProvider {
    val below = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below)
    val above = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above)
    val top = WindowInsets.safeDrawing.getTop(LocalDensity.current)
    return remember(side, below, above, top) {
        if (side == CoachMarkSide.Below) below else PreferAbove(above, below, top)
    }
}

/** [above] while what it places fits between [top] and the anchor, else [below]. */
class PreferAbove(
    private val above: PopupPositionProvider,
    private val below: PopupPositionProvider,
    private val top: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val over = above.calculatePosition(anchorBounds, windowSize, layoutDirection, popupContentSize)
        val fits = over.y >= top && over.y + popupContentSize.height <= anchorBounds.top
        return if (fits) over else below.calculatePosition(anchorBounds, windowSize, layoutDirection, popupContentSize)
    }
}

object CoachMarkDefaults {
    /** How long a screen stays still before its tip opens — long enough to have been looked at first. */
    val ShowAfter = 700.milliseconds
}

/**
 * Whether a screen reader is exploring by touch, which cannot swipe: a tip about a swipe names the row's
 * action instead ([com.seblful.klas.ui.common.swipeAction]).
 */
@Composable
expect fun rememberTouchExploration(): Boolean
