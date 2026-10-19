package com.seblful.klas.notifications

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Shows the platform's notification prompt when the returned function is called, then calls [onAnswered]
 * with whether the tutor refused nothing it asked.
 *
 * The half of [NotificationPermission] that needs a screen; call it only when
 * [NotificationPermission.canAsk] said there is a prompt to show. The answer is a yes or no rather than
 * Android's map of permission names, so the screen reads the same on a platform with one prompt and no
 * names. A request the system dismissed unseen refuses nothing, and is not a no.
 */
@Composable
fun rememberNotificationPermissionRequest(onAnswered: (granted: Boolean) -> Unit = {}): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        onAnswered(grants.values.all { it })
    }
    return remember(launcher) { { launcher.launch(AndroidNotificationPermission.REQUIRED.toTypedArray()) } }
}
