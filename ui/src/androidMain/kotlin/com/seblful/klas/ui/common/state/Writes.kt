package com.seblful.klas.ui.common.state

import com.seblful.klas.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

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
