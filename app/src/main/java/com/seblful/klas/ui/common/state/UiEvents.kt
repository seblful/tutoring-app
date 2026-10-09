package com.seblful.klas.ui.common.state

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One-shot messages from a view model to whatever screen is showing it: "that was refused", "the
 * export worked". Delivered exactly once and **not** replayed, which is what separates them from
 * state — a toast that reappeared after a rotation would be reporting something that already
 * happened.
 *
 * Every view model that needed this used to declare its own `Channel(BUFFERED)` and
 * `receiveAsFlow()` pair, four times over. It is one line of behaviour, but it is the line where
 * a wrong buffer or a second collector silently loses a message, so it is worth having once.
 *
 * Use [send] from ordinary code — it never suspends and never drops, because the channel is
 * buffered. A message sent while nothing is collecting waits for the next collector.
 *
 * Note the deliberate exception: a refusal that must **survive** a rotation, like the one naming
 * the lesson blocking a reschedule, is state and belongs in a `StateFlow`, not here.
 */
class UiEvents<T> {

    private val channel = Channel<T>(Channel.BUFFERED)

    /** The messages, in order. Intended for a single collector — the screen. */
    val stream: Flow<T> = channel.receiveAsFlow()

    /** Queues [event] for the screen. Safe to call from a non-suspending context. */
    fun send(event: T) {
        channel.trySend(event)
    }
}
