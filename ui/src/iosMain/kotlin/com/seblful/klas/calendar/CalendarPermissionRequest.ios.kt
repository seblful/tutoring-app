package com.seblful.klas.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.seblful.klas.PermissionPrompt
import platform.EventKit.EKEventStore
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberCalendarPermissionRequest(onAnswered: () -> Unit): PermissionPrompt {
    val answer by rememberUpdatedState(onAnswered)
    return remember {
        PermissionPrompt(
            launch = {
                EKEventStore().requestFullAccessToEventsWithCompletion { _, _ ->
                    dispatch_async(dispatch_get_main_queue()) { answer() }
                }
            },
            // iOS asks once; after a refusal only the Settings app can change the answer.
            rationaleDue = { false },
        )
    }
}
