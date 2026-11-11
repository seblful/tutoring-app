package com.seblful.klas.calendar

import androidx.compose.runtime.Composable
import com.seblful.klas.PermissionPrompt

/** The platform's calendar prompt; [onAnswered] is called once the tutor has answered it. */
@Composable
expect fun rememberCalendarPermissionRequest(onAnswered: () -> Unit): PermissionPrompt
