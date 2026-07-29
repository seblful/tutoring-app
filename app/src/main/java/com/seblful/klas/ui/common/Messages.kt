package com.seblful.klas.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * How a screen says something to the tutor in passing.
 *
 * The app used to say it two ways. Undoing a deleted payment, reporting a backup failure and the
 * onboarding errors were **snackbars**; a refused subject removal, a failed save, a blocked class and
 * six others were `android.widget.Toast`. Same class of message, two presentations — and the toast is
 * the worse of the two in every respect that matters here: it ignores the app's theme and its dark
 * mode, it floats over the bottom bar (so it lands on top of the very tabs it is talking about), it
 * cannot carry an action, and since Android 12 the platform draws it with an icon chip that belongs
 * to a different decade of the design language.
 *
 * This is the one way in. A screen holds one of these, hands [hostState] to its `Scaffold`, and calls
 * [show]; nothing else has to be remembered per screen, which is what keeps the next message from
 * being a toast again.
 */
class Messages(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    /**
     * Shows [text].
     *
     * Any message already up is dismissed first. Snackbars queue by default, so two failures in quick
     * succession would otherwise leave the second one waiting behind a message about the first —
     * which for a *transient* notice is worse than not showing it: the tutor reads it after they have
     * moved on, attached to whatever they are doing by then.
     */
    fun show(text: String) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }

    /**
     * Shows [text] with something to do about it — «Показать», «Отменить».
     *
     * The action is the reason this exists at all: a message like «нельзя убрать предмет: по нему
     * есть занятия» names a thing the tutor now has to go and find, and the fix is one tap away if
     * the message is allowed to carry it. A toast, by construction, never could.
     *
     * Longer-lived than a plain message, since it has to survive being read *and* aimed at.
     */
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
 * The six-line tail this replaces — a `LaunchedEffect(Unit)`, a `collect`, a `when` mapping the event
 * to an already-resolved string, and a `show` — appeared on eleven screens, and three of them had
 * drifted onto a raw `SnackbarHostState` instead: no dismissing of the message already up, so two
 * failures in a row queued and the second arrived after the tutor had moved on.
 *
 * [words] is an ordinary lambda, not a `@Composable` one, because a `stringResource` cannot be read
 * from inside the collector's coroutine. Resolve the strings above the call — which is what every screen
 * already did.
 *
 * A screen that does more than show a string per event (an undo action, a navigation) keeps its own
 * collector; this is for the eight that do not.
 */
@Composable
fun <T> ShowMessagesFor(events: UiEvents<T>, messages: Messages, words: (T) -> String) {
    LaunchedEffect(events, messages) {
        events.stream.collect { messages.show(words(it)) }
    }
}
