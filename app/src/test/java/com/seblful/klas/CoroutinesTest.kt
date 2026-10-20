package com.seblful.klas

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutinesTest {

    @Test
    fun `a successful block returns its value`() {
        assertEquals(7, runCatchingCancellable { 7 }.getOrNull())
    }

    @Test
    fun `an ordinary failure is captured, not thrown`() {
        val result = runCatchingCancellable { error("disk full") }

        assertTrue(result.isFailure)
        assertEquals("disk full", result.exceptionOrNull()?.message)
    }

    @Test
    fun `a cancellation propagates instead of becoming a failed result`() {
        var captured: Result<Unit>? = null

        val thrown = runCatching {
            captured = runCatchingCancellable { throw CancellationException("cancelled") }
        }

        assertTrue("the cancellation was rethrown", thrown.isFailure)
        assertTrue(thrown.exceptionOrNull() is CancellationException)
        assertEquals("and never became a Result", null, captured)
    }

    @Test
    fun `a cancelled coroutine does not run the code after a guarded call`() = runTest {
        var reportedFailure = false
        var completed = false

        val job: Job = CoroutineScope(this.coroutineContext).launch {
            val result = runCatchingCancellable {
                suspendCancellableCoroutine<Unit> { }
            }
            reportedFailure = result.isFailure
            completed = true
        }

        testScheduler.advanceUntilIdle()
        job.cancel()
        testScheduler.advanceUntilIdle()

        assertFalse("a cancelled write must not be reported as a failed one", reportedFailure)
        assertFalse(completed)
        assertTrue(job.isCancelled)
    }
}
