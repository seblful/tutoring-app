package com.seblful.klas.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class AppLockTest {
    private val time = TestTimeSource()
    private val lock = AppLock(time)

    private fun away(gone: kotlin.time.Duration, relockAfterMinutes: Int?) {
        lock.backgrounded()
        time += gone
        lock.foregrounded(relockAfterMinutes, available = true)
    }

    @Test
    fun `the app starts closed and opens on an unlock`() {
        assertFalse(lock.open.value)
        lock.unlocked()
        assertTrue(lock.open.value)
    }

    @Test
    fun `a short trip away keeps it open, a long one closes it`() {
        lock.unlocked()

        away(30.seconds, relockAfterMinutes = 1)
        assertTrue("back within the delay", lock.open.value)

        away(1.minutes, relockAfterMinutes = 1)
        assertFalse("away for the whole delay", lock.open.value)
    }

    @Test
    fun `at once means any trip away closes it`() {
        lock.unlocked()

        away(0.seconds, relockAfterMinutes = 0)

        assertFalse(lock.open.value)
    }

    @Test
    fun `a delay not read yet closes it, closed being the safe way to be wrong`() {
        lock.unlocked()

        away(1.seconds, relockAfterMinutes = null)

        assertFalse(lock.open.value)
    }

    @Test
    fun `the unlock prompt's own screen is not a trip away`() {
        lock.unlocked()
        lock.prompting(true)

        away(10.minutes, relockAfterMinutes = 1)

        assertTrue(lock.open.value)
    }

    @Test
    fun `coming up reports whether the phone can unlock at all`() {
        lock.foregrounded(relockAfterMinutes = 1, available = false)

        assertFalse(lock.available.value)
    }
}
