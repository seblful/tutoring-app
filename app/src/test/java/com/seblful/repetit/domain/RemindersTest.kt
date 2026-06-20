package com.seblful.repetit.domain

import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RemindersTest {

    private val now = Instant.parse("2026-06-18T10:00:00Z")
    private val lead = Duration.ofMinutes(60)
    private val horizon = Duration.ofDays(7)

    private fun klass(id: Long, start: Instant, status: ClassStatus = ClassStatus.SCHEDULED) =
        TutoringClass(id = id, studentId = 1, subjectId = 1, start = start, durationMinutes = 60, status = status)

    @Test
    fun `reminder fires one lead before a scheduled class`() {
        val start = now.plus(Duration.ofHours(3)) // 13:00
        val plans = Reminders.plan(now, listOf(klass(1, start)), lead, horizon)
        assertEquals(1, plans.size)
        // fire at 12:00 -> 2 hours from now
        assertEquals(Duration.ofHours(2).toMillis(), plans.first().delayMillis)
    }

    @Test
    fun `a class nearer than the lead fires immediately`() {
        val start = now.plus(Duration.ofMinutes(20)) // within the 60-min lead
        val plans = Reminders.plan(now, listOf(klass(1, start)), lead, horizon)
        assertEquals(1, plans.size)
        assertEquals(0L, plans.first().delayMillis)
    }

    @Test
    fun `past classes get no reminder`() {
        val plans = Reminders.plan(now, listOf(klass(1, now.minus(Duration.ofHours(1)))), lead, horizon)
        assertTrue(plans.isEmpty())
    }

    @Test
    fun `non-scheduled classes get no reminder`() {
        val start = now.plus(Duration.ofHours(3))
        val classes = listOf(
            klass(1, start, ClassStatus.CANCELLED),
            klass(2, start, ClassStatus.COMPLETED),
        )
        assertTrue(Reminders.plan(now, classes, lead, horizon).isEmpty())
    }

    @Test
    fun `classes beyond the horizon get no reminder`() {
        val start = now.plus(Duration.ofDays(8)) // beyond 7-day horizon
        assertTrue(Reminders.plan(now, listOf(klass(1, start)), lead, horizon).isEmpty())
    }

    @Test
    fun `plans one reminder per eligible class`() {
        val classes = listOf(
            klass(1, now.plus(Duration.ofHours(2))),
            klass(2, now.plus(Duration.ofDays(1))),
            klass(3, now.plus(Duration.ofDays(2))),
        )
        assertEquals(setOf(1L, 2L, 3L), Reminders.plan(now, classes, lead, horizon).map { it.classId }.toSet())
    }
}
