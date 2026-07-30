package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The buzz that says a gesture landed — **through the phone's haptics, never a raw [android.os.Vibrator]**.
 *
 * `LocalHapticFeedback` ends at `View.performHapticFeedback`, which honours the system's touch-feedback
 * setting and the device's own haptic strength: a tutor who has turned vibration off gets silence, and one
 * on a phone with a good actuator gets that phone's confirm effect rather than a duration this app
 * invented. A `Vibrator` call would answer a question the phone has already answered — the same mistake
 * as pinning a clock to 24 hours (`ui/common/timeText`). `HapticFeedbackTest` keeps the `Vibrator` out.
 *
 * Returned as a lambda rather than performed here because the caller fires it from a gesture callback,
 * which is not composition. Material3 1.4 performs no haptics of its own anywhere, so nothing doubles up.
 */
@Composable
fun rememberConfirmFeedback(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.Confirm) } }
}

/**
 * The tick that says a drag has reached the mark — **and that letting go now is what does the thing**.
 *
 * `GestureThresholdActivate` is the platform's own name for exactly this gesture: "the user is executing a
 * swipe/drag-style gesture, such as pull-to-refresh, where the gesture action is eligible at a certain
 * threshold of movement, and can be cancelled by moving back past the threshold". So the effect carries
 * the meaning rather than a duration this app picked — and `androidx.core` degrades it to a context click
 * on a phone older than API 34, so an older phone still ticks instead of going quiet.
 *
 * Separate from [rememberConfirmFeedback] because the two moments are different things: this one says
 * «now», the other says «done», and a gesture that offers to be taken back needs to say both. Sliding
 * back *under* the mark is answered with silence — nothing happened, and a buzz for an undo would read as
 * one more thing done.
 */
@Composable
fun rememberArmedFeedback(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) {
        { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    }
}

/**
 * The buzz that says **no** — the app refused what was just asked for.
 *
 * The counterpart of [rememberConfirmFeedback], and the moment the app had nothing to say about at all: a
 * booking refused because the hour is already taken, a save refused for the same reason, a subject that
 * cannot be unenrolled while lessons exist for it. Each of those showed a snackbar and nothing else, so the
 * one kind of answer worth feeling without reading — *that did not happen* — was the one the phone stayed
 * silent for, while a lesson marked taught buzzed twice.
 *
 * `Reject` is the platform's own name for it, so a phone with a good actuator gives its refusal pattern
 * rather than a duration this app invented. On a device older than API 30 `androidx` has nothing to map it
 * to and it is silent, which is the same bargain every effect here makes: the phone decides, and the
 * snackbar carries the message either way.
 *
 * **Not for a field that is merely incomplete.** A greyed-out «Сохранить» over an empty name is not a
 * refusal, it is a form not finished yet — buzzing at somebody midway through typing would be the app
 * telling them off for progress. This fires where an action was taken and declined.
 */
@Composable
fun rememberRejectFeedback(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.Reject) } }
}

/**
 * The click of a switch or a tick box, in whichever direction it went.
 *
 * Takes the **new** state rather than being two functions, because `ToggleOn` and `ToggleOff` are one
 * decision with a direction and a caller that had to pick between them could pick wrong — a switch that
 * buzzes "on" while turning off is worse than no haptic at all. The platform's two effects differ where the
 * hardware can express it and are identical where it cannot.
 *
 * Used by the settings switches and by the roster and attendance ticks. Deliberately **not** by every
 * checkbox-shaped thing in the app: what earns it is a stored fact changing, not a control being touched.
 */
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
 *
 * `SegmentTick` is the platform's effect for a control with detents, which is exactly what a segmented row
 * is: the finger lands on one of a few positions and the phone marks the arrival. It is deliberately lighter
 * than [rememberConfirmFeedback] — picking a segment is navigation between views of the same thing, not a
 * write — and it is the reason a segmented control gets a haptic while an ordinary button does not: a button
 * has its ripple and its own visible result, whereas a segment's whole feedback is a pill moving under a
 * thumb that is covering it.
 *
 * Fires only where the selection actually **moves**; re-tapping the segment already chosen changes nothing
 * and says nothing (`SegmentedChoice` already refuses to re-select).
 */
@Composable
fun rememberSegmentTickFeedback(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) } }
}
