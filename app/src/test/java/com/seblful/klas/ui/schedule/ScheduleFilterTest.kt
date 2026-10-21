package com.seblful.klas.ui.schedule

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.schedule.ScheduleEmpty
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.valueOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScheduleFilterTest : ScheduleTestFixture() {

    @Test
    fun `the schedule shows everyone until it is narrowed`() = runTest(mainRule.dispatcher) {
        val today = env.clock.today()
        seedTwoStudentsOn(today)

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()

        assertNull(vm.studentFilter.value)
        assertEquals(2, vm.dayContent.rows.size)
    }

    @Test
    fun `narrowing to a student leaves only their lessons, and names them`() =
        runTest(mainRule.dispatcher) {
            val today = env.clock.today()
            val (ann, _) = seedTwoStudentsOn(today)

            val vm = vm()
            observing(vm.content)
            observing(vm.studentFilter)
            advanceUntilIdle()

            vm.setStudentFilter(ann)
            advanceUntilIdle()

            assertEquals("Аня", vm.studentFilter.value?.name)
            assertEquals(1, vm.dayContent.rows.size)
            assertEquals("Аня", vm.dayContent.rows.single().title)
        }

    @Test
    fun `clearing the filter brings everyone back`() = runTest(mainRule.dispatcher) {
        val today = env.clock.today()
        val (ann, _) = seedTwoStudentsOn(today)

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()
        vm.setStudentFilter(ann)
        advanceUntilIdle()

        vm.setStudentFilter(null)
        advanceUntilIdle()

        assertNull(vm.studentFilter.value)
        assertEquals(2, vm.dayContent.rows.size)
    }

    @Test
    fun `a group lesson the student is in stays a whole lesson`() = runTest(mainRule.dispatcher) {
        val today = env.clock.today()
        val subj = env.fixtures.subject("Математика")
        val ann = env.fixtures.pricedStudent("Аня", subj)
        val bob = env.fixtures.pricedStudent("Борис", subj)
        val cleo = env.fixtures.pricedStudent("Вера", subj)
        val group = env.fixtures.group(subjectId = subj, members = setOf(ann, bob, cleo))
        env.fixtures.lesson(
            subjectId = subj, students = listOf(ann, bob, cleo), groupId = group,
            start = today.atTime(9, 0).toInstant(zone),
        )

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()
        vm.setStudentFilter(ann)
        advanceUntilIdle()

        val row = vm.dayContent.rows.single()
        assertTrue("it is still the group's lesson", row.isGroup)
        assertEquals("with all three of its classes", 3, row.classes.size)
        assertEquals(3, row.members.size)
    }

    @Test
    fun `a group lesson the student is not in is left out entirely`() = runTest(mainRule.dispatcher) {
        val today = env.clock.today()
        val subj = env.fixtures.subject("Математика")
        val ann = env.fixtures.pricedStudent("Аня", subj)
        val bob = env.fixtures.pricedStudent("Борис", subj)
        val group = env.fixtures.group(subjectId = subj, members = setOf(bob))
        env.fixtures.lesson(
            subjectId = subj, students = listOf(bob), groupId = group,
            start = today.atTime(9, 0).toInstant(zone),
        )

        val vm = vm()
        observing(vm.content)
        advanceUntilIdle()
        vm.setStudentFilter(ann)
        advanceUntilIdle()

        assertTrue(vm.dayContent.rows.isEmpty())
    }

    @Test
    fun `the month's markers are narrowed with the list`() = runTest(mainRule.dispatcher) {
        val today = env.clock.today()
        val (ann, _) = seedTwoStudentsOn(today)

        val vm = vm()
        vm.setViewMode(ScheduleViewMode.MONTH)
        observing(vm.dayLoads)
        advanceUntilIdle()

        assertEquals(2, vm.dayLoads.current[today]?.counts?.get(LessonStatus.SCHEDULED))

        vm.setStudentFilter(ann)
        advanceUntilIdle()

        assertEquals(1, vm.dayLoads.current[today]?.counts?.get(LessonStatus.SCHEDULED))
    }

    /**
     * A filter naming somebody the database no longer holds resolves to *no filter at all*, rather than
     * to an empty schedule with no way out of it. Both the chip and the queries read this one value, so
     * they cannot disagree about whether narrowing is in effect.
     */
    @Test
    fun `a filter on a student who is not there shows everyone`() = runTest(mainRule.dispatcher) {
        val today = env.clock.today()
        seedTwoStudentsOn(today)

        val vm = vm()
        observing(vm.content)
        observing(vm.studentFilter)
        advanceUntilIdle()

        vm.setStudentFilter(9999L)
        advanceUntilIdle()

        assertNull(vm.studentFilter.value)
        assertEquals(2, vm.dayContent.rows.size)
    }

    @Test
    fun `an archived student the schedule is filtered by keeps their name`() =
        runTest(mainRule.dispatcher) {
            // The menu only offers active students, but one already being looked at must not lose the
            // chip that says so — the chip is the only way back to everyone else.
            val today = env.clock.today()
            val (ann, _) = seedTwoStudentsOn(today)

            val vm = vm()
            observing(vm.content)
            observing(vm.studentFilter)
            observing(vm.filterableStudents)
            advanceUntilIdle()
            vm.setStudentFilter(ann)
            advanceUntilIdle()

            env.studentRepo.setArchived(env.studentRepo.getById(ann)!!, archived = true)
            advanceUntilIdle()

            assertEquals("Аня", vm.studentFilter.value?.name)
            assertFalse(vm.filterableStudents.value.any { it.id == ann })
        }

    @Test
    fun `a narrowed schedule reports being narrowed, not quiet`() = runTest(mainRule.dispatcher) {
        val today = env.clock.today()
        val (ann, _) = seedTwoStudentsOn(today)

        val vm = vm()
        observing(vm.emptyReason)
        observing(vm.filterableStudents)
        advanceUntilIdle()
        assertEquals(ScheduleEmpty.QUIET, vm.emptyReason.value.valueOrNull())

        vm.setStudentFilter(ann)
        advanceUntilIdle()

        assertEquals(ScheduleEmpty.FILTERED, vm.emptyReason.value.valueOrNull())
    }
}
