package com.seblful.klas.ui.common.state

import com.seblful.klas.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The guarded-write tail every view model's writes go through — see `ui/common/state/Writes.kt`. */
@OptIn(ExperimentalCoroutinesApi::class)
class WritesTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private enum class Event { FAILED, ANSWERED }

    @Test
    fun `a write that fails is reported, not thrown`() = runTest(mainRule.dispatcher) {
        val events = UiEvents<Event>()
        val seen = mutableListOf<Event>()
        backgroundScope.launch { events.stream.collect { seen += it } }

        mainRule.appScope.write(events, Event.FAILED) { error("disk full") }
        advanceUntilIdle()

        assertEquals(listOf(Event.FAILED), seen)
    }

    /** A refusal or a count coming back from the write is the write's answer, not its failure. */
    @Test
    fun `a write that succeeds hands its answer on and reports no failure`() = runTest(mainRule.dispatcher) {
        val events = UiEvents<Event>()
        val seen = mutableListOf<Event>()
        backgroundScope.launch { events.stream.collect { seen += it } }

        mainRule.appScope.write(events, Event.FAILED, onWritten = { n: Int -> if (n > 0) events.send(Event.ANSWERED) }) { 3 }
        advanceUntilIdle()

        assertEquals(listOf(Event.ANSWERED), seen)
    }

    /** Telling the tutor an action failed while the process was being torn down would be a lie. */
    @Test
    fun `a cancelled write is not reported as a failed one`() = runTest(mainRule.dispatcher) {
        val events = UiEvents<Event>()
        val seen = mutableListOf<Event>()
        backgroundScope.launch { events.stream.collect { seen += it } }
        val never = CompletableDeferred<Unit>()

        mainRule.appScope.write(events, Event.FAILED) { never.await() }
        advanceUntilIdle()
        mainRule.appScope.cancel()
        advanceUntilIdle()

        assertTrue(seen.isEmpty())
    }

    @Test
    fun `an awaited write comes back as a value, failure included`() = runTest(mainRule.dispatcher) {
        assertEquals(Result.success(42), mainRule.appScope.writeAwaited { 42 })
        assertTrue(mainRule.appScope.writeAwaited<Int> { error("disk full") }.isFailure)
    }
}
