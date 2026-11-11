package com.seblful.klas.ui.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import platform.UIKit.UIAccessibilityIsVoiceOverRunning

/** VoiceOver, asked again each time the screen resumes: it is switched on and off in Settings. */
@Composable
actual fun rememberTouchExploration(): Boolean {
    var running by remember { mutableStateOf(UIAccessibilityIsVoiceOverRunning()) }
    LifecycleResumeEffect(Unit) {
        running = UIAccessibilityIsVoiceOverRunning()
        onPauseOrDispose {}
    }
    return running
}
