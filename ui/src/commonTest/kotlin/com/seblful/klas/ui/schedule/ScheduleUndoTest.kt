package com.seblful.klas.ui.schedule

import com.seblful.klas.domain.lesson.SessionEdit
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.ui.schedule.ScheduleViewModel.Undoable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleUndoTest : ScheduleTestFixture() {

    private suspend fun mirroredLesson(students: Int = 1): ClassSession {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val subject = env.fixtures.subject()
        val members = (1..students).map { env.fixtures.pricedStudent("Student $it", subject) }
        val group = if (students > 1) env.fixtures.group(subjectId = subject, members = members.toSet()) else null
        val ids = env.fixtures.lesson(subject, members, at(10), groupId = group)
        return env.classRepo.getSessionFor(ids.first())!!
    }

    private suspend fun now(lesson: ClassSession) = env.classRepo.getSessionFor(lesson.first.id)!!

    @Test
    fun `a cancel offers an undo - and taking it back never withdraws the calendar event`() =
        runTest(main.dispatcher) {
            val lesson = mirroredLesson()
            val vm = vm()

            vm.setStatus(lesson, LessonStatus.CANCELLED)
            runCurrent()
            val offered = vm.undoables.stream.first()
            assertEquals(Undoable.Change.CANCELLED, offered.change)
            assertEquals(LessonStatus.CANCELLED, now(lesson).status)

            vm.undo(offered)
            advanceUntilIdle()

            assertEquals(LessonStatus.SCHEDULED, now(lesson).status)
            assertTrue(env.calendar.deletes.isEmpty(), "the calendar never saw the cancel")
        }

    @Test
    fun `a cancel left alone reaches the calendar once the undo window closes`() = runTest(main.dispatcher) {
        val lesson = mirroredLesson()
        val vm = vm()

        vm.setStatus(lesson, LessonStatus.CANCELLED)
        runCurrent()
        assertTrue(env.calendar.deletes.isEmpty())

        advanceUntilIdle()

        assertEquals(listOf(lesson.first.calendarEventId), env.calendar.deletes)
    }

    @Test
    fun `re-planning a cancelled lesson offers its own undo`() = runTest(main.dispatcher) {
        val lesson = mirroredLesson()
        env.classRepo.setStatus(lesson, LessonStatus.CANCELLED)
        val vm = vm()

        vm.setStatus(now(lesson), LessonStatus.SCHEDULED)
        runCurrent()
        val offered = vm.undoables.stream.first()
        assertEquals(Undoable.Change.REPLANNED, offered.change)

        vm.undo(offered)
        advanceUntilIdle()
        assertEquals(LessonStatus.CANCELLED, now(lesson).status)
    }

    @Test
    fun `an undo of a lesson edited since leaves the edit and says so`() = runTest(main.dispatcher) {
        val lesson = mirroredLesson()
        val vm = vm()
        val seen = mutableListOf<ScheduleViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { seen += it } }
        vm.setStatus(lesson, LessonStatus.CANCELLED)
        runCurrent()
        val offered = vm.undoables.stream.first()
        env.classRepo.apply(
            now(lesson), SessionEdit(lesson.first.subjectId, 90, ClassMode.REMOTE, Money.ofMajor(40)),
            lesson.start, now = env.clock.now(),
        )

        vm.undo(offered)
        advanceUntilIdle()

        assertEquals(listOf<ScheduleViewModel.Event>(ScheduleViewModel.Event.UNDO_CHANGED), seen)
        assertEquals(LessonStatus.CANCELLED, now(lesson).status)
        assertEquals(90, now(lesson).durationMinutes)
    }

    @Test
    fun `a move offers an undo that puts the lesson back`() = runTest(main.dispatcher) {
        val lesson = mirroredLesson()
        val vm = vm()

        vm.moveTo(lesson, LocalDate(2026, 5, 19), LocalTime(12, 0))
        runCurrent()
        val offered = vm.undoables.stream.first()
        assertEquals(Undoable.Change.MOVED, offered.change)

        vm.undo(offered)
        advanceUntilIdle()
        assertEquals(at(10), now(lesson).start)
    }

    @Test
    fun `an attendance mark offers an undo that restores it`() = runTest(main.dispatcher) {
        val lesson = mirroredLesson(students = 2)
        val vm = vm()

        vm.setAttendance(lesson.classes[1], Attended.ABSENT)
        runCurrent()
        val offered = vm.undoables.stream.first()
        assertEquals(Undoable.Change.ATTENDANCE, offered.change)
        assertEquals(Attended.ABSENT, now(lesson).classes[1].attended)

        vm.undo(offered)
        advanceUntilIdle()
        assertEquals(lesson.classes, now(lesson).classes)
    }

    @Test
    fun `marking taught keeps its own correction instead of a status undo`() = runTest(main.dispatcher) {
        val lesson = mirroredLesson()
        val vm = vm()

        vm.setStatus(lesson, LessonStatus.COMPLETED)
        advanceUntilIdle()

        assertEquals(LessonStatus.COMPLETED, vm.completions.stream.first().lesson.status)
    }
}
