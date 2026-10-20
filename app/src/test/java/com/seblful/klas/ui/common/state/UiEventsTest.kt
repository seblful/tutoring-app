package com.seblful.klas.ui.common.state

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UiEventsTest {

    private enum class Event { REFUSED, SAVED, EXPORTED }

    @Test
    fun `a message sent before the screen is looking is not lost`() = runTest {
        val events = UiEvents<Event>()

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

    @Test
    fun `sending does not block the caller, and a burst queues in order`() = runTest {
        val events = UiEvents<Event>()

        repeat(10) { events.send(Event.REFUSED) }

        assertEquals(List(10) { Event.REFUSED }, events.stream.take(10).toList())
    }
}
