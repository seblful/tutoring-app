package com.seblful.klas.ui.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one-shot channel every view model reports refusals through. It is four lines of code, which is
 * why it had no test — and it is also the four lines where a wrong buffer or a replay silently changes
 * what the tutor sees, so the contract its KDoc states is worth holding it to.
 *
 * The two halves that matter pull in opposite directions: a message must **survive** having been sent
 * before the screen was listening, and must **not** survive having already been shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UiEventsTest {

    private enum class Event { REFUSED, SAVED, EXPORTED }

    @Test
    fun `a message sent before the screen is looking is not lost`() = runTest {
        val events = UiEvents<Event>()

        // A view model can refuse a save in the same frame the screen is still being composed in.
        events.send(Event.REFUSED)

        assertEquals(Event.REFUSED, events.stream.first())
    }

    @Test
    fun `messages arrive in the order they were sent`() = runTest {
        val events = UiEvents<Event>()

        events.send(Event.REFUSED)
        events.send(Event.SAVED)
        events.send(Event.EXPORTED)

        assertEquals(
            listOf(Event.REFUSED, Event.SAVED, Event.EXPORTED),
            events.stream.take(3).toList(),
        )
    }

    /**
     * The line that separates an event from state. A `StateFlow` would hand the same refusal to
     * whoever looked next, so rotating the phone would re-announce something that already happened —
     * which is exactly why these are not state.
     *
     * Deliberately asserts only that the consumed message does not come back, and not that a second
     * collector goes on receiving: [UiEvents.stream] is a `receiveAsFlow`, which is single-consumer by
     * contract, and the screen is that one consumer. Asserting anything about a second one would pin
     * behaviour the type does not promise and production never asks for.
     */
    @Test
    fun `a message already shown is not replayed`() = runTest {
        val events = UiEvents<Event>()
        events.send(Event.REFUSED)
        assertEquals(Event.REFUSED, events.stream.first())

        val seen = mutableListOf<Event>()
        backgroundScope.launch { events.stream.collect { seen += it } }
        advanceUntilIdle()

        assertEquals(
            "a rotation must not repeat a refusal the tutor has already been shown",
            emptyList<Event>(),
            seen,
        )
    }

    /**
     * [UiEvents.send] is called from ordinary non-suspending code, so it must never be the thing that
     * blocks a view model. A burst queues instead — more than any screen produces between frames.
     */
    @Test
    fun `sending does not block the caller, and a burst queues in order`() = runTest {
        val events = UiEvents<Event>()

        repeat(10) { events.send(Event.REFUSED) }

        assertEquals(List(10) { Event.REFUSED }, events.stream.take(10).toList())
    }
}
