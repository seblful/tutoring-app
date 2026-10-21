package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** The buzz that says a gesture landed — **through the phone's haptics, never a raw [android.os.Vibrator]**. */
@Composable
fun rememberConfirmFeedback(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.Confirm) } }
}

/** The tick that says a drag has reached the mark — **and that letting go now is what does the thing**. */
@Composable
fun rememberArmedFeedback(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) {
        { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    }
}

/** The buzz that says **no** — the app refused what was just asked for. */
@Composable
fun rememberRejectFeedback(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.Reject) } }
}

/** The click of a switch or a tick box, in whichever direction it went. */
@Composable
fun rememberToggleFeedback(): (Boolean) -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) {
        { on ->
            haptics.performHapticFeedback(
                if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
            )
        }
    }
}

/**
 * The tick of moving between the segments of a fixed choice — the schedule's view mode, a lesson's status,
 * «Люди»'s two sides, the settings rows.
 */
@Composable
fun rememberSegmentTickFeedback(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) } }
}

/** The fine tick of a slide passing each step of a scale — the booking timeline's five minutes. */
@Composable
fun rememberStepTickFeedback(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) } }
}
