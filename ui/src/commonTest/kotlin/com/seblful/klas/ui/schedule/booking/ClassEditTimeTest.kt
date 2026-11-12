package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ClassEditTimeTest : ClassEditTestFixture() {

    @Test
    fun `picking a start time gives the lesson the default length`() = runTest(main.dispatcher) {
        env.settingsRepo.update { it.copy(defaultLessonMinutes = 90) }
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.change(BookingIntent.StartAt(LocalTime(10, 0)))
        advanceUntilIdle()
        assertEquals(90, vm.state.current.form.durationMinutes)
        assertEquals(LocalTime(11, 30), vm.state.current.ending?.time)
    }

    @Test
    fun `a new lesson opens with the default length already chosen`() = runTest(main.dispatcher) {
        env.settingsRepo.update { it.copy(defaultLessonMinutes = 90) }
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(90, vm.state.current.form.durationMinutes, "the length it will book is on screen")
        assertFalse(vm.state.current.isDirty, "the app chose it, not the tutor")
    }

    @Test
    fun `opening an existing lesson keeps its own length`() = runTest(main.dispatcher) {
        env.settingsRepo.update { it.copy(defaultLessonMinutes = 90) }
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        val start = LocalDate(2026, 6, 22).atTime(10, 0).toInstant(zone)
        val classId = env.fixtures.lesson(subjectId, listOf(studentId), start, minutes = 45).single()
        val vm = vm(id = classId)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(45, vm.state.current.form.durationMinutes)
    }

    @Test
    fun `changing the start time keeps the lesson length`() = runTest(main.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        // A 90-minute lesson, 10:00–11:30.
        vm.change(BookingIntent.StartAt(LocalTime(10, 0)))
        vm.change(BookingIntent.LastsFor(90))

        // Moving the start later leaves it 90 minutes long, now 14:00–15:30.
        vm.change(BookingIntent.StartAt(LocalTime(14, 0)))
        assertEquals(90, vm.state.current.form.durationMinutes)
        assertEquals(LocalTime(15, 30), vm.state.current.ending?.time)
    }

    @Test
    fun `the length picker opens on the default length while nothing is chosen`() =
        runTest(main.dispatcher) {
            env.settingsRepo.update { it.copy(defaultLessonMinutes = 90) }
            val vm = vm()
            advanceUntilIdle()

            // The length is the tutor's own.
            assertEquals(90, vm.durationPickerDefault())
        }

    @Test
    fun `the length picker reopens on what was already chosen`() = runTest(main.dispatcher) {
        val vm = vm()
        advanceUntilIdle()

        vm.change(BookingIntent.StartAt(LocalTime(10, 0)))
        vm.change(BookingIntent.LastsFor(50))

        assertEquals(50, vm.durationPickerDefault())
    }

    /** The last picker default the platform used to answer from inside a composable. */
    @Test
    fun `the date picker opens on the chosen date - or today`() = runTest(main.dispatcher) {
        val vm = vm()
        advanceUntilIdle()

        val today = LocalDate(2026, 7, 1)
        assertEquals(today, vm.datePickerDefault(today = today))

        vm.change(BookingIntent.Edit { it.copy(date = LocalDate(2026, 8, 9)) })
        assertEquals(LocalDate(2026, 8, 9), vm.datePickerDefault(today = today))
    }

    /**
     * The add button books into the day the schedule is on, without asking the tutor to choose it again.
     */
    @Test
    fun `a booking opened from a day prefills that date and still allows changing it`() = runTest(main.dispatcher) {
        val thursday = LocalDate(2026, 10, 1)
        val vm = vm(day = thursday)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(thursday, vm.datePickerDefault(today = LocalDate(2026, 9, 24)))
        assertEquals(thursday, vm.state.current.form.date)
        assertFalse(vm.state.current.isDirty, "the prefilled date is not an unsaved edit")
        assertNull(vm.state.current.form.timeFrom, "the start time is still the tutor's to choose")

        vm.change(BookingIntent.Edit { it.copy(date = LocalDate(2026, 10, 5)) })
        advanceUntilIdle()
        assertEquals(LocalDate(2026, 10, 5), vm.state.current.form.date)
        assertEquals(LocalDate(2026, 10, 5), vm.datePickerDefault(today = LocalDate(2026, 9, 24)))
    }

    @Test
    fun `editing keeps the lesson date when a different schedule day is supplied`() = runTest(main.dispatcher) {
        val lessonDay = LocalDate(2026, 10, 1)
        val (_, id) = seedLesson(lessonDay, LocalTime(14, 0))
        val vm = vm(id = id, day = lessonDay.plus(7, DateTimeUnit.DAY))
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(lessonDay, vm.state.current.form.date)
        assertEquals(lessonDay, vm.datePickerDefault())
    }

    @Test
    fun `editing opens on the lesson's own time and length`() = runTest(main.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        val start = Instant.parse("2026-06-15T07:00:00Z")
        val id = env.fixtures.storedClass(
            studentId = studentId, subjectId = subjectId, startMillis = start.toEpochMilliseconds(),
            durationMinutes = 60, priceMinor = Money.ofMajor(30).minor, status = LessonStatus.SCHEDULED,
        )
        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        val from = start.toLocalDateTime(zone).time
        assertEquals(from, vm.state.current.form.timeFrom)
        assertEquals(60, vm.durationPickerDefault())
    }

    @Test
    fun `a lesson that starts at 23 00 opens ready to save`() = runTest(main.dispatcher) {
        val (_, id) = seedLesson(env.clock.today().plus(3, DateTimeUnit.DAY), LocalTime(23, 0))
        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        val state = vm.state.current
        assertEquals(60, state.form.durationMinutes)
        assertEquals(LocalTime(0, 0), state.ending?.time)
        assertEquals(true, state.ending?.nextDay)
        assertTrue(state.canSave, "a lesson already in the schedule is one the form can save")
    }
}
