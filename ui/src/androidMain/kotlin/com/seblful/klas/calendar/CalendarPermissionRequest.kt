package com.seblful.klas.calendar

import androidx.compose.runtime.Composable
import com.seblful.klas.PermissionPrompt
import com.seblful.klas.rememberPermissionPrompt

/** The platform's calendar prompt; [onAnswered] is called once the tutor has answered it. */
@Composable
fun rememberCalendarPermissionRequest(onAnswered: () -> Unit): PermissionPrompt =
    rememberPermissionPrompt(AndroidCalendarPermission.REQUIRED) { onAnswered() }
