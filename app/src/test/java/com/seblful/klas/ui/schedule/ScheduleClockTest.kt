package com.seblful.klas.ui.schedule

import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.testutil.MutableClock
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScheduleClockTest : ScheduleTestFixture() {

    @Test
    fun `the now mark follows the clock past a lesson`() = runTest(mainRule.dispatcher) {
        val subj = env.fixtures.subject("Математика")
        val sid = env.fixtures.pricedStudent("Аня", subj)
        val day = LocalDate(2026, 5, 18)
        val clock = MutableClock(day.atTime(8, 59).toInstant(zone), zone)
        env.fixtures.lesson(
            subjectId = subj,
            students = listOf(sid),
            start = day.atTime(9, 0).toInstant(zone),
        )

        val vm = vm(clock)
        observing(vm.content)
        observing(vm.nowMark)
        val minute = 1.minutes.inWholeMilliseconds
        advanceTimeBy(minute)

        assertEquals("before the lesson, the mark is above it", 0, vm.nowMark.value)

        clock.advanceBy(minute)
        advanceTimeBy(minute)

        assertEquals("once it has started, the mark is below it", 1, vm.nowMark.value)
    }

    @Test
    fun `a lesson reads as to come, then on now, then over and unmarked`() = runTest(mainRule.dispatcher) {
        val subj = env.fixtures.subject("Математика")
        val sid = env.fixtures.pricedStudent("Аня", subj)
        val day = LocalDate(2026, 5, 18)
        val clock = MutableClock(day.atTime(8, 59).toInstant(zone), zone)
        env.fixtures.lesson(
            subjectId = subj,
            students = listOf(sid),
            start = day.atTime(9, 0).toInstant(zone),
            minutes = 60,
        )

        val vm = vm(clock)
        observing(vm.content)
        observing(vm.phases)
        val minute = 1.minutes.inWholeMilliseconds
        advanceTimeBy(minute)
        val row = vm.dayContent.rows.single()
        val lessonId = row.session.lessonId
        assertEquals("not yet", LessonPhase.UPCOMING, vm.phases.value.of(row))

        clock.advanceBy(minute)
        advanceTimeBy(minute)
        assertEquals("on now", LessonPhase.IN_PROGRESS, vm.phases.value[lessonId])

        clock.advanceBy(60 * minute)
        advanceTimeBy(60 * minute)
        assertEquals("over, and unmarked rather than running", LessonPhase.OVERDUE, vm.phases.value[lessonId])
    }

    @Test
    fun `another day gets no now mark`() = runTest(mainRule.dispatcher) {
        val day = LocalDate(2026, 5, 18)
        val clock = MutableClock(day.atTime(12, 0).toInstant(zone), zone)

        val vm = vm(clock)
        observing(vm.content)
        observing(vm.nowMark)
        advanceTimeBy(1)
        assertEquals(0, vm.nowMark.value)

        vm.selectDate(day.plus(1, DateTimeUnit.DAY))
        advanceTimeBy(1)

        assertNull("tomorrow has no now", vm.nowMark.value)
    }

    /**
     * The screen used to read `LocalDate.now()` in its own body, evaluated per composition and never
     * again on its own — so an app left open overnight went on marking yesterday as today.
     *
     * `delay` is virtual under the test scheduler, so crossing midnight costs no wall-clock time.
     *
     * Deliberately **no** `advanceUntilIdle()` anywhere here: a clock has no last emission, so there is
     * always another `delay` queued and "until idle" never arrives — the test would hang rather than
     * fail. Every step below advances by a stated amount.
     */
    @Test
    fun `today rolls over at midnight`() = runTest(mainRule.dispatcher) {
        val start = LocalDate(2026, 7, 15)
        // 22:00 on the 15th, so midnight is two hours out.
        val clock = MutableClock(start.atTime(22, 0).toInstant(zone), zone)
        val twoHours = 2.hours.inWholeMilliseconds

        val vm = vm(clock)
        observing(vm.today)
        advanceTimeBy(1)
        assertEquals(start, vm.today.value)

        // One millisecond short of midnight. Both clocks move: the wall clock is what the date is read
        // from, the scheduler is what the sleep is waiting on.
        clock.advanceBy(twoHours - 2)
        advanceTimeBy(twoHours - 2)
        assertEquals("the moment before midnight is still today", start, vm.today.value)

        clock.advanceBy(2)
        advanceTimeBy(2)
        assertEquals("and then it is tomorrow", start.plus(1, DateTimeUnit.DAY), vm.today.value)
    }

    /** A device that slept through more than one night wakes up on the right day, not the next one. */
    @Test
    fun `today catches up when the phone slept for days`() = runTest(mainRule.dispatcher) {
        val start = LocalDate(2026, 7, 15)
        val clock = MutableClock(start.atTime(22, 0).toInstant(zone), zone)

        val vm = vm(clock)
        observing(vm.today)
        advanceTimeBy(1)
        assertEquals(start, vm.today.value)

        val threeDays = 3.days.inWholeMilliseconds
        clock.advanceBy(threeDays)
        advanceTimeBy(threeDays)

        assertEquals(
            "the date is re-read from the clock, not counted up one sleep at a time",
            start.plus(3, DateTimeUnit.DAY),
            vm.today.value,
        )
    }

    /**
     * Left on today overnight, the schedule is on today in the morning — the Day view opened on yesterday,
     * with no «now» line, because the selection was set once and only `today` rolled over. A day the tutor
     * browsed to is theirs and stays where they put it.
     */
    @Test
    fun `a selection left on today follows it past midnight, a browsed one stays`() = runTest(mainRule.dispatcher) {
        val start = LocalDate(2026, 7, 15)
        val clock = MutableClock(start.atTime(22, 0).toInstant(zone), zone)
        val tenHours = 10.hours.inWholeMilliseconds

        val vm = vm(clock)
        observing(vm.today)
        advanceTimeBy(1)

        clock.advanceBy(tenHours)
        advanceTimeBy(tenHours)
        assertEquals("on today, it moves with today", start.plus(1, DateTimeUnit.DAY), vm.selectedDate.value)

        val browsed = start.plus(5, DateTimeUnit.DAY)
        vm.selectDate(browsed)
        val day = 1.days.inWholeMilliseconds
        clock.advanceBy(day)
        advanceTimeBy(day)
        assertEquals("a day the tutor picked is not moved", browsed, vm.selectedDate.value)
    }
}
