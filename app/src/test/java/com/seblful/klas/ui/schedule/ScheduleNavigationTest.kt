package com.seblful.klas.ui.schedule

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScheduleNavigationTest : ScheduleTestFixture() {

    @Test
    fun `stepping into the next week goes straight from one day to the next`() = runTest(mainRule.dispatcher) {
        val sid = env.fixtures.student()
        val subj = env.fixtures.subject()
        val sunday = LocalDate(2026, 7, 19)
        val monday = sunday.plus(1, DateTimeUnit.DAY)
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = startMillisOn(monday, LocalTime(9, 0)),
            durationMinutes = 60, status = LessonStatus.SCHEDULED,
        )
        val vm = vm()
        vm.selectDate(sunday)
        val seen = mutableListOf<Loadable<ScheduleContent.Day>>()
        backgroundScope.launch { vm.shownDay.collect { seen += it } }
        advanceUntilIdle()
        seen.clear()

        vm.goToNextDay()
        advanceUntilIdle()

        assertFalse("no Loading between two days that answer at once: $seen", Loadable.Loading in seen)
        val shown = vm.shownDay.current
        assertEquals("the day names the date it is", monday, shown.date)
        assertEquals(1, shown.rows.size)
    }

    @Test
    fun `day navigation moves the selected date and today resets it`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        val today = env.clock.today()

        vm.goToNextDay()
        assertEquals(today.plus(1, DateTimeUnit.DAY), vm.selectedDate.value)
        vm.goToPreviousDay()
        vm.goToPreviousDay()
        assertEquals(today.minus(1, DateTimeUnit.DAY), vm.selectedDate.value)
        vm.goToToday()
        assertEquals(today, vm.selectedDate.value)
    }

    @Test
    fun `selecting a day repoints the list and brings the calendar page with it`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        val target = LocalDate(2026, 11, 20)

        vm.selectDate(target)

        assertEquals(target, vm.selectedDate.value)
        assertEquals(YearMonth(2026, 11), vm.visibleMonth.value)
    }

    @Test
    fun `day navigation carries the calendar page across a month boundary`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        vm.selectDate(LocalDate(2026, 11, 30))

        vm.goToNextDay()

        assertEquals(LocalDate(2026, 12, 1), vm.selectedDate.value)
        assertEquals(YearMonth(2026, 12), vm.visibleMonth.value)
    }

    @Test
    fun `paging months browses without moving the selected day`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        vm.selectDate(LocalDate(2026, 11, 20))

        vm.goToNextMonth()
        assertEquals(YearMonth(2026, 12), vm.visibleMonth.value)
        vm.goToPreviousMonth()
        vm.goToPreviousMonth()
        assertEquals(YearMonth(2026, 10), vm.visibleMonth.value)
        assertEquals("browsing must not repoint the day list", LocalDate(2026, 11, 20), vm.selectedDate.value)
    }

    @Test
    fun `opening a calendar view snaps its page back to the selected day`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        vm.selectDate(LocalDate(2026, 11, 20))
        vm.goToNextMonth()
        vm.goToNextWeek()

        vm.setViewMode(ScheduleViewMode.MONTH)
        assertEquals(ScheduleViewMode.MONTH, vm.viewMode.value)
        assertEquals(YearMonth(2026, 11), vm.visibleMonth.value)

        vm.goToNextWeek()
        vm.setViewMode(ScheduleViewMode.WEEK)
        assertEquals(ScheduleViewMode.WEEK, vm.viewMode.value)
        assertEquals("the week containing 20 Nov 2026", LocalDate(2026, 11, 16), vm.visibleWeek.value)
    }

    @Test
    fun `paging weeks browses without moving the selected day`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        vm.selectDate(LocalDate(2026, 7, 15)) // a Wednesday
        assertEquals(LocalDate(2026, 7, 13), vm.visibleWeek.value)

        vm.goToNextWeek()
        assertEquals(LocalDate(2026, 7, 20), vm.visibleWeek.value)
        vm.goToPreviousWeek()
        vm.goToPreviousWeek()
        assertEquals(LocalDate(2026, 7, 6), vm.visibleWeek.value)
        assertEquals("browsing must not repoint the selection", LocalDate(2026, 7, 15), vm.selectedDate.value)
    }

    @Test
    fun `the week strip shows seven contiguous days from the visible Monday`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.weekDays)
        vm.selectDate(LocalDate(2026, 7, 15))
        advanceUntilIdle()

        val days = vm.weekDays.value
        assertEquals(7, days.size)
        assertEquals(LocalDate(2026, 7, 13), days.first())
        assertEquals(LocalDate(2026, 7, 19), days.last())
    }

    @Test
    fun `a week that starts on Sunday anchors every calendar page on Sunday`() = runTest(mainRule.dispatcher) {
        val vm = vm(weekStart = DayOfWeek.SUNDAY)
        observing(vm.weekDays)
        observing(vm.monthGrid)
        vm.selectDate(LocalDate(2026, 7, 15)) // a Wednesday
        advanceUntilIdle()

        assertEquals("the Sunday before, not the Monday", LocalDate(2026, 7, 12), vm.visibleWeek.value)
        assertEquals(LocalDate(2026, 7, 12), vm.weekDays.value.first())
        assertEquals(LocalDate(2026, 7, 18), vm.weekDays.value.last())
        assertEquals(DayOfWeek.SUNDAY, vm.monthGrid.value.firstDate.dayOfWeek)
    }

    @Test
    fun `the day view loads no markers at all`() = runTest(mainRule.dispatcher) {
        val sid = env.fixtures.student(name = "Аня")
        val subj = env.fixtures.subject(name = "Математика")
        val day = LocalDate(2026, 7, 15)
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = startMillisOn(day, LocalTime(9, 0)),
            durationMinutes = 60, status = LessonStatus.SCHEDULED,
        )

        val vm = vm()
        assertEquals(Loadable.Loading, vm.dayLoads.value)

        observing(vm.dayLoads)
        vm.selectDate(day)
        advanceUntilIdle()
        assertEquals(
            "the day view draws no calendar, so it queries none — and claims none",
            Loadable.Loading,
            vm.dayLoads.value,
        )

        vm.setViewMode(ScheduleViewMode.WEEK)
        advanceUntilIdle()
        assertEquals(1, vm.dayLoads.current.getValue(day).total)
    }

    @Test
    fun `the calendar grid follows the visible month`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.monthGrid)
        vm.selectDate(LocalDate(2026, 7, 15))
        advanceUntilIdle()

        assertEquals(YearMonth(2026, 7), vm.monthGrid.value.month)
        assertTrue(LocalDate(2026, 7, 15) in vm.monthGrid.value.weeks.flatten())
    }

    @Test
    fun `day markers cover the whole page, padding days included`() = runTest(mainRule.dispatcher) {
        val sid = env.fixtures.student(name = "Аня")
        val subj = env.fixtures.subject(name = "Математика")
        val padding = LocalDate(2026, 6, 30)
        val inMonth = LocalDate(2026, 7, 15)
        val otherMonth = LocalDate(2026, 9, 1)
        listOf(padding to LocalTime(9, 0), inMonth to LocalTime(9, 0), inMonth to LocalTime(15, 0))
            .forEach { (day, time) ->
                env.fixtures.storedClass(
                    studentId = sid, subjectId = subj, startMillis = startMillisOn(day, time),
                    durationMinutes = 60, status = LessonStatus.SCHEDULED,
                )
            }
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = startMillisOn(otherMonth, LocalTime(9, 0)),
            durationMinutes = 60, status = LessonStatus.CANCELLED,
        )

        val vm = vm()
        observing(vm.dayLoads)
        vm.setViewMode(ScheduleViewMode.MONTH)
        vm.selectDate(LocalDate(2026, 7, 1))
        advanceUntilIdle()

        val loads = vm.dayLoads.current
        assertEquals(2, loads.getValue(inMonth).total)
        assertEquals("padding cells are marked too", 1, loads.getValue(padding).total)
        assertFalse("days outside the page are not loaded", otherMonth in loads)
    }

    @Test
    fun `paging to another month reloads its markers`() = runTest(mainRule.dispatcher) {
        val sid = env.fixtures.student(name = "Аня")
        val subj = env.fixtures.subject(name = "Математика")
        val august = LocalDate(2026, 8, 12)
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = startMillisOn(august, LocalTime(9, 0)),
            durationMinutes = 60, status = LessonStatus.COMPLETED,
        )

        val vm = vm()
        observing(vm.dayLoads)
        vm.setViewMode(ScheduleViewMode.MONTH)
        vm.selectDate(LocalDate(2026, 7, 1))
        advanceUntilIdle()
        assertFalse(august in vm.dayLoads.current)

        vm.goToNextMonth()
        advanceUntilIdle()

        assertEquals(1, vm.dayLoads.current.getValue(august).total)
        assertEquals(
            mapOf(LessonStatus.COMPLETED to 1),
            vm.dayLoads.current.getValue(august).counts,
        )
    }
}
