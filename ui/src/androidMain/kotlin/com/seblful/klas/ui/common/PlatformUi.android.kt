package com.seblful.klas.ui.common

import android.content.ClipData
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
}

internal actual val recentsPreviewHasOwnSwitch: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

internal actual val clipboardConfirmsCopy: Boolean get() = confirmsItself(Build.VERSION.SDK_INT)

/**
 * Android 13+ confirms a copy with its own preview of the clip; before that nothing says it landed, so
 * the screen does.
 */
fun confirmsItself(sdk: Int): Boolean = sdk >= Build.VERSION_CODES.TIRAMISU

internal actual suspend fun Clipboard.copyPlainText(label: String, text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
}

@Composable
internal actual fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

@Composable
@ReadOnlyComposable
internal actual fun phoneHour12Reader(): () -> Boolean {
    val context = LocalContext.current.applicationContext
    return { !DateFormat.is24HourFormat(context) }
}

@Composable
internal actual fun rememberLeaveApp(): () -> Unit {
    // Null outside an activity (a preview); there is nothing to leave there.
    val activity = LocalActivity.current
    return remember(activity) { { activity?.moveTaskToBack(true) } }
}
