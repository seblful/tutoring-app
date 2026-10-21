package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.schedule.CalendarLayout
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.valueOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The When section's week strip: which week it shows, and how busy its days are. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ClassEditWeekTest : ClassEditTestFixture() {

    // A Thursday: its week runs Monday 2 November to Sunday 8 November.
    private val thursday = LocalDate(2026, 11, 5)
    private val monday = LocalDate(2026, 11, 2)

    @Test
    fun `the strip shows the week of the day the booking was opened on`() = runTest(mainRule.dispatcher) {
        val vm = vm(day = thursday)
        observing(vm.week)
        advanceUntilIdle()

        assertEquals(CalendarLayout.week(monday), vm.week.value.days)
    }

    @Test
    fun `paging moves the strip a week without moving the lesson`() = runTest(mainRule.dispatcher) {
        val vm = vm(day = thursday)
        observing(vm.week, vm.state)
        advanceUntilIdle()

        vm.showWeek(1)
        advanceUntilIdle()

        assertEquals(monday.plus(7, DateTimeUnit.DAY), vm.week.value.days.first())
        assertEquals(thursday, vm.datePickerDefault())
    }

    @Test
    fun `each day of the strip counts its lessons`() = runTest(mainRule.dispatcher) {
        val maths = env.fixtures.subject("Математика")
        val maria = env.fixtures.pricedStudent("Мария", maths)
        env.fixtures.lesson(maths, listOf(maria), thursday.atTime(15, 30).toInstant(zone))
        env.fixtures.lesson(maths, listOf(maria), thursday.atTime(17, 0).toInstant(zone))
        val vm = vm(day = thursday)
        observing(vm.week)
        advanceUntilIdle()

        val loads = vm.week.value.loads.valueOrNull()!!
        assertEquals(2, loads[thursday]?.total)
        assertEquals(null, loads[monday])
    }
}
