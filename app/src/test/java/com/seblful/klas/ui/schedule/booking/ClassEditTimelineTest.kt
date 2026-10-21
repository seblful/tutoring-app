package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The When section's day timeline: what it draws, and starting a lesson from a tap on it. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ClassEditTimelineTest : ClassEditTestFixture() {

    private val day = env.clock.today().plus(3, DateTimeUnit.DAY)

    private fun at(hour: Int, minute: Int = 0) = day.atTime(hour, minute).toInstant(zone)

    private suspend fun seedMaria(): Long {
        val maths = env.fixtures.subject("Математика")
        val maria = env.fixtures.pricedStudent("Мария", maths)
        return env.fixtures.lesson(maths, listOf(maria), at(15, 30)).single()
    }

    @Test
    fun `without a date there is no timeline`() = runTest(mainRule.dispatcher) {
        seedMaria()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertNull(vm.state.current.timeline)
    }

    @Test
    fun `the timeline draws the day's lessons by who they are for`() = runTest(mainRule.dispatcher) {
        seedMaria()
        val maths = env.fixtures.subject("Физика")
        val other = env.fixtures.pricedStudent("Пётр", maths)
        env.fixtures.lesson(maths, listOf(other), day.plus(1, DateTimeUnit.DAY).atTime(15, 30).toInstant(zone))
        val vm = vm(day = day)
        observing(vm.state)
        advanceUntilIdle()

        val timeline = vm.state.current.timeline!!
        assertEquals(listOf("Мария"), timeline.booked.map { it.who })
        assertEquals(ClassMode.IN_PERSON, timeline.booked.single().mode)
        assertEquals(at(9), timeline.start)
        assertEquals(at(23), timeline.end)
    }

    @Test
    fun `a lesson being edited is not drawn among the others`() = runTest(mainRule.dispatcher) {
        val id = seedMaria()
        val vm = vm(id = id)
        observing(vm.state)
        advanceUntilIdle()

        val state = vm.state.current
        assertTrue(state.timeline!!.booked.isEmpty())
        assertEquals(at(15, 30), state.slot?.start)
    }

    @Test
    fun `a start chosen on the timeline is the lesson's time`() = runTest(mainRule.dispatcher) {
        seedMaria()
        val vm = vm(day = day)
        observing(vm.state)
        advanceUntilIdle()

        // An hour ending as Мария starts at 15:30.
        vm.startAt(at(14, 30))
        advanceUntilIdle()

        val form = vm.state.current.form
        assertEquals(LocalTime(14, 30), form.timeFrom)
        assertEquals(60, form.durationMinutes)
        assertNull(vm.state.current.conflict)
    }

    @Test
    fun `an early lesson draws the day from its hour`() = runTest(mainRule.dispatcher) {
        val maths = env.fixtures.subject("Математика")
        val maria = env.fixtures.pricedStudent("Мария", maths)
        env.fixtures.lesson(maths, listOf(maria), at(7, 30))
        val vm = vm(day = day)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(at(7), vm.state.current.timeline!!.start)
    }

    @Test
    fun `before a start is picked the timeline offers the first free time`() = runTest(mainRule.dispatcher) {
        seedMaria()
        val vm = vm(day = day)
        observing(vm.state)
        advanceUntilIdle()

        // The day is drawn from 9:00 and this booking is three days off, so the first hour is free.
        assertEquals(at(9), vm.state.current.suggestion?.start)
        assertNull("nothing is booked by the offer", vm.state.current.form.timeFrom)

        vm.startAt(at(16, 40))
        advanceUntilIdle()

        assertNull("a start picked replaces the offer", vm.state.current.suggestion)
    }
}
