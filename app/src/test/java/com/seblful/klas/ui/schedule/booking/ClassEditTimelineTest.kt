package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.valueOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/** The When section's day: the lessons it holds, and moving this one through it a step or a day at a time. */
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

    @Test
    fun `the first step takes the first free time`() = runTest(mainRule.dispatcher) {
        seedMaria()
        val vm = vm(day = day)
        observing(vm.state)
        advanceUntilIdle()

        vm.step(later = true)
        advanceUntilIdle()

        assertEquals(LocalTime(9, 0), vm.state.current.form.timeFrom)
    }

    @Test
    fun `a step moves the lesson a quarter of an hour`() = runTest(mainRule.dispatcher) {
        seedMaria()
        val vm = vm(day = day)
        observing(vm.state)
        advanceUntilIdle()
        vm.startAt(at(13, 0))
        advanceUntilIdle()

        vm.step(later = true)
        advanceUntilIdle()
        assertEquals(LocalTime(13, 15), vm.state.current.form.timeFrom)

        vm.step(later = false)
        vm.step(later = false)
        advanceUntilIdle()
        assertEquals(LocalTime(12, 45), vm.state.current.form.timeFrom)
    }

    @Test
    fun `a step past a lesson lands on its far side`() = runTest(mainRule.dispatcher) {
        seedMaria()
        val vm = vm(day = day)
        observing(vm.state)
        advanceUntilIdle()
        // Back to back with Мария, who has 15:30–16:30.
        vm.startAt(at(14, 30))
        advanceUntilIdle()

        vm.step(later = true)
        advanceUntilIdle()

        assertEquals(LocalTime(16, 30), vm.state.current.form.timeFrom)
        assertNull(vm.state.current.conflict)
    }

    @Test
    fun `steps reach the whole day, not only the hours once drawn`() = runTest(mainRule.dispatcher) {
        val vm = vm(day = day)
        observing(vm.state)
        advanceUntilIdle()
        vm.startAt(at(8, 0))
        advanceUntilIdle()

        vm.step(later = false)
        advanceUntilIdle()
        assertEquals(LocalTime(7, 45), vm.state.current.form.timeFrom)

        vm.startAt(at(0, 10))
        advanceUntilIdle()
        assertNull("nothing is earlier than midnight", vm.state.current.stepped(later = false))
    }

    @Test
    fun `moving a day keeps the time`() = runTest(mainRule.dispatcher) {
        val vm = vm(day = day)
        observing(vm.state)
        advanceUntilIdle()
        vm.startAt(at(14, 0))
        advanceUntilIdle()

        vm.moveDay(1)
        advanceUntilIdle()

        val form = vm.state.current.form
        assertEquals(day.plus(1, DateTimeUnit.DAY), form.date)
        assertEquals(LocalTime(14, 0), form.timeFrom)
    }

    @Test
    fun `a day moved to is drawn with its lessons - never empty while they are read`() = runTest(mainRule.dispatcher) {
        seedMaria()
        val vm = vm(day = day.plus(-1, DateTimeUnit.DAY))
        val drawn = mutableListOf<ClassFormState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect { it.valueOrNull()?.let(drawn::add) }
        }
        advanceUntilIdle()
        assertTrue(drawn.last().timeline != null)

        vm.moveDay(1)
        advanceUntilIdle()

        val moved = drawn.filter { it.form.date == day }
        assertTrue("no frame of the new day without its lessons", moved.all { it.timeline != null })
        assertEquals(listOf("Мария"), moved.last().timeline!!.booked.map { it.who })
    }

    @Test
    fun `the first day picked is drawn with its lessons too`() = runTest(mainRule.dispatcher) {
        seedMaria()
        val vm = vm()
        val drawn = mutableListOf<ClassFormState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect { it.valueOrNull()?.let(drawn::add) }
        }
        advanceUntilIdle()

        vm.change(BookingIntent.Edit { it.copy(date = day) })
        advanceUntilIdle()

        val picked = drawn.filter { it.form.date == day }
        assertTrue("no frame of the day without its lessons", picked.all { it.timeline != null })
        assertEquals(listOf("Мария"), picked.last().timeline!!.booked.map { it.who })
    }

    @Test
    fun `a step pressed right after moving a day takes the new day's free time`() = runTest(mainRule.dispatcher) {
        val maths = env.fixtures.subject("Математика")
        env.fixtures.lesson(maths, listOf(env.fixtures.pricedStudent("Мария", maths)), at(9))
        val vm = vm(day = day.plus(-1, DateTimeUnit.DAY))
        observing(vm.state)
        advanceUntilIdle()

        vm.moveDay(1)
        vm.step(later = true)
        advanceUntilIdle()

        // 09:00 would be the day left behind's first free time; this one's lesson holds it.
        assertEquals(day, vm.state.current.form.date)
        assertEquals(LocalTime(10, 0), vm.state.current.form.timeFrom)
    }

    @Test
    fun `without a day the arrows start from today`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.moveDay(-1)
        advanceUntilIdle()

        assertEquals(env.clock.today().plus(-1, DateTimeUnit.DAY), vm.state.current.form.date)
    }

    @Test
    fun `the time dialog opens on the first free time until one is chosen`() = runTest(mainRule.dispatcher) {
        seedMaria()
        val vm = vm(day = day)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(LocalTime(9, 0), vm.timePickerDefault())

        vm.startAt(at(17, 5))
        advanceUntilIdle()
        assertEquals(LocalTime(17, 5), vm.timePickerDefault())
    }
}
