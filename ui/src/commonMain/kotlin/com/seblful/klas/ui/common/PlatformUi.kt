package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.Clipboard

/*
 * What a shared screen asks of the platform it runs on, each in the fewest words the screens need. The
 * answers are Android's (`PlatformUi.android.kt`) and iOS's (`PlatformUi.ios.kt`).
 */

/** This build's version as the platform installed it — a debug install's `.debug` suffix included. */
@Composable
internal expect fun rememberAppVersion(): String

/**
 * Whether the app switcher can hide the app's preview on its own. Without that (Android 12 and below) the
 * only way to hide it also blocks screenshots, and the setting says so.
 */
internal expect val recentsPreviewHasOwnSwitch: Boolean

/** Whether the platform confirms a copy itself (Android 13+ previews the clip); otherwise the screen does. */
internal expect val clipboardConfirmsCopy: Boolean

/** Puts [text] on the clipboard as plain text, [label] naming it where the platform shows one. */
internal expect suspend fun Clipboard.copyPlainText(label: String, text: String)

/** Whether the phone runs animations: with them off (or motion reduced) a skeleton holds one tone. */
@Composable
internal expect fun rememberAnimationsEnabled(): Boolean

/** Reads, each time it is called, whether the phone writes times on a 12-hour clock. */
@Composable
@ReadOnlyComposable
internal expect fun phoneHour12Reader(): () -> Boolean

/** Leaves the app for the home screen, where the platform has a way to: what Back does on the lock screen. */
@Composable
internal expect fun rememberLeaveApp(): () -> Unit
