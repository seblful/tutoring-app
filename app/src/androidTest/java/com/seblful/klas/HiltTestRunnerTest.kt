package com.seblful.klas

import com.seblful.klas.work.BootReceiver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the runner drops and what it still lets kill the process: only a receiver that woke before any test
 * built a Hilt component. See [isReceiverWokenBeforeAnyTest].
 *
 * Plain JUnit, no `HiltAndroidRule`: the point of the runner's hook is that a stray wake-up no longer needs
 * one. The race itself — the system's `BOOT_COMPLETED` at process start — cannot be staged from a test, and
 * is held by running a class from a stopped app repeatedly and counting crashes (see `docs/testing.md`).
 */
class HiltTestRunnerTest {

    private val noComponent = IllegalStateException(
        "The component was not created. Check that you have added the HiltAndroidRule.",
    )

    @Test
    fun aReceiverWokenBeforeAnyTestIsDropped() {
        assertTrue(isReceiverWokenBeforeAnyTest(BootReceiver(), noComponent))
    }

    @Test
    fun aReceiverFailingForAnyOtherReasonStillCrashes() {
        assertFalse(isReceiverWokenBeforeAnyTest(BootReceiver(), IllegalStateException("boom")))
        assertFalse(isReceiverWokenBeforeAnyTest(BootReceiver(), NullPointerException()))
    }

    @Test
    fun aComponentThatIsNotAReceiverStillCrashes() {
        assertFalse(isReceiverWokenBeforeAnyTest(Any(), noComponent))
        assertFalse(isReceiverWokenBeforeAnyTest(null, noComponent))
    }

    @Test
    fun noErrorIsNotDropped() {
        assertFalse(isReceiverWokenBeforeAnyTest(BootReceiver(), null))
    }
}
