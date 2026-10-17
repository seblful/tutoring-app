package com.seblful.klas.domain.reminders

import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        status: LessonStatus = LessonStatus.SCHEDULED,
        groupId: Long? = null,
    ) = TutoringClass(
        id = id,
        lessonId = lessonIdFor(groupId, start, id),
        // Each member of a group lesson is a different student; irrelevant for individual classes.
        studentId = id,
        subjectId = 1,
        start = start,
        durationMinutes = 60,
        status = status,
        groupId = groupId,
    )

    private val lessonIds = mutableMapOf<Pair<Long, Any>, Long>()

    /**
     * The lesson a fixture's class belongs to: one per (group, start) for a group's, its own id for an
     * individual class. A convention of the test — storage has a column for it — and it has to be stated
     * because that column is the whole of what makes classes one lesson (`SessionRules.sessions`).
     */
    private fun lessonIdFor(groupId: Long?, start: Any, id: Long): Long =
        if (groupId == null) id else lessonIds.getOrPut(groupId to start) { 1_000L + lessonIds.size }

    @Test
    fun `reminder fires one lead before a scheduled class`() {
        val start = now.plus(Duration.ofHours(3)) // 13:00
        val plans = Reminders.plan(now, listOf(klass(1, start)), lead, horizon)
        assertEquals(1, plans.size)
        // fires at 12:00 — one lead before the 13:00 start
        assertEquals(now.plus(Duration.ofHours(2)), plans.first().fireAt)
    }

    @Test
    fun `a class nearer than the lead is owed rather than armed`() {
        // Booking a lesson for the next hour must not announce it on the spot: the plan is re-derived
        // on every change, so arming an instant already behind us would repeat with each one. It is
        // still *stated*, so that an alarm already set for it is not taken away — see `ReminderAlarms`.
        val start = now.plus(Duration.ofMinutes(20)) // within the 60-min lead
        val plan = Reminders.plan(now, listOf(klass(1, start)), lead, horizon).single()

        assertEquals(now.minus(Duration.ofMinutes(40)), plan.fireAt)
        assertTrue(plan.due)
    }

    @Test
    fun `re-planning after a reminder has come due keeps it owed and never arms it again`() {
        val klass = klass(1, now.plus(Duration.ofMinutes(70)))

        // Planned for 10 minutes out, and still to be armed...
        val planned = Reminders.plan(now, listOf(klass), lead, horizon).single()
        assertEquals(now.plus(Duration.ofMinutes(10)), planned.fireAt)
        assertFalse(planned.due)

        // ...and once that moment has passed (a later class change re-plans everything) it comes back
        // owed: the alarm that plan set is still waiting, and the adapter has to leave it alone.
        val later = now.plus(Duration.ofMinutes(15))
        val replanned = Reminders.plan(later, listOf(klass), lead, horizon).single()
        assertEquals(now.plus(Duration.ofMinutes(10)), replanned.fireAt)
        assertTrue(replanned.due)
    }

    /**
     * A lesson the tutor is in the middle of is still worth being told about — [Reminders.shouldPost]
     * keeps posting until it ends — so its reminder stays owed while it runs, and the alarm waiting to
     * deliver it is not taken away underneath it.
     */
    @Test
    fun `a lesson that has started is still owed its reminder`() {
        val running = klass(1, now.minus(Duration.ofMinutes(5)))
        assertTrue(Reminders.plan(now, listOf(running), lead, horizon).single().due)
    }

    /** Once the lesson is over there is nothing left to warn about, and its alarm may finally go. */
    @Test
    fun `a lesson that has ended leaves the plan`() {
        val ended = klass(1, now.minus(Duration.ofMinutes(70)))
        assertTrue(Reminders.plan(now, listOf(ended), lead, horizon).isEmpty())
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
            klass(1, start, LessonStatus.CANCELLED),
            klass(2, start, LessonStatus.COMPLETED),
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
            klass(1, start, LessonStatus.CANCELLED, groupId = 7),
            klass(2, start, groupId = 7),
        )
        assertEquals(listOf(2L), Reminders.plan(now, classes, lead, horizon).map { it.classId })
    }

    /**
     * A reminder that fires late still posts while its lesson is on — the lesson phases that are still
     * worth hearing about ([LessonPhase.UPCOMING], [LessonPhase.IN_PROGRESS]) — and never once it is over.
     */
    @Test
    fun `a due reminder posts until its lesson ends, and only while reminders are on`() {
        val upcoming = klass(1, now.plus(Duration.ofMinutes(30)))
        val running = klass(2, now.minus(Duration.ofMinutes(30)))
        val ended = klass(3, now.minus(Duration.ofMinutes(60)))

        assertTrue(Reminders.shouldPost(upcoming, notificationsEnabled = true, now = now))
        assertTrue(Reminders.shouldPost(running, notificationsEnabled = true, now = now))
        assertFalse("its end is the boundary", Reminders.shouldPost(ended, notificationsEnabled = true, now = now))
        val calledOff = upcoming.copy(status = LessonStatus.CANCELLED)
        assertFalse(Reminders.shouldPost(calledOff, notificationsEnabled = true, now = now))
        assertFalse(Reminders.shouldPost(upcoming, notificationsEnabled = false, now = now))
    }
}
