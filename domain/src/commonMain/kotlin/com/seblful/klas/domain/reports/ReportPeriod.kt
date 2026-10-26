package com.seblful.klas.domain.reports

import com.seblful.klas.domain.model.TutoringClass
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Instant

/**
 * The slice of time a report covers — one month, one year, or everything there is.
 *
 * Summing the whole database answers "how am I doing?" only for a tutor who has just started: after a
 * few years the total is dominated by history and no line on the screen can say whether this spring
 * earned more than last. A period makes every number on the screen mean the same thing at once — the
 * total, each breakdown and its percentages all read *within* it.
 *
 * The anchor is a [YearMonth] for both widths, so switching between them keeps where the tutor was
 * looking rather than jumping to today: «Март 2025» widened to a year is *2025*, not the year in
 * progress. [Kind.ALL] carries the anchor along untouched for the same reason — narrowing back from
 * it returns to the month that was last being read.
 *
 * Money owed is deliberately **not** a period quantity, so nothing here applies to it: a balance is
 * charges minus payments over a student's whole history, and clipping only the charges to a year
 * would print a debt nobody has (see `ReportsViewModel`).
 */
data class ReportPeriod(val kind: Kind, val anchor: YearMonth) {

    /** How wide a slice of time a report is showing. */
    enum class Kind { MONTH, YEAR, ALL }

    /** The first day covered; null for [Kind.ALL], which reaches back as far as the data goes. */
    val from: LocalDate? = when (kind) {
        Kind.MONTH -> anchor.firstDay
        Kind.YEAR -> LocalDate(anchor.year, 1, 1)
        Kind.ALL -> null
    }

    /** The first day *after* the period — exclusive, so a month ends where the next begins. */
    val until: LocalDate? = when (kind) {
        Kind.MONTH -> anchor.plus(1, DateTimeUnit.MONTH).firstDay
        Kind.YEAR -> LocalDate(anchor.year + 1, 1, 1)
        Kind.ALL -> null
    }

    /**
     * The days this period spans, or **null** for [Kind.ALL] — which has no bounds, the data being
     * what sets its ends.
     *
     * The same fact as [from] and [until] being non-null, in a shape the compiler can follow: a caller
     * that has to enumerate the period (drawing a bar per month, say) branches on this instead of
     * asserting `from!!` in the arm where [kind] happens to rule out all time. The two statements of
     * "this period is bounded" then cannot come apart.
     */
    val bounds: Bounds? = if (from != null && until != null) Bounds(from, until) else null

    /** A period's ends. [untilExclusive] is the first day *after* it, as [until] is. */
    data class Bounds(val from: LocalDate, val untilExclusive: LocalDate) {
        /** The last day actually inside the period — what names its final month. */
        val lastDay: LocalDate get() = untilExclusive.minus(1, DateTimeUnit.DAY)

        /** The classes taught inside these days, by when the lesson is — as [ReportPeriod.filter]. */
        fun filter(classes: List<TutoringClass>, zone: TimeZone): List<TutoringClass> =
            classes.filter {
                val date = it.start.toLocalDateTime(zone).date
                !(date < from) && date < untilExclusive
            }
    }

    /**
     * How many of this period's days have happened by [today], or null when the whole of it has — or none
     * of it, or it is all time, which has no days to count.
     *
     * What a comparison with the period before has to measure while this one is **still going**: the
     * screen opens on the year in progress, and on 24 September its income so far set against the whole
     * of the year before would show a tutor earning more every month «−25 %» with a falling arrow.
     */
    fun daysElapsed(today: LocalDate): Long? {
        val b = bounds ?: return null
        if (today < b.from || !(today < b.untilExclusive)) return null
        return b.from.daysUntil(today) + 1L
    }

    /** This period's first [days] days — never past its own end, so a short February stays February. */
    fun firstDays(days: Long): Bounds? =
        bounds?.let { Bounds(it.from, minOf(it.from.plus(days, DateTimeUnit.DAY), it.untilExclusive)) }

    /** Whether ‹ › mean anything here: all time has nowhere to step to. */
    val isNavigable: Boolean get() = kind != Kind.ALL

    /** The same width of period [by] steps later, or earlier when negative. All time never moves. */
    fun shifted(by: Int): ReportPeriod = when (kind) {
        Kind.MONTH -> copy(anchor = anchor.plus(by.toLong(), DateTimeUnit.MONTH))
        Kind.YEAR -> copy(anchor = anchor.plus(by.toLong(), DateTimeUnit.YEAR))
        Kind.ALL -> this
    }

    /** The same anchor read at another width — what picking a width does. */
    fun withKind(kind: Kind): ReportPeriod = copy(kind = kind)

    /** Whether [moment] falls inside the period, as the day it lands on in [zone]. */
    fun contains(moment: Instant, zone: TimeZone): Boolean {
        val date = moment.toLocalDateTime(zone).date
        val start = from
        val end = until
        return (start == null || !(date < start)) && (end == null || date < end)
    }

    /**
     * The classes taught inside the period — by when the lesson **is**, not when it was booked, so
     * correcting an old lesson's time moves its income to the month it actually happened in.
     */
    fun filter(classes: List<TutoringClass>, zone: TimeZone): List<TutoringClass> =
        classes.filter { contains(it.start, zone) }

    companion object {
        /**
         * What the reports screen opens on: the year in progress.
         *
         * A year is the width that is almost always worth reading — long enough to hold a season's
         * shape and short enough to be about now — and it is the only default that makes the screen's
         * first answer change as the tutor works, which is the point of having one.
         */
        fun currentYear(today: LocalDate): ReportPeriod = ReportPeriod(Kind.YEAR, today.yearMonth)
    }
}
