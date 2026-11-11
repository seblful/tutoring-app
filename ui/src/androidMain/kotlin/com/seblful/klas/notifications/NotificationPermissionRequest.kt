package com.seblful.klas.notifications

import androidx.compose.runtime.Composable
import com.seblful.klas.PermissionPrompt
import com.seblful.klas.rememberPermissionPrompt

/**
 * The platform's notification prompt; [onAnswered] is called with whether the tutor refused nothing it
 * asked.
 *
 * The half of [NotificationPermission] that needs a screen; call it only when
 * [NotificationPermission.canAsk] said there is a prompt to show. The answer is a yes or no rather than
 * Android's map of permission names, so the screen reads the same on a platform with one prompt and no
 * names. A request the system dismissed unseen refuses nothing, and is not a no.
 */
@Composable
fun rememberNotificationPermissionRequest(onAnswered: (granted: Boolean) -> Unit = {}): PermissionPrompt =
    rememberPermissionPrompt(AndroidNotificationPermission.REQUIRED) { grants -> onAnswered(grants.values.all { it }) }
