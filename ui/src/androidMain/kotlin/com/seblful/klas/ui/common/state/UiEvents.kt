package com.seblful.klas.ui.common.state

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One-shot messages from a view model to whatever screen is showing it: "that was refused", "the
 * export worked". Delivered exactly once and **not** replayed, which is what separates them from
 * state — a toast that reappeared after a rotation would be reporting something that already
 * happened.
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
