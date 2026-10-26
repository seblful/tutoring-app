package com.seblful.klas

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LaunchAsyncTest {

    private val unhandled = mutableListOf<Throwable>()
    private val scope = CoroutineScope(
        SupervisorJob() + UnconfinedTestDispatcher() + CoroutineExceptionHandler { _, e -> unhandled += e },
    )

    @Test
    fun `finishes the broadcast once the work is done`() {
        var finished = 0

        launchHeldOpen(scope, { finished++ }) {}

        assertEquals(1, finished)
        assertTrue(unhandled.isEmpty())
    }

    @Test
    fun `a failure reaches the scope's handler and still finishes the broadcast`() {
        var finished = 0
        val failure = IllegalStateException("database is locked")

        launchHeldOpen(scope, { finished++ }) { throw failure }

        assertEquals(1, finished)
        assertSame(failure, unhandled.single())
    }

    @Test
    fun `a cancelled scope finishes the broadcast without reporting a failure`() {
        var finished = 0

        launchHeldOpen(scope, { finished++ }) { throw CancellationException("scope closed") }

        assertEquals(1, finished)
        assertTrue(unhandled.isEmpty())
    }
}
