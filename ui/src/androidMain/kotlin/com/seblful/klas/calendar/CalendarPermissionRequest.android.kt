package com.seblful.klas.calendar

import androidx.compose.runtime.Composable
import com.seblful.klas.PermissionPrompt
import com.seblful.klas.rememberPermissionPrompt

@Composable
actual fun rememberCalendarPermissionRequest(onAnswered: () -> Unit): PermissionPrompt =
    rememberPermissionPrompt(AndroidCalendarPermission.REQUIRED) { onAnswered() }
