package com.seblful.repetit.domain

import com.seblful.repetit.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ReportPeriodTest {

    private val zone = ZoneId.of("Europe/Minsk")
    private val may2026 = YearMonth.of(2026, 5)

    private fun klass(start: String) = TutoringClass(
        studentId = 1, subjectId = 1, start = Instant.parse(start), durationMinutes = 60,
    )

    @Test
    fun `a month runs from its first day to the first day of the next`() {
        val period = ReportPeriod(ReportPeriod.Kind.MONTH, may2026)
        assertEquals(LocalDate.of(2026, 5, 1), period.from)
        assertEquals(LocalDate.of(2026, 6, 1), period.until)
    }

    @Test
    fun `a year runs from January to the next January, whatever month it is anchored on`() {
        val period = ReportPeriod(ReportPeriod.Kind.YEAR, may2026)
        assertEquals(LocalDate.of(2026, 1, 1), period.from)
        assertEquals(LocalDate.of(2027, 1, 1), period.until)
    }

    @Test
    fun `all time is bounded at neither end`() {
        val period = ReportPeriod(ReportPeriod.Kind.ALL, may2026)
        assertNull(period.from)
        assertNull(period.until)
        assertFalse("all time has nowhere to step to", period.isNavigable)
    }

    @Test
    fun `the last moment of a month is inside it and the first of the next is not`() {
        val may = ReportPeriod(ReportPeriod.Kind.MONTH, may2026)
        // 23:59 local on the 31st is still May; the next minute is June.
        assertTrue(may.contains(Instant.parse("2026-05-31T20:59:00Z"), zone))
        assertFalse(may.contains(Instant.parse("2026-05-31T21:00:00Z"), zone))
        // 20:30 UTC on 30 April is 23:30 local — still April, so still out.
        assertFalse(may.contains(Instant.parse("2026-04-30T20:30:00Z"), zone))
    }

    /** The boundary is the local day, not UTC: a late evening lesson belongs to the day it was taught. */
    @Test
    fun `a lesson late on the last evening of the year counts in that year`() {
        val year = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth.of(2026, 1))
        // 22:00 UTC on 31 December is already 1 January in Minsk (UTC+3), so it is the *next* year's.
        assertFalse(year.contains(Instant.parse("2026-12-31T22:00:00Z"), zone))
        assertTrue(year.contains(Instant.parse("2026-12-31T20:00:00Z"), zone))
    }

    @Test
    fun `all time contains everything`() {
        val all = ReportPeriod(ReportPeriod.Kind.ALL, may2026)
        assertTrue(all.contains(Instant.EPOCH, zone))
        assertTrue(all.contains(Instant.parse("2099-01-01T00:00:00Z"), zone))
    }

    @Test
    fun `stepping moves by the period's own width`() {
        assertEquals(
            YearMonth.of(2026, 4),
            ReportPeriod(ReportPeriod.Kind.MONTH, may2026).shifted(-1).anchor,
        )
        assertEquals(
            YearMonth.of(2027, 5),
            ReportPeriod(ReportPeriod.Kind.YEAR, may2026).shifted(1).anchor,
        )
    }

    /** Stepping "all time" is a no-op rather than a silent move of the anchor nobody can see. */
    @Test
    fun `all time never steps anywhere`() {
        val all = ReportPeriod(ReportPeriod.Kind.ALL, may2026)
        assertEquals(all, all.shifted(-3))
        assertEquals(all, all.shifted(7))
    }

    /** Widening keeps where the tutor was reading; it does not jump them back to today. */
    @Test
    fun `changing width keeps the anchor`() {
        val march2025 = ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth.of(2025, 3))
        val widened = march2025.withKind(ReportPeriod.Kind.YEAR)
        assertEquals(2025, widened.anchor.year)
        assertEquals(LocalDate.of(2025, 1, 1), widened.from)
        // ...and narrowing again returns to the very month it came from.
        assertEquals(march2025, widened.withKind(ReportPeriod.Kind.MONTH))
    }

    @Test
    fun `filtering keeps only the lessons taught inside the period`() {
        val classes = listOf(
            klass("2026-04-30T10:00:00Z"),
            klass("2026-05-15T10:00:00Z"),
            klass("2026-06-01T10:00:00Z"),
        )
        val may = ReportPeriod(ReportPeriod.Kind.MONTH, may2026).filter(classes, zone)
        assertEquals(listOf(Instant.parse("2026-05-15T10:00:00Z")), may.map { it.start })
        assertEquals(3, ReportPeriod(ReportPeriod.Kind.ALL, may2026).filter(classes, zone).size)
    }

    @Test
    fun `the default period is the year in progress`() {
        val period = ReportPeriod.currentYear(LocalDate.of(2026, 7, 26))
        assertEquals(ReportPeriod.Kind.YEAR, period.kind)
        assertEquals(LocalDate.of(2026, 1, 1), period.from)
        assertEquals(LocalDate.of(2027, 1, 1), period.until)
    }
}
