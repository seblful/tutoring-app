package com.seblful.klas.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.seblful.klas.PermissionPrompt
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberNotificationPermissionRequest(onAnswered: (granted: Boolean) -> Unit): PermissionPrompt {
    val answer by rememberUpdatedState(onAnswered)
    return remember {
        PermissionPrompt(
            launch = {
                UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                    UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
                ) { granted, _ -> dispatch_async(dispatch_get_main_queue()) { answer(granted) } }
            },
            // iOS asks once; after a refusal only the Settings app can change the answer.
            rationaleDue = { false },
        )
    }
}
