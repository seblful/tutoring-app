package com.seblful.klas.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.seblful.klas.ui.common.state.UiEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How a screen says something to the tutor in passing. */
class Messages(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    /** Shows [text]. */
    fun show(text: String) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }

    /** Shows [text] with something to do about it — «Показать», «Отменить». */
    fun show(text: String, actionLabel: String, onAction: () -> Unit) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(
                message = text,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }
}

/** The [Messages] for one screen. Pass `messages.hostState` to the screen's `Scaffold`. */
@Composable
fun rememberMessages(): Messages {
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    return remember(hostState, scope) { Messages(hostState, scope) }
}

/**
 * Shows every one-shot event of [events] as a message, in [words].
 *
 * @param refused which events are the app saying **no** to something just asked for — a save refused
 * because the hour is taken. Those are felt as well as read ([rememberRejectFeedback]); a failure is not
 * a refusal, and stays silent.
 *
 * A screen that does more than show a string per event (an undo action, a navigation) keeps its own
 * collector; this is for the eleven that do not.
 */
@Composable
fun <T> ShowMessagesFor(
    events: UiEvents<T>,
    messages: Messages,
    refused: (T) -> Boolean = { false },
    words: (T) -> String,
) {
    val reject = rememberRejectFeedback()
    val currentWords by rememberUpdatedState(words)
    val currentRefused by rememberUpdatedState(refused)
    LaunchedEffect(events, messages) {
        events.stream.collect { event ->
            if (currentRefused(event)) reject()
            messages.show(currentWords(event))
        }
    }
}
