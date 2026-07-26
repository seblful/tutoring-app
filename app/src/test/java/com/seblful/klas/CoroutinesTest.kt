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

/**
 * [runCatchingCancellable] must let a cancellation through while still catching real failures.
 *
 * Plain `runCatching` catches `Throwable`, and cancellation *is* one — so guarding a suspend call
 * with it converts "this coroutine is dead" into an ordinary failed `Result`. The caller then reports
 * a cancelled operation to the user as one that ran and failed, and carries on inside a coroutine
 * that is no longer supposed to be running.
 */
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

    /**
     * The behaviour that matters in the app: a coroutine cancelled while suspended inside a guarded
     * call must actually stop, rather than fall through to the code that reports a failure.
     */
    @Test
    fun `a cancelled coroutine does not run the code after a guarded call`() = runTest {
        var reportedFailure = false
        var completed = false

        val job: Job = CoroutineScope(this.coroutineContext).launch {
            val result = runCatchingCancellable {
                // Suspends forever, so the only way out is cancellation.
                suspendCancellableCoroutine<Unit> { }
            }
            // Neither line may be reached: the cancellation should have left this coroutine already.
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
