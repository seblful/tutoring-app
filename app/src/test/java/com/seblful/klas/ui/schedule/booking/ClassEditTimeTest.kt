package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
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
import kotlinx.datetime.todayIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ClassEditTimeTest : ClassEditTestFixture() {

    @Test
    fun `picking a start time gives the lesson the default length`() = runTest(mainRule.dispatcher) {
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
    fun `a new lesson opens with the default length already chosen`() = runTest(mainRule.dispatcher) {
        env.settingsRepo.update { it.copy(defaultLessonMinutes = 90) }
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals("the length it will book is on screen", 90, vm.state.current.form.durationMinutes)
        assertFalse("the app chose it, not the tutor", vm.state.current.isDirty)
    }

    @Test
    fun `opening an existing lesson keeps its own length`() = runTest(mainRule.dispatcher) {
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
    fun `changing the start time keeps the lesson length`() = runTest(mainRule.dispatcher) {
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
    fun `the pickers open on the next whole hour and the default length while nothing is chosen`() =
        runTest(mainRule.dispatcher) {
            env.settingsRepo.update { it.copy(defaultLessonMinutes = 90) }
            val vm = vm()
            advanceUntilIdle()

            // A lesson arranged at 14:37 is held at 15:00 — tutoring runs on the hour, so an empty form
            // rounds up rather than offering minutes the tutor would only spin away. The length is the
            // tutor's own.
            assertEquals(LocalTime(15, 0), vm.startPickerDefault(LocalTime(14, 37, 42)))
            assertEquals(90, vm.durationPickerDefault())
        }

    @Test
    fun `a time already on the hour is the hour it opens on`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        advanceUntilIdle()

        // Nothing to round: 15:00 is neither in the past nor short of the hour, so it must not be
        // pushed to 16:00.
        assertEquals(LocalTime(15, 0), vm.startPickerDefault(LocalTime(15, 0)))
        // Seconds are not minutes, and are below what the picker can even show — 15:00:20 is still
        // the 15:00 hour, not a reason to offer 16:00.
        assertEquals(LocalTime(15, 0), vm.startPickerDefault(LocalTime(15, 0, 20)))
    }

    @Test
    fun `the last hour of the day rounds up to midnight rather than overflowing`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            advanceUntilIdle()

            // The date is a separate field, so this is "the next hour", not a claim about which day.
            assertEquals(LocalTime(0, 0), vm.startPickerDefault(LocalTime(23, 40)))
        }

    @Test
    fun `the pickers reopen on what was already chosen`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        advanceUntilIdle()

        vm.change(BookingIntent.StartAt(LocalTime(10, 0)))
        vm.change(BookingIntent.LastsFor(50))

        assertEquals(LocalTime(10, 0), vm.startPickerDefault(LocalTime(14, 37)))
        assertEquals(50, vm.durationPickerDefault())
    }

    /** The last picker default the platform used to answer from inside a composable. */
    @Test
    fun `the date picker opens on the chosen date, or today`() = runTest(mainRule.dispatcher) {
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
    fun `a booking opened from a day prefills that date and still allows changing it`() = runTest(mainRule.dispatcher) {
        val thursday = LocalDate(2026, 10, 1)
        val vm = vm(day = thursday)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(thursday, vm.datePickerDefault(today = LocalDate(2026, 9, 24)))
        assertEquals(thursday, vm.state.current.form.date)
        assertFalse("the prefilled date is not an unsaved edit", vm.state.current.isDirty)
        assertNull("the start time is still the tutor's to choose", vm.state.current.form.timeFrom)

        vm.change(BookingIntent.Edit { it.copy(date = LocalDate(2026, 10, 5)) })
        advanceUntilIdle()
        assertEquals(LocalDate(2026, 10, 5), vm.state.current.form.date)
        assertEquals(LocalDate(2026, 10, 5), vm.datePickerDefault(today = LocalDate(2026, 9, 24)))
    }

    @Test
    fun `editing keeps the lesson date when a different schedule day is supplied`() = runTest(mainRule.dispatcher) {
        val lessonDay = LocalDate(2026, 10, 1)
        val (_, id) = seedLesson(lessonDay, LocalTime(14, 0))
        val vm = vm(id = id, day = lessonDay.plus(7, DateTimeUnit.DAY))
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(lessonDay, vm.state.current.form.date)
        assertEquals(lessonDay, vm.datePickerDefault())
    }

    @Test
    fun `editing opens the pickers on the lesson's own time, not the current one`() = runTest(mainRule.dispatcher) {
        val (studentId, subjectId) = seedStudentWithPricedSubject()
        val start = Instant.parse("2026-06-15T07:00:00Z")
        val id = env.fixtures.storedClass(
            studentId = studentId, subjectId = subjectId, startMillis = start.toEpochMilliseconds(),
            durationMinutes = 60, priceMinor = Money.ofMajor(30).minor, status = LessonStatus.SCHEDULED,
        )
        val vm = vm(id)
        advanceUntilIdle()

        val from = start.toLocalDateTime(zone).time
        assertEquals(from, vm.startPickerDefault(LocalTime(14, 37)))
        assertEquals(60, vm.durationPickerDefault())
    }

    @Test
    fun `a lesson that starts at 23 00 opens ready to save`() = runTest(mainRule.dispatcher) {
        val (_, id) = seedLesson(Clock.System.todayIn(zone).plus(3, DateTimeUnit.DAY), LocalTime(23, 0))
        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        val state = vm.state.current
        assertEquals(60, state.form.durationMinutes)
        assertEquals(LocalTime(0, 0), state.ending?.time)
        assertEquals(true, state.ending?.nextDay)
        assertTrue("a lesson already in the schedule is one the form can save", state.canSave)
    }
}
