package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Money
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
import kotlinx.datetime.todayIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Clock

/** The quick-book sheet's half of the booking form: its chips, filling from the last lesson, «All fields». */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QuickBookTest : ClassEditTestFixture() {

    private val today = Clock.System.todayIn(zone)
    private val lastWeek = today.minus(7, DateTimeUnit.DAY)
    private val nextWeek = today.plus(7, DateTimeUnit.DAY)

    @Test
    fun `choosing a student fills the booking from their last lesson, at today's rate`() =
        runTest(mainRule.dispatcher) {
            val maths = env.fixtures.subject("Математика")
            val physics = env.fixtures.subject("Физика")
            val anya = env.fixtures.pricedStudent("Аня", maths, rate = Money.ofMajor(30))
            env.enrollmentRepo.enroll(anya, physics, Money.ofMajor(35))
            env.fixtures.lesson(
                subjectId = physics,
                students = listOf(anya),
                start = lastWeek.atTime(16, 30).toInstant(zone),
                minutes = 90,
                rate = Money.ofMajor(25),
                mode = ClassMode.REMOTE,
            )
            env.fixtures.student("Борис")
            val vm = vm(day = nextWeek)
            observing(vm.state)
            advanceUntilIdle()

            vm.choose(Assignee.Student(anya))
            advanceUntilIdle()

            val state = vm.state.current
            assertEquals(LocalTime(16, 30), state.form.timeFrom)
            assertEquals(90, state.form.durationMinutes)
            assertEquals(physics, state.form.subjectId)
            assertEquals(ClassMode.REMOTE, state.form.mode)
            assertEquals("the current rate, not the old lesson's", Money.ofMajor(35), state.form.price.amount)
            assertEquals(lastWeek, state.filledFrom)
            assertEquals(nextWeek, state.form.date)
            assertTrue(state.canSave)
        }

    @Test
    fun `choosing a group takes its last lesson's hour and the group's own rate and place`() =
        runTest(mainRule.dispatcher) {
            val (groupId, ann, bob) = seedGroup()
            val geometry = env.fixtures.subject("Геометрия")
            env.fixtures.lesson(
                subjectId = geometry,
                students = listOf(ann, bob),
                start = lastWeek.atTime(18, 0).toInstant(zone),
                minutes = 120,
                groupId = groupId,
            )
            val vm = vm(day = nextWeek)
            observing(vm.state)

            vm.choose(Assignee.Group(groupId))
            advanceUntilIdle()

            val state = vm.state.current
            assertEquals(ClassTarget.GROUP, state.form.target)
            assertEquals(LocalTime(18, 0), state.form.timeFrom)
            assertEquals(120, state.form.durationMinutes)
            assertEquals(groupRate, state.form.price.amount)
            assertEquals(ClassMode.REMOTE, state.form.mode)
        }

    @Test
    fun `editing a lesson never fills it from another one`() = runTest(mainRule.dispatcher) {
        val maths = env.fixtures.subject()
        val anya = env.fixtures.pricedStudent("Аня", maths)
        val boris = env.fixtures.pricedStudent("Борис", maths)
        env.fixtures.lesson(maths, listOf(boris), lastWeek.atTime(9, 0).toInstant(zone))
        val id = env.fixtures.lesson(maths, listOf(anya), nextWeek.atTime(15, 0).toInstant(zone)).single()
        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        vm.change(BookingIntent.ChooseStudent(boris))
        advanceUntilIdle()

        assertEquals(LocalTime(15, 0), vm.state.current.form.timeFrom)
        assertNull(vm.state.current.filledFrom)
    }

    @Test
    fun `the chips put whoever was taught last first`() = runTest(mainRule.dispatcher) {
        val maths = env.fixtures.subject()
        val anya = env.fixtures.pricedStudent("Аня", maths)
        val boris = env.fixtures.pricedStudent("Борис", maths)
        env.fixtures.lesson(maths, listOf(anya), lastWeek.atTime(9, 0).toInstant(zone))
        env.fixtures.lesson(maths, listOf(boris), lastWeek.plus(1, DateTimeUnit.DAY).atTime(9, 0).toInstant(zone))
        val vm = vm(day = nextWeek)
        observing(vm.state, vm.quickTargets)
        advanceUntilIdle()

        assertEquals(
            listOf(Assignee.Student(boris), Assignee.Student(anya)),
            vm.quickTargets.value.map { it.who },
        )
    }

    @Test
    fun `all fields hands the full form the booking as it stands`() = runTest(mainRule.dispatcher) {
        val maths = env.fixtures.subject()
        val anya = env.fixtures.pricedStudent("Аня", maths, rate = Money.ofMajor(45))
        env.fixtures.pricedStudent("Борис", maths)
        val sheet = vm(day = nextWeek)
        observing(sheet.state)
        sheet.choose(Assignee.Student(anya))
        sheet.change(BookingIntent.StartAt(LocalTime(11, 0)))
        advanceUntilIdle()

        val form = vm(draft = sheet.draft())
        observing(form.state)
        advanceUntilIdle()

        assertEquals(sheet.state.current.form, form.state.current.form)
        assertTrue("the tutor's choices are still unsaved", form.state.current.isDirty)
        assertTrue(form.isNew)
    }

    @Test
    fun `a sheet opened with nothing chosen is not unsaved`() = runTest(mainRule.dispatcher) {
        env.fixtures.pricedStudent("Аня", env.fixtures.subject())
        env.fixtures.student("Борис")
        val vm = vm(day = nextWeek)
        observing(vm.state)
        advanceUntilIdle()

        assertFalse(vm.state.current.isDirty)
        assertNull(vm.state.current.form.studentId)
    }
}
