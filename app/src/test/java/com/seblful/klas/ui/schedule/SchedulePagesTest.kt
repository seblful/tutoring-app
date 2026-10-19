package com.seblful.klas.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * How the schedule's day and pages move, without a view model. `ScheduleViewModelTest` still covers the
 * same behaviour through the screen's flows; these pin the rules where they now live.
 */
class SchedulePagesTest {

    /** A Thursday, so a Monday week and a Sunday week start on different days. */
    private val thursday = LocalDate.of(2026, 10, 15)

    private val pages = SchedulePages.on(thursday, DayOfWeek.MONDAY)

    @Test
    fun `the schedule opens on today's day view, with both pages holding it`() {
        assertEquals(thursday, pages.selected)
        assertEquals(YearMonth.of(2026, 10), pages.month)
        assertEquals(LocalDate.of(2026, 10, 12), pages.week)
        assertEquals(ScheduleViewMode.DAY, pages.mode)
    }

    @Test
    fun `the week page starts on the phone's first day of the week`() {
        assertEquals(LocalDate.of(2026, 10, 11), SchedulePages.on(thursday, DayOfWeek.SUNDAY).week)
    }

    @Test
    fun `browsing a page leaves the selected day alone`() {
        val browsed = pages.nextMonth().nextMonth().previousWeek()

        assertEquals(thursday, browsed.selected)
        assertEquals(YearMonth.of(2026, 12), browsed.month)
        assertEquals(LocalDate.of(2026, 10, 5), browsed.week)
    }

    @Test
    fun `picking a day brings both pages to it`() {
        val picked = pages.select(LocalDate.of(2027, 1, 1))

        assertEquals(LocalDate.of(2027, 1, 1), picked.selected)
        assertEquals(YearMonth.of(2027, 1), picked.month)
        assertEquals(LocalDate.of(2026, 12, 28), picked.week)
    }

    @Test
    fun `switching view snaps the pages back to the selected day`() {
        val month = pages.nextMonth().nextWeek().showing(ScheduleViewMode.MONTH)

        assertEquals(ScheduleViewMode.MONTH, month.mode)
        assertEquals(YearMonth.of(2026, 10), month.month)
        assertEquals(LocalDate.of(2026, 10, 12), month.week)
    }

    @Test
    fun `a selection left on today follows it past midnight`() {
        val friday = thursday.plusDays(1)

        assertEquals(friday, pages.dayChanged(friday).selected)
    }

    @Test
    fun `following today across a week boundary brings the pages along`() {
        val sunday = LocalDate.of(2026, 10, 18)
        val monday = sunday.plusDays(1)

        val next = SchedulePages.on(sunday, DayOfWeek.MONDAY).dayChanged(monday)

        assertEquals(monday, next.week)
    }

    @Test
    fun `a day the tutor browsed to stays put when today moves on`() {
        val browsed = pages.select(thursday.plusDays(5))

        assertEquals(thursday.plusDays(5), browsed.dayChanged(thursday.plusDays(1)).selected)
    }

    /**
     * A selection moved off today and then back onto the *new* today follows it from then on — the
     * comparison is with the today last seen, not the one the schedule opened on.
     */
    @Test
    fun `following is judged against the today last seen`() {
        val friday = thursday.plusDays(1)
        val backOnToday = pages.select(thursday.minusDays(3)).dayChanged(friday).select(friday)

        assertEquals(friday.plusDays(1), backOnToday.dayChanged(friday.plusDays(1)).selected)
    }

    @Test
    fun `the same today again changes nothing`() {
        assertSame(pages, pages.dayChanged(thursday))
    }
}
