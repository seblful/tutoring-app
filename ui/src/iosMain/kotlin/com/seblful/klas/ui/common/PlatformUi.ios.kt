package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import platform.Foundation.NSBundle
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
internal actual fun rememberAppVersion(): String = remember {
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""
}

internal actual val recentsPreviewHasOwnSwitch: Boolean get() = true

internal actual val clipboardConfirmsCopy: Boolean get() = false

@OptIn(ExperimentalComposeUiApi::class)
internal actual suspend fun Clipboard.copyPlainText(label: String, text: String) {
    setClipEntry(ClipEntry.withPlainText(text))
}

@Composable
internal actual fun rememberAnimationsEnabled(): Boolean = remember { !UIAccessibilityIsReduceMotionEnabled() }

@Composable
@ReadOnlyComposable
internal actual fun phoneHour12Reader(): () -> Boolean = ::phoneHour12

/** The phone's own hour pattern («h a» or «HH») for its locale and its 12/24-hour switch. */
internal fun phoneHour12(): Boolean =
    NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale)?.contains('a') == true

/** An iOS app has no Back of its own to leave by; the lock screen simply stays. */
@Composable
internal actual fun rememberLeaveApp(): () -> Unit = remember { {} }
