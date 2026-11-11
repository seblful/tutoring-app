package com.seblful.klas

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat

/** A [PermissionPrompt] for [permissions], all asked at once; [onResult] gets Android's answer for each. */
@Composable
fun rememberPermissionPrompt(
    permissions: List<String>,
    onResult: (Map<String, Boolean>) -> Unit,
): PermissionPrompt {
    // Null outside an activity (a preview); nothing has been refused there.
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), onResult)
    return remember(launcher, activity, permissions) {
        PermissionPrompt(
            launch = { launcher.launch(permissions.toTypedArray()) },
            rationaleDue = {
                activity != null &&
                    permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
            },
        )
    }
}
