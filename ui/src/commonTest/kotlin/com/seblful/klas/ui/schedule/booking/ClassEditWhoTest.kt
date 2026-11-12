package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The «Who» section of a new booking: its chips, and choosing a student or a group in one tap. */
@OptIn(ExperimentalCoroutinesApi::class)
class ClassEditWhoTest : ClassEditTestFixture() {

    private val lastWeek = env.clock.today().minus(7, DateTimeUnit.DAY)

    @Test
    fun `the chips put whoever was taught last first`() = runTest(main.dispatcher) {
        val maths = env.fixtures.subject()
        val anya = env.fixtures.pricedStudent("Аня", maths)
        val boris = env.fixtures.pricedStudent("Борис", maths)
        env.fixtures.lesson(maths, listOf(anya), lastWeek.atTime(9, 0).toInstant(zone))
        env.fixtures.lesson(maths, listOf(boris), lastWeek.plus(1, DateTimeUnit.DAY).atTime(9, 0).toInstant(zone))
        val vm = vm()
        observing(vm.state, vm.targetChoices)
        advanceUntilIdle()

        assertEquals(
            listOf(Assignee.Student(boris), Assignee.Student(anya)),
            vm.targetChoices.value.map { it.who },
        )
    }

    @Test
    fun `a group chip books the group without asking for the kind of lesson first`() = runTest(main.dispatcher) {
        val (groupId, _, _) = seedGroup()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.choose(Assignee.Group(groupId))
        advanceUntilIdle()

        val state = vm.state.current
        assertEquals(ClassTarget.GROUP, state.form.target)
        assertEquals(groupId, state.form.groupId)
        assertNull(state.form.studentId)
        assertEquals(groupRate, state.form.price.amount)
        assertTrue(state.isDirty)
    }

    @Test
    fun `a student chip after a group switches back and keeps the time`() = runTest(main.dispatcher) {
        val (groupId, ann, _) = seedGroup()
        val vm = vm()
        observing(vm.state)
        vm.choose(Assignee.Group(groupId))
        vm.change(BookingIntent.StartAt(LocalTime(16, 30)))
        advanceUntilIdle()

        vm.choose(Assignee.Student(ann))
        advanceUntilIdle()

        val form = vm.state.current.form
        assertEquals(ClassTarget.STUDENT, form.target)
        assertEquals(ann, form.studentId)
        assertNull(form.groupId)
        assertEquals(LocalTime(16, 30), form.timeFrom)
    }

    @Test
    fun `a student created from the menu is chosen even while a group was`() = runTest(main.dispatcher) {
        val (groupId, _, _) = seedGroup()
        val vm = vm()
        observing(vm.state)
        vm.choose(Assignee.Group(groupId))
        advanceUntilIdle()

        val created = env.fixtures.pricedStudent("Новенький", env.fixtures.subject("Физика"))
        vm.selectCreatedStudent(created)
        advanceUntilIdle()

        assertEquals(ClassTarget.STUDENT, vm.state.current.form.target)
        assertEquals(created, vm.state.current.form.studentId)
    }
}
