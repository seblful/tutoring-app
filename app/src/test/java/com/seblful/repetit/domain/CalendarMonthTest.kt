package com.seblful.repetit.domain

import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarMonthTest {

    private val zone = ZoneId.of("Europe/Minsk")

    private fun klass(start: String, status: ClassStatus = ClassStatus.SCHEDULED) = TutoringClass(
        studentId = 1, subjectId = 1, start = Instant.parse(start),
        durationMinutes = 60, status = status,
    )

    @Test
    fun `every week row is a full week starting on Monday`() {
        val grid = CalendarMonth.grid(YearMonth.of(2026, 7))

        assertTrue(grid.weeks.all { it.size == CalendarMonth.DAYS_IN_WEEK })
        assertTrue(grid.weeks.all { it.first().dayOfWeek == DayOfWeek.MONDAY })
        assertTrue(grid.weeks.all { it.last().dayOfWeek == DayOfWeek.SUNDAY })
    }

    @Test
    fun `grid is contiguous and contains every day of the month`() {
        val grid = CalendarMonth.grid(YearMonth.of(2026, 7))
        val cells = grid.weeks.flatten()

        cells.zipWithNext { a, b -> assertEquals(a.plusDays(1), b) }
        (1..31).forEach { day -> assertTrue(LocalDate.of(2026, 7, day) in cells) }
    }

    @Test
    fun `padding cells come from the neighbouring months`() {
        // July 2026 starts on a Wednesday, so the first row leads with 29-30 June.
        val grid = CalendarMonth.grid(YearMonth.of(2026, 7))

        assertEquals(LocalDate.of(2026, 6, 29), grid.firstDate)
        assertFalse(grid.isInMonth(LocalDate.of(2026, 6, 30)))
        assertTrue(grid.isInMonth(LocalDate.of(2026, 7, 1)))
        assertEquals(LocalDate.of(2026, 8, 3), grid.endExclusive)
    }

    @Test
    fun `a month that starts on Monday needs no leading padding`() {
        val grid = CalendarMonth.grid(YearMonth.of(2026, 6))

        assertEquals(LocalDate.of(2026, 6, 1), grid.firstDate)
        assertEquals(5, grid.weeks.size)
    }

    @Test
    fun `a non-leap February starting on Monday fits in exactly four rows`() {
        val grid = CalendarMonth.grid(YearMonth.of(2027, 2))

        assertEquals(4, grid.weeks.size)
        assertEquals(LocalDate.of(2027, 2, 1), grid.firstDate)
        assertEquals(LocalDate.of(2027, 3, 1), grid.endExclusive)
    }

    @Test
    fun `a long month starting late in the week needs six rows`() {
        // 31 days starting on a Sunday is the worst case: 6 leading pad days + 31 = 37 cells.
        val grid = CalendarMonth.grid(YearMonth.of(2026, 3))

        assertEquals(DayOfWeek.SUNDAY, LocalDate.of(2026, 3, 1).dayOfWeek)
        assertEquals(6, grid.weeks.size)
    }

    @Test
    fun `a leap day gets its own cell`() {
        val grid = CalendarMonth.grid(YearMonth.of(2028, 2))

        assertTrue(LocalDate.of(2028, 2, 29) in grid.weeks.flatten())
    }

    @Test
    fun `weekday headers follow the first day of the week`() {
        assertEquals(DayOfWeek.MONDAY, CalendarMonth.weekdays().first())
        assertEquals(DayOfWeek.SUNDAY, CalendarMonth.weekdays().last())
        assertEquals(DayOfWeek.SUNDAY, CalendarMonth.weekdays(DayOfWeek.SUNDAY).first())
    }

    @Test
    fun `day loads count a day's classes and record their statuses`() {
        val loads = CalendarMonth.dayLoads(
            listOf(
                klass("2026-07-15T07:00:00Z"),
                klass("2026-07-15T12:00:00Z", ClassStatus.CANCELLED),
                klass("2026-07-16T07:00:00Z", ClassStatus.COMPLETED),
            ),
            zone,
        )

        assertEquals(2, loads.getValue(LocalDate.of(2026, 7, 15)).total)
        assertEquals(
            setOf(ClassStatus.SCHEDULED, ClassStatus.CANCELLED),
            loads.getValue(LocalDate.of(2026, 7, 15)).statuses,
        )
        assertEquals(1, loads.getValue(LocalDate.of(2026, 7, 16)).total)
        assertFalse("days without classes are absent", LocalDate.of(2026, 7, 17) in loads)
    }

    @Test
    fun `day loads bucket a class by its local date, not UTC`() {
        // 22:30 UTC is already the next day in Minsk (UTC+3).
        val loads = CalendarMonth.dayLoads(listOf(klass("2026-07-15T22:30:00Z")), zone)

        assertEquals(setOf(LocalDate.of(2026, 7, 16)), loads.keys)
    }

    @Test
    fun `the dominant status prefers a scheduled class, then a completed one`() {
        fun load(vararg statuses: ClassStatus) =
            CalendarMonth.DayLoad(statuses.size, statuses.toSet())

        assertEquals(
            ClassStatus.SCHEDULED,
            load(ClassStatus.CANCELLED, ClassStatus.COMPLETED, ClassStatus.SCHEDULED).dominant,
        )
        assertEquals(
            ClassStatus.COMPLETED,
            load(ClassStatus.CANCELLED, ClassStatus.COMPLETED).dominant,
        )
        assertEquals(ClassStatus.CANCELLED, load(ClassStatus.CANCELLED).dominant)
    }
}
