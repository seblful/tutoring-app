package com.seblful.klas.ui.navigation

import com.seblful.klas.ui.schedule.ScheduleTarget
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TabArrivalsTest {
    private val navigator = KlasNavigator()
    private val arrivals = navigator.arrivals

    @Test
    fun `opening a lesson associates its request with the opened schedule entry`() {
        arrivals.openSchedule(ScheduleTarget.Lesson(7))

        assertEquals(Routes.Schedule, navigator.top.route)
        assertEquals(ScheduleTarget.Lesson(7), arrivals.forSchedule(navigator.top.id)?.target)
        assertNull(arrivals.forDebts(navigator.top.id))
    }

    @Test
    fun `a targeted arrival preserves an interrupted draft for Back`() {
        navigator.navigateToTab(TopDestination.PEOPLE)
        navigator.navigate(Routes.classEdit(0))
        val draft = navigator.top

        arrivals.openSchedule(ScheduleTarget.Day(LocalDate(2026, 6, 1)))
        assertNotNull(arrivals.forSchedule(navigator.top.id))
        navigator.goBack()

        assertSame(draft, navigator.top)
    }

    @Test
    fun `an older schedule below a draft cannot replay the new entry's request`() {
        arrivals.openSchedule(ScheduleTarget.Lesson(1))
        val original = navigator.top
        navigator.navigate(Routes.classEdit(0))
        val draft = navigator.top

        arrivals.openSchedule(ScheduleTarget.Lesson(2))
        val opened = navigator.top

        assertNotSame(original, opened)
        assertNull(arrivals.forSchedule(original.id))
        assertEquals(ScheduleTarget.Lesson(2), arrivals.forSchedule(opened.id)?.target)
        navigator.goBack()
        assertSame(draft, navigator.top)
        navigator.goBack()
        assertSame(original, navigator.top)
        assertNull(arrivals.forSchedule(original.id))
    }

    @Test
    fun `returning from an edit preserves the already consumed request`() {
        arrivals.openSchedule(ScheduleTarget.Lesson(7))
        val entry = navigator.top
        val arrival = arrivals.forSchedule(entry.id)
        val gate = ArrivalGate()
        assertEquals(ScheduleTarget.Lesson(7), gate.admit(arrival))

        navigator.navigate(Routes.classEdit(7))
        navigator.goBack()

        assertSame(entry, navigator.top)
        assertSame(arrival, arrivals.forSchedule(entry.id))
        assertNull(gate.admit(arrivals.forSchedule(entry.id)))
    }

    @Test
    fun `a second request for the same lesson is a new arrival`() {
        arrivals.openSchedule(ScheduleTarget.Lesson(7))
        val first = arrivals.forSchedule(navigator.top.id)

        arrivals.openSchedule(ScheduleTarget.Lesson(7))
        val second = arrivals.forSchedule(navigator.top.id)

        assertNotSame(first, second)
        assertEquals(first?.target, second?.target)
        val gate = ArrivalGate()
        assertNotNull(gate.admit(first))
        assertNotNull(gate.admit(second))
    }

    @Test
    fun `debts bind only to the opened reports and leave the draft intact`() {
        navigator.navigate(Routes.classEdit(0))
        val draft = navigator.top

        arrivals.openDebts()
        val entry = navigator.top

        assertEquals(Routes.Reports, entry.route)
        assertNotNull(arrivals.forDebts(entry.id))
        assertNull(arrivals.forDebts(draft.id))
        assertNull(arrivals.forSchedule(entry.id))
        navigator.goBack()
        assertSame(draft, navigator.top)
    }
}
