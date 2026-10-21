package com.seblful.klas

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat

/** A platform permission prompt as a screen holds it. */
class PermissionPrompt(
    /** Shows the platform's prompt. */
    val launch: () -> Unit,
    /**
     * Whether the tutor refused this prompt once and Android advises saying why before showing it again.
     * False before the first ask, after a grant, and after a refusal for good — when [launch] answers at
     * once, unseen. Read it at the tap: the last answer changes it.
     */
    val rationaleDue: () -> Boolean,
)

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
