package com.seblful.klas.ui.schedule

import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.navigation.Arrival
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleArrivalTest : ScheduleTestFixture() {

    /**
     * A tap in the tray is an arrival, and one arrival moves the schedule once. The screen hands it over on
     * every return — it leaves composition whenever a lesson or another tab covers it — and honouring it
     * each time sent the tutor back to the notification's day after every edit, for the rest of the process.
     */
    @Test
    fun `an arrival is honoured once - and a new one again`() = runTest(main.dispatcher) {
        val vm = vm()
        val today = env.clock.today()
        val tapped = Arrival<ScheduleTarget>(ScheduleTarget.Today)

        vm.selectDate(today.plus(3, DateTimeUnit.DAY))
        vm.arrive(tapped)
        assertEquals(today, vm.selectedDate.value)

        vm.selectDate(today.plus(3, DateTimeUnit.DAY))
        vm.arrive(tapped)
        assertEquals(
            today.plus(3, DateTimeUnit.DAY),
            vm.selectedDate.value,
            "the same tap, handed over again on return",
        )

        vm.arrive(Arrival(ScheduleTarget.Today))
        assertEquals(today, vm.selectedDate.value, "a second tap on the notification")

        vm.selectDate(today.plus(3, DateTimeUnit.DAY))
        vm.arrive(null)
        assertEquals(today.plus(3, DateTimeUnit.DAY), vm.selectedDate.value, "no arrival is nobody asking")
    }

    @Test
    fun `a day's arrival opens that day`() = runTest(main.dispatcher) {
        val vm = vm()
        val yesterday = env.clock.today().minus(1, DateTimeUnit.DAY)

        vm.arrive(Arrival(ScheduleTarget.Day(yesterday)))

        assertEquals(yesterday, vm.selectedDate.value)
    }

    /**
     * A reminder's tap opens its lesson the way tapping the card does — on the day it is on **now**, which
     * is not necessarily the day it was on when the reminder was posted — and the ask is spent once shown.
     */
    @Test
    fun `a reminder's arrival opens its lesson's sheet on the day it is on`() = runTest(main.dispatcher) {
        val subj = env.fixtures.subject("Математика")
        val ann = env.fixtures.pricedStudent("Аня", subj)
        val day = env.clock.today().plus(2, DateTimeUnit.DAY)
        val id = env.fixtures.lesson(
            subjectId = subj, students = listOf(ann), start = day.atTime(10, 0).toInstant(zone),
        ).single()
        val vm = vm()
        observing(vm.askedSheet)
        advanceUntilIdle()

        vm.arrive(Arrival(ScheduleTarget.Lesson(id)))
        advanceUntilIdle()

        assertEquals(day, vm.selectedDate.value)
        assertEquals(id, vm.askedSheet.value?.klass?.id)

        vm.sheetShown()
        advanceUntilIdle()
        assertNull(vm.askedSheet.value, "shown once, not again on the next page load")
    }

    @Test
    fun `a reminder for somebody the schedule is not showing lifts the filter`() = runTest(main.dispatcher) {
        val today = env.clock.today()
        val (ann, bob) = seedTwoStudentsOn(today)
        val bobs = env.classRepo.observeBetween(
            today.atStartOfDayIn(zone),
            today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone),
        ).first().single { it.studentId == bob }.id
        val vm = vm()
        observing(vm.filter)
        observing(vm.askedSheet)
        vm.setWho(ScheduleWho.Student(ann))
        advanceUntilIdle()

        vm.arrive(Arrival(ScheduleTarget.Lesson(bobs)))
        advanceUntilIdle()

        assertNull(vm.filter.value.who)
        assertEquals(bobs, vm.askedSheet.value?.klass?.id)
    }

    @Test
    fun `a reminder for a lesson since deleted says so`() = runTest(main.dispatcher) {
        val vm = vm()
        val seen = mutableListOf<ScheduleViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { seen += it } }

        vm.arrive(Arrival(ScheduleTarget.Lesson(404L)))
        advanceUntilIdle()

        assertEquals(listOf<ScheduleViewModel.Event>(ScheduleViewModel.Event.LESSON_GONE), seen)
    }
}
