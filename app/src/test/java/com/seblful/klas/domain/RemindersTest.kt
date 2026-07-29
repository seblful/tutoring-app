package com.seblful.klas.domain

import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RemindersTest {

    private val now = Instant.parse("2026-06-18T10:00:00Z")
    private val lead = Duration.ofMinutes(60)
    private val horizon = Duration.ofDays(7)

    private fun klass(
        id: Long,
        start: Instant,
        status: ClassStatus = ClassStatus.SCHEDULED,
        groupId: Long? = null,
    ) = TutoringClass(
        id = id,
        // Each member of a group lesson is a different student; irrelevant for individual classes.
        studentId = id,
        subjectId = 1,
        start = start,
        durationMinutes = 60,
        status = status,
        groupId = groupId,
    )

    @Test
    fun `reminder fires one lead before a scheduled class`() {
        val start = now.plus(Duration.ofHours(3)) // 13:00
        val plans = Reminders.plan(now, listOf(klass(1, start)), lead, horizon)
        assertEquals(1, plans.size)
        // fires at 12:00 — one lead before the 13:00 start
        assertEquals(now.plus(Duration.ofHours(2)), plans.first().fireAt)
    }

    @Test
    fun `a class nearer than the lead gets no reminder`() {
        // Booking a lesson for the next hour must not announce it on the spot: the plan is
        // re-derived on every change, so an immediate firing would repeat with each one.
        val start = now.plus(Duration.ofMinutes(20)) // within the 60-min lead
        assertTrue(Reminders.plan(now, listOf(klass(1, start)), lead, horizon).isEmpty())
    }

    @Test
    fun `re-planning after a reminder has come due does not plan it again`() {
        val klass = klass(1, now.plus(Duration.ofMinutes(70)))

        // Planned for 10 minutes out...
        assertEquals(
            now.plus(Duration.ofMinutes(10)),
            Reminders.plan(now, listOf(klass), lead, horizon).single().fireAt,
        )
        // ...and once that moment has passed (a later class change re-plans everything), gone.
        val later = now.plus(Duration.ofMinutes(15))
        assertTrue(Reminders.plan(later, listOf(klass), lead, horizon).isEmpty())
    }

    @Test
    fun `a reminder still due is planned for the remaining time, not immediately`() {
        val start = now.plus(lead).plus(Duration.ofSeconds(30))
        val plans = Reminders.plan(now, listOf(klass(1, start)), lead, horizon)
        assertEquals(now.plus(Duration.ofSeconds(30)), plans.single().fireAt)
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

    @Test
    fun `a group lesson earns one reminder, not one per member`() {
        val start = now.plus(Duration.ofHours(3))
        val classes = listOf(
            klass(1, start, groupId = 7),
            klass(2, start, groupId = 7),
            klass(3, start, groupId = 7),
        )

        val plans = Reminders.plan(now, classes, lead, horizon)

        assertEquals(1, plans.size)
        assertEquals(1L, plans.single().classId)
        assertEquals(now.plus(Duration.ofHours(2)), plans.single().fireAt)
    }

    @Test
    fun `two lessons of the same group each get their own reminder`() {
        val classes = listOf(
            klass(1, now.plus(Duration.ofHours(3)), groupId = 7),
            klass(2, now.plus(Duration.ofHours(3)), groupId = 7),
            klass(3, now.plus(Duration.ofDays(1)), groupId = 7),
        )
        assertEquals(setOf(1L, 3L), Reminders.plan(now, classes, lead, horizon).map { it.classId }.toSet())
    }

    @Test
    fun `a group reminder points at a member who is still scheduled`() {
        val start = now.plus(Duration.ofHours(3))
        val classes = listOf(
            // The lowest-id member has been cancelled (didn't come); the lesson still happens.
            klass(1, start, ClassStatus.CANCELLED, groupId = 7),
            klass(2, start, groupId = 7),
        )
        assertEquals(listOf(2L), Reminders.plan(now, classes, lead, horizon).map { it.classId })
    }
}
