package com.seblful.klas.ui.common.state

import com.seblful.klas.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The guarded-write tail: [block] launched into [this] — the injected `@ApplicationScope` — with a
 * failure reported to the screen as [failure] rather than thrown.
 */
fun <E, T> CoroutineScope.write(
    events: UiEvents<E>,
    failure: E,
    onWritten: suspend (T) -> Unit = {},
    block: suspend () -> T,
) {
    launch {
        runCatchingCancellable { block() }
            .onSuccess { onWritten(it) }
            .onFailure { events.send(failure) }
    }
}

/**
 * [block] run in [this] — the injected `@ApplicationScope` — and **awaited** by the caller, which is a
 * coroutine in `viewModelScope` with something to do once the write is in: navigate away, tick what was
 * just made, raise a dialog.
 */
suspend fun <T> CoroutineScope.writeAwaited(block: suspend () -> T): Result<T> =
    async { runCatchingCancellable { block() } }.await()

/** How long a change stays undoable: longer than a long snackbar, so the window outlasts its Undo. */
val UndoWindow: Duration = 12.seconds

/**
 * [settle] once [UndoWindow] has passed — launched into [this], the `@ApplicationScope`, so a screen
 * left inside the window still lets the calendar catch up with the change.
 */
fun <E> CoroutineScope.settleAfterUndoWindow(events: UiEvents<E>, failure: E, settle: suspend () -> Unit) =
    write(events, failure) {
        delay(UndoWindow)
        settle()
    }
