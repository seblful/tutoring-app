package com.seblful.klas.data.repository

import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.data.lessons.UndoResult
import com.seblful.klas.domain.lesson.SessionEdit
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.lessons.LessonChange
import com.seblful.klas.lessons.UndoWindow
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LessonChangesTest {
    private val env = TestEnv()
    private val start = Instant.parse("2026-06-20T10:00:00Z")

    @AfterTest
    fun closeEnvironment() = env.close()

    private suspend fun mirroredLesson(students: Int = 1): ClassSession {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val subject = env.fixtures.subject()
        val members = (1..students).map { env.fixtures.pricedStudent("Student $it", subject) }
        val group = if (students > 1) env.fixtures.group(subjectId = subject, members = members.toSet()) else null
        val ids = env.fixtures.lesson(subject, members, start, groupId = group)
        return env.classRepo.getSessionFor(ids.first())!!
    }

    private fun LessonWrite<LessonChange?>.change(): LessonChange =
        assertIs<LessonWrite.Written<LessonChange?>>(this).value!!

    private fun TestScope.writer() = env.lessonChanges.inScope(this) { throw it }

    private suspend fun now(lesson: ClassSession) = env.classRepo.getSessionFor(lesson.first.id)!!

    @Test
    fun `undoing a cancel restores the lesson without ever withdrawing its event`() = runTest {
        val changes = writer()
        val lesson = mirroredLesson()
        val event = lesson.first.calendarEventId!!

        val receipt = changes.setStatus(lesson, LessonStatus.CANCELLED).change()
        assertEquals(LessonStatus.CANCELLED, now(lesson).status, "the rows change at once")
        assertTrue(env.calendar.deletes.isEmpty(), "the calendar waits for the undo window")

        assertEquals(UndoResult.Restored, changes.undo(receipt))

        assertEquals(LessonStatus.SCHEDULED, now(lesson).status)
        assertEquals(event, now(lesson).first.calendarEventId)
        assertTrue(env.calendar.deletes.isEmpty(), "the guest never saw the lesson called off")
    }

    @Test
    fun `a settled cancel withdraws the event`() = runTest {
        val changes = writer()
        val lesson = mirroredLesson()
        changes.setStatus(lesson, LessonStatus.CANCELLED).change()

        assertTrue(env.calendar.deletes.isEmpty())
        advanceTimeBy(UndoWindow)
        runCurrent()
        advanceTimeBy(UndoWindow)
        runCurrent()

        assertEquals(listOf(lesson.first.calendarEventId), env.calendar.deletes, "once, however often settled")
    }

    @Test
    fun `undoing the cancel of a taught lesson bills it again`() = runTest {
        val changes = writer()
        val lesson = mirroredLesson(students = 2)
        env.classRepo.setStatus(lesson, LessonStatus.COMPLETED)
        val taught = now(lesson)
        assertTrue(taught.classes.all { it.charge > Money.ZERO })

        val receipt = changes.setStatus(taught, LessonStatus.CANCELLED).change()
        assertTrue(now(lesson).classes.all { it.charge == Money.ZERO })

        assertEquals(UndoResult.Restored, changes.undo(receipt))
        assertEquals(taught.classes, now(lesson).classes)
    }

    @Test
    fun `an undo refuses a lesson edited since`() = runTest {
        val changes = writer()
        val lesson = mirroredLesson()
        val receipt = changes.setStatus(lesson, LessonStatus.CANCELLED).change()
        env.classRepo.apply(
            now(lesson), SessionEdit(lesson.first.subjectId, 90, ClassMode.REMOTE, Money.ofMajor(40)),
            start, now = env.clock.now(),
        )
        val edited = env.stored()

        assertEquals(UndoResult.ChangedSince, changes.undo(receipt))
        assertEquals(edited, env.stored())
    }

    @Test
    fun `an undo refuses an hour booked since, and the calendar catches up with the cancel`() = runTest {
        val changes = writer()
        val lesson = mirroredLesson()
        val receipt = changes.setStatus(lesson, LessonStatus.CANCELLED).change()
        val other = env.fixtures.pricedStudent("Other", lesson.first.subjectId)
        env.fixtures.lesson(lesson.first.subjectId, listOf(other), start)

        val result = changes.undo(receipt)

        assertIs<UndoResult.HourTaken>(result)
        assertEquals(LessonStatus.CANCELLED, now(lesson).status)
        assertEquals(listOf(lesson.first.calendarEventId), env.calendar.deletes)
    }

    @Test
    fun `undoing a move puts the lesson back at its hour`() = runTest {
        val changes = writer()
        val lesson = mirroredLesson()
        val receipt = changes.move(lesson, start + 1.days, env.clock.now()).change()
        assertEquals(start + 1.days, now(lesson).start)

        assertEquals(UndoResult.Restored, changes.undo(receipt))
        assertEquals(start, now(lesson).start)
    }

    @Test
    fun `undoing an attendance mark restores the member's mark`() = runTest {
        val changes = writer()
        val lesson = mirroredLesson(students = 2)
        val member = lesson.classes[1]
        val receipt = changes.setAttendance(member, Attended.ABSENT).change()
        assertEquals(Attended.ABSENT, now(lesson).classes[1].attended)

        assertEquals(UndoResult.Restored, changes.undo(receipt))
        assertEquals(lesson.classes, now(lesson).classes)
    }

    @Test
    fun `a write that changes nothing leaves nothing to undo`() = runTest {
        val changes = writer()
        val lesson = mirroredLesson()

        val written = changes.setStatus(lesson, LessonStatus.SCHEDULED)

        assertNull(assertIs<LessonWrite.Written<LessonChange?>>(written).value)
    }

    @Test
    fun `an undo of a deleted lesson reports it gone`() = runTest {
        val changes = writer()
        val lesson = mirroredLesson()
        val receipt = changes.setStatus(lesson, LessonStatus.CANCELLED).change()
        env.classRepo.delete(now(lesson))

        assertEquals(UndoResult.Gone, changes.undo(receipt))
        assertTrue(env.classRepo.getAll().isEmpty())
    }

    @Test
    fun `settlement outlives the screen that requested the change`() = runTest {
        val changes = writer()
        val lesson = mirroredLesson()
        val screen = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            screen.launch { changes.setStatus(lesson, LessonStatus.CANCELLED) }
            runCurrent()
            screen.cancel()
            advanceTimeBy(UndoWindow)
            runCurrent()

            assertEquals(listOf(lesson.first.calendarEventId), env.calendar.deletes)
        } finally {
            screen.cancel()
        }
    }

    @Test
    fun `undo releases the hold before its scheduled settlement`() = runTest {
        val changes = writer()
        val lesson = mirroredLesson()
        val change = changes.setStatus(lesson, LessonStatus.CANCELLED).change()

        assertEquals(UndoResult.Restored, changes.undo(change))
        advanceTimeBy(UndoWindow)
        runCurrent()

        assertTrue(env.calendar.deletes.isEmpty())
        assertEquals(LessonStatus.SCHEDULED, now(lesson).status)
    }
}
