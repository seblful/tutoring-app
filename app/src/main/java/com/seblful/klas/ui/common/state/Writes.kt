package com.seblful.klas.ui.common.state

import com.seblful.klas.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * The guarded-write tail: [block] launched into [this] — the injected `@ApplicationScope` — with a
 * failure reported to the screen as [failure] rather than thrown.
 *
 * The scope is the point. The sheets, dialogs and swipes these writes are tapped in close as they are
 * tapped, so a write left in `viewModelScope` could be cut in half by the screen going away. What that
 * scope does not give is a reply: a bare `appScope.launch` reports a failure only to the scope's handler,
 * which records it to the diagnostics log and says nothing to anybody, so a status change that failed
 * looked exactly like one that had not been tapped. Six view models used to spell this out as a private
 * `write` of their own, and the copies were not quite copies: some answered something on success too,
 * which is what [onWritten] carries — a refusal or a count coming back from the write is an event of its
 * own, and only a database or calendar error may reach [failure].
 *
 * [runCatchingCancellable] rather than `runCatching`: a cancelled write is not a failed one, and telling
 * the tutor an action failed while the process was being torn down would be a lie. Being caught here,
 * the failure does not also reach the scope's handler.
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
 *
 * **The save outlives the screen; only the navigation waits.** Going back the instant after tapping
 * «Сохранить» cancels the awaiting coroutine and nothing else — the write is already out of its reach,
 * so it is not cut off between the committed rows and the calendar mirror and reminder refresh that
 * follow them.
 *
 * Guarded *inside* the `async`, because a failure has to come back as a value. An exception would cross
 * the `await` into `viewModelScope`, where nothing catches it — and the application scope's own handler
 * does not see it either, since `async` holds a failure in its `Deferred` rather than reporting it. A save
 * that failed therefore used to crash the app, in the one place the tutor is most sure of what they did.
 */
suspend fun <T> CoroutineScope.writeAwaited(block: suspend () -> T): Result<T> =
    async { runCatchingCancellable { block() } }.await()
