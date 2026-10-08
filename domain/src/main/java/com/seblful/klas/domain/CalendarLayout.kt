package com.seblful.klas.domain

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.TutoringClass
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Layout and aggregation behind the schedule's calendar views. Pure, so the fiddly parts — how many
 * week rows a month needs, which neighbouring days pad them out, where a week starts, and how a
 * day's classes collapse into its cell's markers — are unit-tested without a device.
 */
object CalendarLayout {

    const val DAYS_IN_WEEK = 7

    /**
     * How many of a day's lessons sit in one status — one tinted number in its calendar cell.
     *
     * Every lesson lands in one of the three, [ClassSession.status] having an answer for a group whose
     * members were corrected apart. There used to be a fourth, null bucket for that lesson, which is
     * how a cell came to print a count of something no class can be.
     */
    data class Tally(val status: LessonStatus, val count: Int)

    /** What one calendar cell shows about its day: its lessons, counted per status. */
    data class DayLoad(val counts: Map<LessonStatus, Int>) {

        val total: Int get() = counts.values.sum()

        /**
         * The numbers the cell prints, left to right. Ordered by [LessonStatus]'s own declaration
         * (scheduled → completed → cancelled), so the actionable count reads first, and statuses the
         * day has none of are left out rather than printed as a zero. There are only three statuses,
         * so a cell tops out at three numbers however busy the day is — no marker cap to apply, and
         * nothing about the day left unsaid.
         */
        val tallies: List<Tally>
            get() = LessonStatus.entries.mapNotNull { status ->
                counts[status]?.let { Tally(status, it) }
            }
    }

    /**
     * One month's worth of calendar cells: whole [weeks] of [DAYS_IN_WEEK] days each, padded with
     * the tail of the previous month and the head of the next so every row is full. The row count
     * follows the month (4–6), rather than being fixed at 6, so no page ends in a dead week.
     */
    data class MonthGrid(val month: YearMonth, val weeks: List<List<LocalDate>>) {
        val firstDate: LocalDate get() = weeks.first().first()

        /** Exclusive end of the range the grid covers — the day after its last cell. */
        val endExclusive: LocalDate get() = weeks.last().last().plusDays(1)

        /** False for the padding cells that belong to the neighbouring months. */
        fun isInMonth(date: LocalDate): Boolean = YearMonth.from(date) == month
    }

    /**
     * Builds the calendar page for [month].
     *
     * [firstDayOfWeek] defaults to Monday — the `ru`/`be` convention, and what a layout test wants to say
     * once at the top rather than at every call. **The app passes the phone's own answer**
     * (`com.seblful.klas.deviceFirstDayOfWeek`, injected as `di.FirstDayOfWeek`), so a tutor who moved
     * their week to Sunday in regional preferences gets Вс…Сб here and in the week strip.
     */
    fun monthGrid(month: YearMonth, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): MonthGrid {
        val first = month.atDay(1)
        val start = startOfWeek(first, firstDayOfWeek)
        // Cells the page must hold: the days padded in before the 1st, plus the month itself.
        val lead = ChronoUnit.DAYS.between(start, first).toInt()
        val rows = (lead + month.lengthOfMonth() + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK
        return MonthGrid(
            month = month,
            weeks = (0 until rows).map { week ->
                (0 until DAYS_IN_WEEK).map { day -> start.plusDays((week * DAYS_IN_WEEK + day).toLong()) }
            },
        )
    }

    /** The [DAYS_IN_WEEK] days of the week containing [date], in order. */
    fun week(date: LocalDate, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): List<LocalDate> {
        val start = startOfWeek(date, firstDayOfWeek)
        return (0 until DAYS_IN_WEEK).map { start.plusDays(it.toLong()) }
    }

    /** The [firstDayOfWeek] on or before [date] — the anchor a week is identified by. */
    fun startOfWeek(date: LocalDate, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): LocalDate =
        date.minusDays(Math.floorMod(date.dayOfWeek.value - firstDayOfWeek.value, DAYS_IN_WEEK).toLong())

    /**
     * Collapses [classes] into one [DayLoad] per day they start on, in [zone]. Days without a class
     * are absent from the map, so a cell can just look itself up.
     *
     * Counted in **lessons**, not classes: a group lesson is stored as one class per member, so a
     * group of two used to print «2» in its cell — two of something the tutor teaches once. The cell
     * has to agree with the list under it, which has always shown one row per lesson, so both collapse
     * through the same [GroupRules.sessions].
     */
    fun dayLoads(classes: List<TutoringClass>, zone: ZoneId): Map<LocalDate, DayLoad> =
        GroupRules.sessions(classes)
            .groupBy { it.start.atZone(zone).toLocalDate() }
            .mapValues { (_, daySessions) ->
                DayLoad(daySessions.groupingBy { it.status }.eachCount())
            }
}
