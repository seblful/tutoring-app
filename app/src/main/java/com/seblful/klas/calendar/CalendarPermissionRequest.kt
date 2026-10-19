package com.seblful.klas.calendar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Shows the platform's calendar prompt when the returned function is called, then calls [onAnswered].
 *
 * The half of [CalendarPermission] that needs a screen. [onAnswered] carries no grants on purpose: what the
 * dialog returned is Android's shape (a map of permission names, empty for a request it never showed), and
 * the question after it is the same one asked everywhere else — [CalendarPermission.isGranted].
 */
@Composable
fun rememberCalendarPermissionRequest(onAnswered: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onAnswered()
    }
    return remember(launcher) { { launcher.launch(AndroidCalendarPermission.REQUIRED.toTypedArray()) } }
}
