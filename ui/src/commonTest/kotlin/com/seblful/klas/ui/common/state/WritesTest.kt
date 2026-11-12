package com.seblful.klas.ui.common.state

import com.seblful.klas.testutil.TestMain
import com.seblful.klas.testutil.UiTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WritesTest : UiTest() {

    @BeforeTest
    fun startMain() = main.start()

    @AfterTest
    fun finishMain() = main.finish()

    private val main = TestMain()

    private enum class Event { FAILED, ANSWERED }

    @Test
    fun `a write that fails is reported - not thrown`() = runTest(main.dispatcher) {
        val events = UiEvents<Event>()
        val seen = mutableListOf<Event>()
        backgroundScope.launch { events.stream.collect { seen += it } }

        main.appScope.write(events, Event.FAILED) { error("disk full") }
        advanceUntilIdle()

        assertEquals(listOf(Event.FAILED), seen)
    }

    @Test
    fun `a write that succeeds hands its answer on and reports no failure`() = runTest(main.dispatcher) {
        val events = UiEvents<Event>()
        val seen = mutableListOf<Event>()
        backgroundScope.launch { events.stream.collect { seen += it } }

        main.appScope.write(events, Event.FAILED, onWritten = { n: Int ->
            if (n >
                0
            ) events.send(Event.ANSWERED)
        }) { 3 }
        advanceUntilIdle()

        assertEquals(listOf(Event.ANSWERED), seen)
    }

    @Test
    fun `a cancelled write is not reported as a failed one`() = runTest(main.dispatcher) {
        val events = UiEvents<Event>()
        val seen = mutableListOf<Event>()
        backgroundScope.launch { events.stream.collect { seen += it } }
        val never = CompletableDeferred<Unit>()

        main.appScope.write(events, Event.FAILED) { never.await() }
        advanceUntilIdle()
        main.appScope.cancel()
        advanceUntilIdle()

        assertTrue(seen.isEmpty())
    }

    @Test
    fun `an awaited write comes back as a value - failure included`() = runTest(main.dispatcher) {
        assertEquals(Result.success(42), main.appScope.writeAwaited { 42 })
        assertTrue(main.appScope.writeAwaited<Int> { error("disk full") }.isFailure)
    }
}
