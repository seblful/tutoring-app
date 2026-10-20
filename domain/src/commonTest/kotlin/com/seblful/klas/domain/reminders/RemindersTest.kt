package com.seblful.klas.domain.reminders

import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.TutoringClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RemindersTest {

    private val now = Instant.parse("2026-06-18T10:00:00Z")
    private val lead = 60.minutes
    private val horizon = 7.days

    private fun klass(
        id: Long,
        start: Instant,
        status: LessonStatus = LessonStatus.SCHEDULED,
        groupId: Long? = null,
    ) = TutoringClass(
        id = id,
        lessonId = lessonIdFor(groupId, start, id),
        studentId = id,
        subjectId = 1,
        start = start,
        durationMinutes = 60,
        status = status,
        groupId = groupId,
    )

    private val lessonIds = mutableMapOf<Pair<Long, Any>, Long>()

    private fun lessonIdFor(groupId: Long?, start: Any, id: Long): Long =
        if (groupId == null) id else lessonIds.getOrPut(groupId to start) { 1_000L + lessonIds.size }

    @Test
    fun `reminder fires one lead before a scheduled class`() {
        val start = now.plus(3.hours) // 13:00
        val plans = Reminders.plan(now, listOf(klass(1, start)), lead, horizon)
        assertEquals(1, plans.size)
        assertEquals(now.plus(2.hours), plans.first().fireAt)
    }

    @Test
    fun `a class nearer than the lead is owed rather than armed`() {
        val start = now.plus(20.minutes) // within the 60-min lead
        val plan = Reminders.plan(now, listOf(klass(1, start)), lead, horizon).single()

        assertEquals(now.minus(40.minutes), plan.fireAt)
        assertTrue(plan.due)
    }

    @Test
    fun `re-planning after a reminder has come due keeps it owed and never arms it again`() {
        val klass = klass(1, now.plus(70.minutes))

        val planned = Reminders.plan(now, listOf(klass), lead, horizon).single()
        assertEquals(now.plus(10.minutes), planned.fireAt)
        assertFalse(planned.due)

        val later = now.plus(15.minutes)
        val replanned = Reminders.plan(later, listOf(klass), lead, horizon).single()
        assertEquals(now.plus(10.minutes), replanned.fireAt)
        assertTrue(replanned.due)
    }

    @Test
    fun `a lesson that has started is still owed its reminder`() {
        val running = klass(1, now.minus(5.minutes))
        assertTrue(Reminders.plan(now, listOf(running), lead, horizon).single().due)
    }

    @Test
    fun `a lesson that has ended leaves the plan`() {
        val ended = klass(1, now.minus(70.minutes))
        assertTrue(Reminders.plan(now, listOf(ended), lead, horizon).isEmpty())
    }

    @Test
    fun `a reminder still due is planned for the remaining time - not immediately`() {
        val start = now.plus(lead).plus(30.seconds)
        val plans = Reminders.plan(now, listOf(klass(1, start)), lead, horizon)
        assertEquals(now.plus(30.seconds), plans.single().fireAt)
    }

    @Test
    fun `past classes get no reminder`() {
        val plans = Reminders.plan(now, listOf(klass(1, now.minus(1.hours))), lead, horizon)
        assertTrue(plans.isEmpty())
    }

    @Test
    fun `non-scheduled classes get no reminder`() {
        val start = now.plus(3.hours)
        val classes = listOf(
            klass(1, start, LessonStatus.CANCELLED),
            klass(2, start, LessonStatus.COMPLETED),
        )
        assertTrue(Reminders.plan(now, classes, lead, horizon).isEmpty())
    }

    @Test
    fun `classes beyond the horizon get no reminder`() {
        val start = now.plus(8.days) // beyond 7-day horizon
        assertTrue(Reminders.plan(now, listOf(klass(1, start)), lead, horizon).isEmpty())
    }

    @Test
    fun `plans one reminder per eligible class`() {
        val classes = listOf(
            klass(1, now.plus(2.hours)),
            klass(2, now.plus(1.days)),
            klass(3, now.plus(2.days)),
        )
        assertEquals(setOf(1L, 2L, 3L), Reminders.plan(now, classes, lead, horizon).map { it.classId }.toSet())
    }

    @Test
    fun `a group lesson earns one reminder - not one per member`() {
        val start = now.plus(3.hours)
        val classes = listOf(
            klass(1, start, groupId = 7),
            klass(2, start, groupId = 7),
            klass(3, start, groupId = 7),
        )

        val plans = Reminders.plan(now, classes, lead, horizon)

        assertEquals(1, plans.size)
        assertEquals(1L, plans.single().classId)
        assertEquals(now.plus(2.hours), plans.single().fireAt)
    }

    @Test
    fun `two lessons of the same group each get their own reminder`() {
        val classes = listOf(
            klass(1, now.plus(3.hours), groupId = 7),
            klass(2, now.plus(3.hours), groupId = 7),
            klass(3, now.plus(1.days), groupId = 7),
        )
        assertEquals(setOf(1L, 3L), Reminders.plan(now, classes, lead, horizon).map { it.classId }.toSet())
    }

    @Test
    fun `a group reminder points at a member who is still scheduled`() {
        val start = now.plus(3.hours)
        val classes = listOf(
            klass(1, start, LessonStatus.CANCELLED, groupId = 7),
            klass(2, start, groupId = 7),
        )
        assertEquals(listOf(2L), Reminders.plan(now, classes, lead, horizon).map { it.classId })
    }

    @Test
    fun `a due reminder posts until its lesson ends - and only while reminders are on`() {
        val upcoming = klass(1, now.plus(30.minutes))
        val running = klass(2, now.minus(30.minutes))
        val ended = klass(3, now.minus(60.minutes))

        assertTrue(Reminders.shouldPost(upcoming, notificationsEnabled = true, now = now))
        assertTrue(Reminders.shouldPost(running, notificationsEnabled = true, now = now))
        assertFalse(Reminders.shouldPost(ended, notificationsEnabled = true, now = now), "its end is the boundary")
        val calledOff = upcoming.copy(status = LessonStatus.CANCELLED)
        assertFalse(Reminders.shouldPost(calledOff, notificationsEnabled = true, now = now))
        assertFalse(Reminders.shouldPost(upcoming, notificationsEnabled = false, now = now))
    }
}
