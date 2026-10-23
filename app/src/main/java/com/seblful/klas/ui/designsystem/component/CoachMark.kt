package com.seblful.klas.ui.designsystem.component

import android.view.accessibility.AccessibilityManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachMark(
    text: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberTooltipState(isPersistent = true)
    val dismissed by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        delay(CoachMarkDefaults.ShowAfter)
        // Suspends while the bubble is up; cancelled — the content gone, or no longer to be shown — it closes.
        state.show()
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = {
            RichTooltip(
                action = {
                    KlasButton(
                        onClick = {
                            scope.launch { state.dismiss() }
                            dismissed()
                        },
                        kind = ButtonKind.Text,
                    ) { Text(stringResource(R.string.tip_got_it)) }
                },
                caretShape = TooltipDefaults.caretShape(),
            ) { Text(text) }
        },
        state = state,
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

object CoachMarkDefaults {
    /** How long a screen stays still before its tip opens — long enough to have been looked at first. */
    val ShowAfter = 700.milliseconds
}

/**
 * Whether a screen reader is exploring by touch, which cannot swipe: a tip about a swipe names the row's
 * action instead ([com.seblful.klas.ui.common.swipeAction]).
 */
@Composable
fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var exploring by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { exploring = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return exploring
}
