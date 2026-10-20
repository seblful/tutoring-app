package com.seblful.klas.ui.schedule

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SchedulePagesTest {

    private val thursday = LocalDate(2026, 10, 15)

    private val pages = SchedulePages.on(thursday, DayOfWeek.MONDAY)

    @Test
    fun `the schedule opens on today's day view, with both pages holding it`() {
        assertEquals(thursday, pages.selected)
        assertEquals(YearMonth(2026, 10), pages.month)
        assertEquals(LocalDate(2026, 10, 12), pages.week)
        assertEquals(ScheduleViewMode.DAY, pages.mode)
    }

    @Test
    fun `the week page starts on the phone's first day of the week`() {
        assertEquals(LocalDate(2026, 10, 11), SchedulePages.on(thursday, DayOfWeek.SUNDAY).week)
    }

    @Test
    fun `browsing a page leaves the selected day alone`() {
        val browsed = pages.nextMonth().nextMonth().previousWeek()

        assertEquals(thursday, browsed.selected)
        assertEquals(YearMonth(2026, 12), browsed.month)
        assertEquals(LocalDate(2026, 10, 5), browsed.week)
    }

    @Test
    fun `picking a day brings both pages to it`() {
        val picked = pages.select(LocalDate(2027, 1, 1))

        assertEquals(LocalDate(2027, 1, 1), picked.selected)
        assertEquals(YearMonth(2027, 1), picked.month)
        assertEquals(LocalDate(2026, 12, 28), picked.week)
    }

    @Test
    fun `switching view snaps the pages back to the selected day`() {
        val month = pages.nextMonth().nextWeek().showing(ScheduleViewMode.MONTH)

        assertEquals(ScheduleViewMode.MONTH, month.mode)
        assertEquals(YearMonth(2026, 10), month.month)
        assertEquals(LocalDate(2026, 10, 12), month.week)
    }

    @Test
    fun `a selection left on today follows it past midnight`() {
        val friday = thursday.plus(1, DateTimeUnit.DAY)

        assertEquals(friday, pages.dayChanged(friday).selected)
    }

    @Test
    fun `following today across a week boundary brings the pages along`() {
        val sunday = LocalDate(2026, 10, 18)
        val monday = sunday.plus(1, DateTimeUnit.DAY)

        val next = SchedulePages.on(sunday, DayOfWeek.MONDAY).dayChanged(monday)

        assertEquals(monday, next.week)
    }

    @Test
    fun `a day the tutor browsed to stays put when today moves on`() {
        val browsed = pages.select(thursday.plus(5, DateTimeUnit.DAY))

        val friday = thursday.plus(1, DateTimeUnit.DAY)

        assertEquals(thursday.plus(5, DateTimeUnit.DAY), browsed.dayChanged(friday).selected)
    }

    @Test
    fun `following is judged against the today last seen`() {
        val friday = thursday.plus(1, DateTimeUnit.DAY)
        val backOnToday = pages.select(thursday.minus(3, DateTimeUnit.DAY)).dayChanged(friday).select(friday)

        val saturday = friday.plus(1, DateTimeUnit.DAY)

        assertEquals(saturday, backOnToday.dayChanged(saturday).selected)
    }

    @Test
    fun `the same today again changes nothing`() {
        assertSame(pages, pages.dayChanged(thursday))
    }
}
