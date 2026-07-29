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
