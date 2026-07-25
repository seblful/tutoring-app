package com.seblful.repetit.domain

import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.TutoringClass
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Layout and aggregation for the schedule's month calendar. Pure, so the fiddly parts — how many
 * week rows a month needs, which neighbouring days pad them out, and how a day's classes collapse
 * into one cell marker — are unit-tested without a device.
 */
object CalendarMonth {

    const val DAYS_IN_WEEK = 7

    /** What one calendar cell shows about its day: how many classes, and in which statuses. */
    data class DayLoad(val total: Int, val statuses: Set<ClassStatus>) {
        /**
         * The status a single-dot cell should be tinted with. Scheduled wins because an upcoming
         * class is the actionable one; a day whose classes are all cancelled reads as cancelled.
         */
        val dominant: ClassStatus
            get() = when {
                ClassStatus.SCHEDULED in statuses -> ClassStatus.SCHEDULED
                ClassStatus.COMPLETED in statuses -> ClassStatus.COMPLETED
                else -> ClassStatus.CANCELLED
            }
    }

    /**
     * One month's worth of calendar cells: whole [weeks] of [DAYS_IN_WEEK] days each, padded with
     * the tail of the previous month and the head of the next so every row is full. The row count
     * follows the month (4–6), rather than being fixed at 6, so no page ends in a dead week.
     */
    data class Grid(val month: YearMonth, val weeks: List<List<LocalDate>>) {
        val firstDate: LocalDate get() = weeks.first().first()

        /** Exclusive end of the range the grid covers — the day after its last cell. */
        val endExclusive: LocalDate get() = weeks.last().last().plusDays(1)

        /** False for the padding cells that belong to the neighbouring months. */
        fun isInMonth(date: LocalDate): Boolean = YearMonth.from(date) == month
    }

    /**
     * Builds the calendar page for [month]. [firstDayOfWeek] defaults to Monday, which is the
     * Russian convention (and the app is `ru`-only), so weeks read Пн…Вс.
     */
    fun grid(month: YearMonth, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): Grid {
        val first = month.atDay(1)
        // How far back the grid must start for the 1st to land in its own column.
        val lead = Math.floorMod(first.dayOfWeek.value - firstDayOfWeek.value, DAYS_IN_WEEK)
        val start = first.minusDays(lead.toLong())
        val cells = lead + month.lengthOfMonth()
        val weeks = ((cells + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK)
        return Grid(
            month = month,
            weeks = (0 until weeks).map { week ->
                (0 until DAYS_IN_WEEK).map { day -> start.plusDays((week * DAYS_IN_WEEK + day).toLong()) }
            },
        )
    }

    /** The weekday headers for a grid built with [firstDayOfWeek], left to right. */
    fun weekdays(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): List<DayOfWeek> =
        (0 until DAYS_IN_WEEK).map { firstDayOfWeek.plus(it.toLong()) }

    /**
     * Collapses [classes] into one [DayLoad] per day they start on, in [zone]. Days without a class
     * are absent from the map, so a cell can just look itself up.
     */
    fun dayLoads(classes: List<TutoringClass>, zone: ZoneId): Map<LocalDate, DayLoad> =
        classes.groupBy { it.start.atZone(zone).toLocalDate() }
            .mapValues { (_, dayClasses) ->
                DayLoad(total = dayClasses.size, statuses = dayClasses.mapTo(mutableSetOf()) { it.status })
            }
}
