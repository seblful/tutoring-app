package com.seblful.klas.ui.schedule

import com.seblful.klas.domain.schedule.ScheduleEmpty
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.valueOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScheduleEmptyTest : ScheduleTestFixture() {

    /**
     * The three cases, as a rule rather than as two copies in two composables.
     *
     * The day and month views weighed three of these and the week view two, so a tutor with nothing set
     * up met «Добавить ученика» on «День» and a dead end on «Неделя» — an empty list carrying no way out
     * of itself, which is the one thing `EmptyState` exists to prevent. Neither copy could be tested.
     */
    @Test
    fun `an empty schedule says which kind of empty it is`() {
        assertEquals(
            ScheduleEmpty.NEEDS_STUDENT,
            ScheduleEmpty.of(filtered = false, canAddClass = false),
        )
        assertEquals(ScheduleEmpty.QUIET, ScheduleEmpty.of(filtered = false, canAddClass = true))
        // A narrowed schedule is not a quiet one, and says so however much could be booked.
        assertEquals(ScheduleEmpty.FILTERED, ScheduleEmpty.of(filtered = true, canAddClass = true))
        assertEquals(
            "a filter outranks having nothing set up: the way out is the chip either way",
            ScheduleEmpty.FILTERED,
            ScheduleEmpty.of(filtered = true, canAddClass = false),
        )
    }

    @Test
    fun `an app with nothing set up offers the step that unblocks it`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.emptyReason)
        advanceUntilIdle()

        assertEquals(ScheduleEmpty.NEEDS_STUDENT, vm.emptyReason.value.valueOrNull())
    }

    @Test
    fun `a quiet day in a set-up app offers to book a lesson`() = runTest(mainRule.dispatcher) {
        val subject = env.fixtures.subject("Математика")
        env.fixtures.pricedStudent("Аня", subject)

        val vm = vm()
        observing(vm.emptyReason)
        advanceUntilIdle()

        assertEquals(ScheduleEmpty.QUIET, vm.emptyReason.value.valueOrNull())
    }
}
