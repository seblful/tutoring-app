package com.seblful.klas.notifications

import com.seblful.klas.domain.reminders.Reminders
import com.seblful.klas.testutil.FakeArmedReminders
import com.seblful.klas.testutil.FakeExactAlarmPermission
import com.seblful.klas.testutil.RecordingAlarmClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The reminder **replacement discipline** — the whole reason `ReminderAlarms` exists, and until now the one
 * thing about it nothing could check.
 *
 * `AlarmManager` has no "cancel everything of mine", so arming a plan means cancelling exactly the set armed
 * before it; and that set has to survive the process, or an alarm set yesterday becomes unreachable after a
 * restart. Both guarantees used to live in the same class as the `PendingIntent` building and the
 * `SharedPreferences` read, so both could only be exercised by waiting on a device — and the class had no
 * test of any kind. With the platform behind [AlarmClock] and [ArmedReminders] they are ordinary JVM code.
 */
class ReminderAlarmsTest {

    private val clockPort = RecordingAlarmClock()
    private val armed = FakeArmedReminders()
    private val exact = FakeExactAlarmPermission(granted = true)
    private val alarms = ReminderAlarms(clockPort, armed, exact)

    private val t0: Instant = Instant.parse("2026-09-01T08:00:00Z")

    private fun plan(classId: Long, minutesFromT0: Long = 0) =
        Reminders.Plan(classId = classId, fireAt = t0.plusSeconds(minutesFromT0 * 60))

    @Test
    fun `arming a plan sets one alarm per lesson, at the instant the plan carries`() {
        alarms.set(listOf(plan(1), plan(2, minutesFromT0 = 30)))

        assertEquals(
            listOf(
                RecordingAlarmClock.Armed(1, t0.toEpochMilli(), exact = true),
                RecordingAlarmClock.Armed(2, t0.plusSeconds(1800).toEpochMilli(), exact = true),
            ),
            clockPort.armed,
        )
    }

    /**
     * **Re-arming replaces exactly the previous set.** The first guarantee, and the one a diff would get
     * wrong: the plan is re-derived from scratch on every change, so what was armed before has to come off
     * whether or not it appears in the new plan.
     */
    @Test
    fun `re-arming cancels exactly what was armed before`() {
        alarms.set(listOf(plan(1), plan(2), plan(3)))
        clockPort.clear()

        alarms.set(listOf(plan(3), plan(4)))

        assertEquals("every previously armed id, and nothing else", listOf(1L, 2L, 3L), clockPort.cancelled)
        assertEquals(listOf(3L, 4L), clockPort.armed.map { it.classId })
        assertEquals(setOf(3L, 4L), armed.read())
    }

    /** A lesson still in the plan is cancelled and armed again, not left alone: its time may have moved. */
    @Test
    fun `a lesson that survives the change is re-armed at its new time`() {
        alarms.set(listOf(plan(7, minutesFromT0 = 10)))
        clockPort.clear()

        alarms.set(listOf(plan(7, minutesFromT0 = 90)))

        assertEquals(listOf(7L), clockPort.cancelled)
        assertEquals(t0.plusSeconds(90 * 60).toEpochMilli(), clockPort.armed.single().atMillis)
    }

    @Test
    fun `an empty plan disarms everything and records nothing`() {
        alarms.set(listOf(plan(1), plan(2)))
        clockPort.clear()

        alarms.set(emptyList())

        assertEquals(listOf(1L, 2L), clockPort.cancelled)
        assertTrue(clockPort.armed.isEmpty())
        assertTrue(armed.read().isEmpty())
    }

    /**
     * **The record survives the process.** The second guarantee: the ids are read back from storage rather
     * than from a field, so a plan armed before a restart is still cancellable after one.
     */
    @Test
    fun `ids armed before a restart are cancelled after it`() {
        alarms.set(listOf(plan(11), plan(12)))

        // A new instance over the same storage is what a process death looks like from here.
        val afterRestart = ReminderAlarms(clockPort, armed, exact)
        clockPort.clear()
        afterRestart.set(listOf(plan(13)))

        assertEquals(listOf(11L, 12L), clockPort.cancelled)
        assertEquals(setOf(13L), armed.read())
    }

    /**
     * Which alarm path is used is [ExactAlarmPermission]'s answer, asked **per arming** rather than
     * remembered: the grant is revocable from system settings while the app is away.
     */
    @Test
    fun `without the exact-alarm grant the inexact path is used`() {
        exact.granted = false

        alarms.set(listOf(plan(1)))

        assertEquals(false, clockPort.armed.single().exact)
    }

    @Test
    fun `the grant is re-read on every arming`() {
        alarms.set(listOf(plan(1)))
        exact.granted = false
        alarms.set(listOf(plan(1)))

        assertEquals(listOf(true, false), clockPort.armed.map { it.exact })
    }
}
