package com.seblful.klas.notifications

import androidx.compose.runtime.Composable
import com.seblful.klas.PermissionPrompt
import com.seblful.klas.rememberPermissionPrompt

@Composable
actual fun rememberNotificationPermissionRequest(onAnswered: (granted: Boolean) -> Unit): PermissionPrompt =
    rememberPermissionPrompt(AndroidNotificationPermission.REQUIRED) { grants -> onAnswered(grants.values.all { it }) }
