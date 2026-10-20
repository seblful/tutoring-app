package com.seblful.klas.ui.schedule

import com.seblful.klas.domain.schedule.CalendarLayout
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

/**
 * Where the schedule is pointed: the day picked, the month and week pages being browsed, and the view
 * showing them — with the rules for how each one moves.
 *
 * @property week the first day of the week the agenda shows — [weekStart]'s, not necessarily a Monday.
 * @property today the today [dayChanged] last saw. Held here rather than read from a clock, because a
 * night spent on another tab is still a night: the question is whether the selection was on the old
 * today, whenever the new one is noticed.
 */
data class SchedulePages(
    val selected: LocalDate,
    val month: YearMonth,
    val week: LocalDate,
    val mode: ScheduleViewMode,
    private val today: LocalDate,
    private val weekStart: DayOfWeek,
) {

    /** Picks [date], and points both calendar pages at it. */
    fun select(date: LocalDate): SchedulePages =
        copy(selected = date, month = date.yearMonth, week = CalendarLayout.startOfWeek(date, weekStart))

    /** Switches to [mode], on the pages that hold the selected day. */
    fun showing(mode: ScheduleViewMode): SchedulePages = copy(mode = mode).select(selected)

    fun previousMonth(): SchedulePages = copy(month = month.minus(1, DateTimeUnit.MONTH))

    fun nextMonth(): SchedulePages = copy(month = month.plus(1, DateTimeUnit.MONTH))

    fun previousWeek(): SchedulePages = copy(week = week.minus(DAYS_IN_WEEK, DateTimeUnit.DAY))

    fun nextWeek(): SchedulePages = copy(week = week.plus(DAYS_IN_WEEK, DateTimeUnit.DAY))

    /**
     * Today is now [date]. A selection still on the previous today follows it — without that, an app left
     * open overnight opened the day view on yesterday, with no «now» line. A day the tutor browsed to stays.
     */
    fun dayChanged(date: LocalDate): SchedulePages = when {
        date == today -> this
        selected == today -> select(date).copy(today = date)
        else -> copy(today = date)
    }

    companion object {
        private const val DAYS_IN_WEEK = CalendarLayout.DAYS_IN_WEEK.toLong()

        /** The schedule as it opens: the day view on [today], weeks starting on [weekStart]. */
        fun on(today: LocalDate, weekStart: DayOfWeek): SchedulePages = SchedulePages(
            selected = today,
            month = today.yearMonth,
            week = CalendarLayout.startOfWeek(today, weekStart),
            mode = ScheduleViewMode.DAY,
            today = today,
            weekStart = weekStart,
        )
    }
}
