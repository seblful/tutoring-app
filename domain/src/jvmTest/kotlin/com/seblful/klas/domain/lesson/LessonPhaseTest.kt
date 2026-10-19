package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.homework.takes
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/**
 * Where a lesson stands against the clock — the whole of [LessonPhase], on the JVM.
 *
 * Every case here is a boundary that on a device could only be reached by waiting for it: the minute a
 * lesson starts, the minute it ends, the hour after. That is the argument for this being a pure function
 * rather than a status something writes at the right moment.
 */
class LessonPhaseTest {

    private val start = Instant.parse("2026-06-15T16:00:00Z")
    private val end = Instant.parse("2026-06-15T17:00:00Z")

    private fun phaseAt(now: String, status: LessonStatus = LessonStatus.SCHEDULED) =
        LessonPhase.of(status, start, end, Instant.parse(now))

    @Test
    fun `before it starts, it is still to come`() {
        assertEquals(LessonPhase.UPCOMING, phaseAt("2026-06-15T15:59:59Z"))
    }

    /** Half-open, like `Scheduling.Slot`: the lesson is on from its first instant. */
    @Test
    fun `at its start, it is in progress`() {
        assertEquals(LessonPhase.IN_PROGRESS, phaseAt("2026-06-15T16:00:00Z"))
    }

    @Test
    fun `during it, it is in progress`() {
        assertEquals(LessonPhase.IN_PROGRESS, phaseAt("2026-06-15T16:30:00Z"))
    }

    /**
     * The other half of half-open, and the boundary that matters most: this is the same instant
     * `ClassRules.shouldAutoComplete` settles the lesson at, so a lesson can never be both running and
     * due for completion. Back-to-back lessons are the everyday case that would break — the 16:00 and the
     * 17:00 must not both read as on.
     */
    @Test
    fun `at its end, it is over and unmarked`() {
        assertEquals(LessonPhase.OVERDUE, phaseAt("2026-06-15T17:00:00Z"))
    }

    @Test
    fun `an hour later, it is still merely unmarked`() {
        assertEquals(LessonPhase.OVERDUE, phaseAt("2026-06-15T18:00:00Z"))
    }

    /** A terminal status answers for itself; the clock has nothing to add to it. */
    @Test
    fun `a marked lesson is settled whenever it is asked about`() {
        listOf("2026-06-15T15:00:00Z", "2026-06-15T16:30:00Z", "2026-06-15T18:00:00Z").forEach { now ->
            assertEquals(
                "a completed lesson at $now",
                LessonPhase.SETTLED,
                phaseAt(now, LessonStatus.COMPLETED),
            )
            assertEquals(
                "a cancelled lesson at $now",
                LessonPhase.SETTLED,
                phaseAt(now, LessonStatus.CANCELLED),
            )
        }
    }

    /**
     * A lesson called off **while it would have been running** is settled, not in progress. Cancelling is
     * the tutor saying the hour did not happen, and a badge reading «Идёт» over a cancelled lesson would
     * be the app contradicting the tutor with a clock.
     */
    @Test
    fun `cancelling a lesson takes it off the clock immediately`() {
        assertEquals(LessonPhase.SETTLED, phaseAt("2026-06-15T16:30:00Z", LessonStatus.CANCELLED))
    }

    /**
     * A zero-length row is never in progress. No write can produce one (`Scheduling.Slot` insists on a
     * positive length), but a restored backup is reproduced as given — and "never running" is the safe
     * way for an impossible row to fail.
     */
    @Test
    fun `a zero-length lesson is never in progress`() {
        val moment = Instant.parse("2026-06-15T16:00:00Z")
        assertEquals(
            LessonPhase.OVERDUE,
            LessonPhase.of(LessonStatus.SCHEDULED, moment, moment, moment),
        )
    }

    // --- The two questions callers actually ask ------------------------------------------------------

    @Test
    fun `only in progress is running, only overdue is unfinished`() {
        assertTrue(LessonPhase.IN_PROGRESS.isRunning)
        assertTrue(LessonPhase.OVERDUE.isUnfinished)
        assertFalse(LessonPhase.OVERDUE.isRunning)
        assertFalse(LessonPhase.IN_PROGRESS.isUnfinished)
        assertFalse(LessonPhase.UPCOMING.isRunning)
        assertFalse(LessonPhase.SETTLED.isUnfinished)
    }

    // --- The two conveniences, which must agree with the four-argument form --------------------------

    @Test
    fun `a class reads its own hour and status`() {
        val klass = TutoringClass(studentId = 1, subjectId = 1, start = start, durationMinutes = 60)
        assertEquals(LessonPhase.IN_PROGRESS, LessonPhase.of(klass, Instant.parse("2026-06-15T16:30:00Z")))
        assertEquals(LessonPhase.OVERDUE, LessonPhase.of(klass, Instant.parse("2026-06-15T17:30:00Z")))
    }

    /**
     * A group lesson has **one** phase, whoever is in it: every class of a session shares the hour and the
     * status, which is what makes the schedule able to badge a lesson rather than a roster.
     */
    @Test
    fun `a session's phase is its lesson's, whatever its roster`() {
        val lesson = TutoringClass(
            lessonId = 7,
            studentId = 1,
            subjectId = 1,
            start = start,
            durationMinutes = 60,
            groupId = 3,
        )
        val session = ClassSession(
            groupId = 3,
            classes = listOf(lesson.copy(id = 1), lesson.copy(id = 2, studentId = 2)),
        )
        assertEquals(LessonPhase.IN_PROGRESS, LessonPhase.of(session, Instant.parse("2026-06-15T16:30:00Z")))
    }
}
