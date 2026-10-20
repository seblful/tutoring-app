package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.homework.takes
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.TutoringClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class LessonPhaseTest {

    private val start = Instant.parse("2026-06-15T16:00:00Z")
    private val end = Instant.parse("2026-06-15T17:00:00Z")

    private fun phaseAt(now: String, status: LessonStatus = LessonStatus.SCHEDULED) =
        LessonPhase.of(status, start, end, Instant.parse(now))

    @Test
    fun `before it starts, it is still to come`() {
        assertEquals(LessonPhase.UPCOMING, phaseAt("2026-06-15T15:59:59Z"))
    }

    @Test
    fun `at its start, it is in progress`() {
        assertEquals(LessonPhase.IN_PROGRESS, phaseAt("2026-06-15T16:00:00Z"))
    }

    @Test
    fun `during it, it is in progress`() {
        assertEquals(LessonPhase.IN_PROGRESS, phaseAt("2026-06-15T16:30:00Z"))
    }

    @Test
    fun `at its end, it is over and unmarked`() {
        assertEquals(LessonPhase.OVERDUE, phaseAt("2026-06-15T17:00:00Z"))
    }

    @Test
    fun `an hour later, it is still merely unmarked`() {
        assertEquals(LessonPhase.OVERDUE, phaseAt("2026-06-15T18:00:00Z"))
    }

    @Test
    fun `a marked lesson is settled whenever it is asked about`() {
        listOf("2026-06-15T15:00:00Z", "2026-06-15T16:30:00Z", "2026-06-15T18:00:00Z").forEach { now ->
            assertEquals(
                LessonPhase.SETTLED,
                phaseAt(now, LessonStatus.COMPLETED),
                "a completed lesson at $now",
            )
            assertEquals(
                LessonPhase.SETTLED,
                phaseAt(now, LessonStatus.CANCELLED),
                "a cancelled lesson at $now",
            )
        }
    }

    @Test
    fun `cancelling a lesson takes it off the clock immediately`() {
        assertEquals(LessonPhase.SETTLED, phaseAt("2026-06-15T16:30:00Z", LessonStatus.CANCELLED))
    }

    @Test
    fun `a zero-length lesson is never in progress`() {
        val moment = Instant.parse("2026-06-15T16:00:00Z")
        assertEquals(
            LessonPhase.OVERDUE,
            LessonPhase.of(LessonStatus.SCHEDULED, moment, moment, moment),
        )
    }

    @Test
    fun `only in progress is running, only overdue is unfinished`() {
        assertTrue(LessonPhase.IN_PROGRESS.isRunning)
        assertTrue(LessonPhase.OVERDUE.isUnfinished)
        assertFalse(LessonPhase.OVERDUE.isRunning)
        assertFalse(LessonPhase.IN_PROGRESS.isUnfinished)
        assertFalse(LessonPhase.UPCOMING.isRunning)
        assertFalse(LessonPhase.SETTLED.isUnfinished)
    }

    @Test
    fun `a class reads its own hour and status`() {
        val klass = TutoringClass(studentId = 1, subjectId = 1, start = start, durationMinutes = 60)
        assertEquals(LessonPhase.IN_PROGRESS, LessonPhase.of(klass, Instant.parse("2026-06-15T16:30:00Z")))
        assertEquals(LessonPhase.OVERDUE, LessonPhase.of(klass, Instant.parse("2026-06-15T17:30:00Z")))
    }

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
