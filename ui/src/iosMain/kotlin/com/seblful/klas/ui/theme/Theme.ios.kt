package com.seblful.klas.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import platform.UIKit.UIAccessibilityDarkerSystemColorsEnabled
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle

actual val dynamicColorAvailable: Boolean get() = false

/** Increase Contrast, asked again each time the screen resumes: it is switched in Settings. */
@Composable
actual fun systemContrast(): ContrastLevel {
    var increased by remember { mutableStateOf(UIAccessibilityDarkerSystemColorsEnabled()) }
    LifecycleResumeEffect(Unit) {
        increased = UIAccessibilityDarkerSystemColorsEnabled()
        onPauseOrDispose {}
    }
    return if (increased) ContrastLevel.High else ContrastLevel.Standard
}

@Composable
internal actual fun dynamicColorScheme(darkTheme: Boolean, contrast: ContrastLevel): ColorScheme? = null

/**
 * The window takes the app's light or dark answer, so the status bar's icons — and the pickers and share
 * sheets UIKit draws over the app — follow the tutor's theme rather than the phone's.
 */
@Composable
internal actual fun SystemBarIcons(darkTheme: Boolean) {
    SideEffect {
        @Suppress("DEPRECATION")
        UIApplication.sharedApplication.keyWindow?.overrideUserInterfaceStyle = if (darkTheme) {
            UIUserInterfaceStyle.UIUserInterfaceStyleDark
        } else {
            UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
    }
}
