package com.seblful.klas.calendar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** hows the platform's calendar prompt when the returned function is called, then calls [onAnswered]. */
@Composable
fun rememberCalendarPermissionRequest(onAnswered: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onAnswered()
    }
    return remember(launcher) { { launcher.launch(AndroidCalendarPermission.REQUIRED.toTypedArray()) } }
}
