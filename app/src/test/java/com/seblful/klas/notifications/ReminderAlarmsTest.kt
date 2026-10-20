package com.seblful.klas.notifications

import com.seblful.klas.domain.reminders.Reminders
import com.seblful.klas.testutil.FakeArmedReminders
import com.seblful.klas.testutil.FakeExactAlarmPermission
import com.seblful.klas.testutil.RecordingAlarmClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ReminderAlarmsTest {

    private val clockPort = RecordingAlarmClock()
    private val armed = FakeArmedReminders()
    private val exact = FakeExactAlarmPermission(granted = true)
    private val alarms = ReminderAlarms(clockPort, armed, exact)

    private val t0: Instant = Instant.parse("2026-09-01T08:00:00Z")

    private fun plan(classId: Long, minutesFromT0: Long = 0, due: Boolean = false) =
        Reminders.Plan(classId = classId, fireAt = t0.plus((minutesFromT0 * 60).seconds), due = due)

    @Test
    fun `arming a plan sets one alarm per lesson, at the instant the plan carries`() {
        alarms.set(listOf(plan(1), plan(2, minutesFromT0 = 30)))

        assertEquals(
            listOf(
                RecordingAlarmClock.Armed(1, t0.toEpochMilliseconds(), exact = true),
                RecordingAlarmClock.Armed(2, t0.plus(1800.seconds).toEpochMilliseconds(), exact = true),
            ),
            clockPort.armed,
        )
    }

    @Test
    fun `re-arming cancels exactly what was armed before`() {
        alarms.set(listOf(plan(1), plan(2), plan(3)))
        clockPort.clear()

        alarms.set(listOf(plan(3), plan(4)))

        assertEquals("every previously armed id, and nothing else", listOf(1L, 2L, 3L), clockPort.cancelled)
        assertEquals(listOf(3L, 4L), clockPort.armed.map { it.classId })
        assertEquals(setOf(3L, 4L), armed.read())
    }

    @Test
    fun `a reminder that has come due is left armed, and is not set again`() {
        alarms.set(listOf(plan(1, minutesFromT0 = -30), plan(2, minutesFromT0 = 120)))
        clockPort.clear()

        alarms.set(listOf(plan(1, minutesFromT0 = -30, due = true), plan(3, minutesFromT0 = 180)))

        assertEquals("the lesson the new plan dropped is the only one cancelled", listOf(2L), clockPort.cancelled)
        assertEquals("and nothing owed is set a second time", listOf(3L), clockPort.armed.map { it.classId })
        assertEquals("it stays recorded, so a later pass can still reach it", setOf(1L, 3L), armed.read())
    }

    @Test
    fun `a lesson that survives the change is re-armed at its new time`() {
        alarms.set(listOf(plan(7, minutesFromT0 = 10)))
        clockPort.clear()

        alarms.set(listOf(plan(7, minutesFromT0 = 90)))

        assertEquals(listOf(7L), clockPort.cancelled)
        assertEquals(t0.plus((90 * 60).seconds).toEpochMilliseconds(), clockPort.armed.single().atMillis)
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

    @Test
    fun `ids armed before a restart are cancelled after it`() {
        alarms.set(listOf(plan(11), plan(12)))

        val afterRestart = ReminderAlarms(clockPort, armed, exact)
        clockPort.clear()
        afterRestart.set(listOf(plan(13)))

        assertEquals(listOf(11L, 12L), clockPort.cancelled)
        assertEquals(setOf(13L), armed.read())
    }

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
