package com.seblful.repetit.domain

import com.seblful.repetit.domain.model.TutoringClass
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The slice of time a report covers — one month, one year, or everything there is.
 *
 * Reports used to sum the whole database, which answers "how am I doing?" only for a tutor who has
 * just started: after a few years the total is dominated by history and no line on the screen can
 * say whether this spring earned more than last. A period makes every number on the screen mean the
 * same thing at once — the total, each breakdown and its percentages all read *within* it.
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
        Kind.MONTH -> anchor.atDay(1)
        Kind.YEAR -> LocalDate.of(anchor.year, 1, 1)
        Kind.ALL -> null
    }

    /** The first day *after* the period — exclusive, so a month ends where the next begins. */
    val until: LocalDate? = when (kind) {
        Kind.MONTH -> anchor.plusMonths(1).atDay(1)
        Kind.YEAR -> LocalDate.of(anchor.year + 1, 1, 1)
        Kind.ALL -> null
    }

    /** Whether ‹ › mean anything here: all time has nowhere to step to. */
    val isNavigable: Boolean get() = kind != Kind.ALL

    /** The same width of period [by] steps later, or earlier when negative. All time never moves. */
    fun shifted(by: Int): ReportPeriod = when (kind) {
        Kind.MONTH -> copy(anchor = anchor.plusMonths(by.toLong()))
        Kind.YEAR -> copy(anchor = anchor.plusYears(by.toLong()))
        Kind.ALL -> this
    }

    /** The same anchor read at another width — what picking a width does. */
    fun withKind(kind: Kind): ReportPeriod = copy(kind = kind)

    /** Whether [moment] falls inside the period, as the day it lands on in [zone]. */
    fun contains(moment: Instant, zone: ZoneId): Boolean {
        val date = moment.atZone(zone).toLocalDate()
        val start = from
        val end = until
        return (start == null || !date.isBefore(start)) && (end == null || date.isBefore(end))
    }

    /**
     * The classes taught inside the period — by when the lesson **is**, not when it was booked, so
     * correcting an old lesson's time moves its income to the month it actually happened in.
     */
    fun filter(classes: List<TutoringClass>, zone: ZoneId): List<TutoringClass> =
        classes.filter { contains(it.start, zone) }

    companion object {
        /**
         * What the reports screen opens on: the year in progress.
         *
         * A year is the width that is almost always worth reading — long enough to hold a season's
         * shape and short enough to be about now — and it is the only default that makes the screen's
         * first answer change as the tutor works, which is the point of having one.
         */
        fun currentYear(today: LocalDate): ReportPeriod = ReportPeriod(Kind.YEAR, YearMonth.from(today))
    }
}
