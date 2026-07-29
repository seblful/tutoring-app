package com.seblful.klas.domain

import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarLayoutTest {

    private val zone = ZoneId.of("Europe/Minsk")

    /**
     * A class of its own — distinct [id], no group. Ids matter here: sessions key an individual class
     * by its id, so a fixture leaving them all at 0 would collapse a day's classes into one lesson.
     */
    private fun klass(
        start: String,
        status: ClassStatus = ClassStatus.SCHEDULED,
        id: Long = nextId++,
        groupId: Long? = null,
        studentId: Long = 1,
    ) = TutoringClass(
        id = id, studentId = studentId, subjectId = 1, start = Instant.parse(start),
        durationMinutes = 60, status = status, groupId = groupId,
    )

    private var nextId = 1L

    /** The [size] classes one group lesson is stored as: same group, same start, one per member. */
    private fun groupLesson(
        start: String,
        size: Int,
        vararg statuses: ClassStatus,
        groupId: Long = 7,
    ) = (0 until size).map { member ->
        klass(
            start = start,
            status = statuses.getOrElse(member) { statuses.lastOrNull() ?: ClassStatus.SCHEDULED },
            groupId = groupId,
            studentId = (member + 1).toLong(),
        )
    }

    @Test
    fun `every week row is a full week starting on Monday`() {
        val grid = CalendarLayout.monthGrid(YearMonth.of(2026, 7))

        assertTrue(grid.weeks.all { it.size == CalendarLayout.DAYS_IN_WEEK })
        assertTrue(grid.weeks.all { it.first().dayOfWeek == DayOfWeek.MONDAY })
        assertTrue(grid.weeks.all { it.last().dayOfWeek == DayOfWeek.SUNDAY })
    }

    @Test
    fun `grid is contiguous and contains every day of the month`() {
        val grid = CalendarLayout.monthGrid(YearMonth.of(2026, 7))
        val cells = grid.weeks.flatten()

        cells.zipWithNext { a, b -> assertEquals(a.plusDays(1), b) }
        (1..31).forEach { day -> assertTrue(LocalDate.of(2026, 7, day) in cells) }
    }

    @Test
    fun `padding cells come from the neighbouring months`() {
        // July 2026 starts on a Wednesday, so the first row leads with 29-30 June.
        val grid = CalendarLayout.monthGrid(YearMonth.of(2026, 7))

        assertEquals(LocalDate.of(2026, 6, 29), grid.firstDate)
        assertFalse(grid.isInMonth(LocalDate.of(2026, 6, 30)))
        assertTrue(grid.isInMonth(LocalDate.of(2026, 7, 1)))
        assertEquals(LocalDate.of(2026, 8, 3), grid.endExclusive)
    }

    @Test
    fun `a month that starts on Monday needs no leading padding`() {
        val grid = CalendarLayout.monthGrid(YearMonth.of(2026, 6))

        assertEquals(LocalDate.of(2026, 6, 1), grid.firstDate)
        assertEquals(5, grid.weeks.size)
    }

    @Test
    fun `a non-leap February starting on Monday fits in exactly four rows`() {
        val grid = CalendarLayout.monthGrid(YearMonth.of(2027, 2))

        assertEquals(4, grid.weeks.size)
        assertEquals(LocalDate.of(2027, 2, 1), grid.firstDate)
        assertEquals(LocalDate.of(2027, 3, 1), grid.endExclusive)
    }

    @Test
    fun `a long month starting late in the week needs six rows`() {
        // 31 days starting on a Sunday is the worst case: 6 leading pad days + 31 = 37 cells.
        val grid = CalendarLayout.monthGrid(YearMonth.of(2026, 3))

        assertEquals(DayOfWeek.SUNDAY, LocalDate.of(2026, 3, 1).dayOfWeek)
        assertEquals(6, grid.weeks.size)
    }

    @Test
    fun `a leap day gets its own cell`() {
        val grid = CalendarLayout.monthGrid(YearMonth.of(2028, 2))

        assertTrue(LocalDate.of(2028, 2, 29) in grid.weeks.flatten())
    }

    @Test
    fun `a week is seven days anchored on its Monday`() {
        // 15 July 2026 is a Wednesday.
        val week = CalendarLayout.week(LocalDate.of(2026, 7, 15))

        assertEquals(CalendarLayout.DAYS_IN_WEEK, week.size)
        assertEquals(LocalDate.of(2026, 7, 13), week.first())
        assertEquals(LocalDate.of(2026, 7, 19), week.last())
        week.zipWithNext { a, b -> assertEquals(a.plusDays(1), b) }
    }

    @Test
    fun `every day of a week resolves to the same start`() {
        val monday = LocalDate.of(2026, 7, 13)

        CalendarLayout.week(monday).forEach { day ->
            assertEquals(monday, CalendarLayout.startOfWeek(day))
        }
    }

    @Test
    fun `a week spanning a month and a year boundary stays contiguous`() {
        // 31 December 2026 is a Thursday, so its week runs 28 Dec 2026 – 3 Jan 2027.
        val week = CalendarLayout.week(LocalDate.of(2026, 12, 31))

        assertEquals(LocalDate.of(2026, 12, 28), week.first())
        assertEquals(LocalDate.of(2027, 1, 3), week.last())
        week.zipWithNext { a, b -> assertEquals(a.plusDays(1), b) }
    }

    @Test
    fun `a Sunday-first week starts on the Sunday before`() {
        val week = CalendarLayout.week(LocalDate.of(2026, 7, 15), DayOfWeek.SUNDAY)

        assertEquals(LocalDate.of(2026, 7, 12), week.first())
        assertEquals(DayOfWeek.SATURDAY, week.last().dayOfWeek)
    }

    @Test
    fun `every month grid row is exactly one week`() {
        val grid = CalendarLayout.monthGrid(YearMonth.of(2026, 7))

        grid.weeks.forEach { row -> assertEquals(row, CalendarLayout.week(row.first())) }
    }

    /**
     * There is no separate list of weekday headers to keep in step: the header row reads its names off the
     * grid's first week (`ui/schedule/WeekdayHeader`), so this is the only place a week start is decided.
     */
    @Test
    fun `a Sunday-first month grid is laid out Sunday to Saturday`() {
        val grid = CalendarLayout.monthGrid(YearMonth.of(2026, 7), DayOfWeek.SUNDAY)

        assertEquals(DayOfWeek.SUNDAY, grid.firstDate.dayOfWeek)
        grid.weeks.forEach { row ->
            assertEquals(DayOfWeek.SUNDAY, row.first().dayOfWeek)
            assertEquals(DayOfWeek.SATURDAY, row.last().dayOfWeek)
        }
        // Every day of July still has a cell — a different week start pads the page, it never drops a day.
        val cells = grid.weeks.flatten()
        assertTrue(cells.containsAll((1..31).map { LocalDate.of(2026, 7, it) }))
    }

    @Test
    fun `a group lesson counts once, however many members it has`() {
        val loads = CalendarLayout.dayLoads(groupLesson("2026-07-15T07:00:00Z", size = 2), zone)
        val load = loads.getValue(LocalDate.of(2026, 7, 15))

        assertEquals("two students, one lesson taught", 1, load.total)
        assertEquals(listOf(CalendarLayout.Tally(ClassStatus.SCHEDULED, 1)), load.tallies)
    }

    @Test
    fun `a lesson is a lesson, group or individual`() {
        val loads = CalendarLayout.dayLoads(
            groupLesson("2026-07-15T07:00:00Z", size = 3) + klass("2026-07-15T10:00:00Z"),
            zone,
        )

        assertEquals(2, loads.getValue(LocalDate.of(2026, 7, 15)).total)
    }

    @Test
    fun `two group lessons at different times stay two lessons`() {
        val loads = CalendarLayout.dayLoads(
            groupLesson("2026-07-15T07:00:00Z", size = 2) +
                groupLesson("2026-07-15T10:00:00Z", size = 2),
            zone,
        )

        assertEquals(2, loads.getValue(LocalDate.of(2026, 7, 15)).total)
    }

    @Test
    fun `a mixed group lesson counts once, under no status of its own`() {
        val loads = CalendarLayout.dayLoads(
            groupLesson(
                "2026-07-15T07:00:00Z",
                size = 2,
                ClassStatus.CANCELLED,
                ClassStatus.COMPLETED,
            ) + klass("2026-07-15T10:00:00Z", ClassStatus.COMPLETED),
            zone,
        )
        val load = loads.getValue(LocalDate.of(2026, 7, 15))

        assertEquals(2, load.total)
        assertEquals(
            "the corrected lesson is neither completed nor cancelled, and reads last",
            listOf(
                CalendarLayout.Tally(ClassStatus.COMPLETED, 1),
                CalendarLayout.Tally(null, 1),
            ),
            load.tallies,
        )
    }

    @Test
    fun `day loads count a day's classes per status`() {
        val loads = CalendarLayout.dayLoads(
            listOf(
                klass("2026-07-15T07:00:00Z"),
                klass("2026-07-15T10:00:00Z"),
                klass("2026-07-15T12:00:00Z", ClassStatus.CANCELLED),
                klass("2026-07-16T07:00:00Z", ClassStatus.COMPLETED),
            ),
            zone,
        )

        assertEquals(3, loads.getValue(LocalDate.of(2026, 7, 15)).total)
        assertEquals(
            mapOf(ClassStatus.SCHEDULED to 2, ClassStatus.CANCELLED to 1),
            loads.getValue(LocalDate.of(2026, 7, 15)).counts,
        )
        assertEquals(1, loads.getValue(LocalDate.of(2026, 7, 16)).total)
        assertFalse("days without classes are absent", LocalDate.of(2026, 7, 17) in loads)
    }

    @Test
    fun `day loads bucket a class by its local date, not UTC`() {
        // 22:30 UTC is already the next day in Minsk (UTC+3).
        val loads = CalendarLayout.dayLoads(listOf(klass("2026-07-15T22:30:00Z")), zone)

        assertEquals(setOf(LocalDate.of(2026, 7, 16)), loads.keys)
    }

    @Test
    fun `tallies read scheduled first, whatever order the counts came in`() {
        val load = CalendarLayout.DayLoad(
            mapOf(
                ClassStatus.CANCELLED to 1,
                ClassStatus.COMPLETED to 4,
                ClassStatus.SCHEDULED to 2,
            )
        )

        assertEquals(
            listOf(
                CalendarLayout.Tally(ClassStatus.SCHEDULED, 2),
                CalendarLayout.Tally(ClassStatus.COMPLETED, 4),
                CalendarLayout.Tally(ClassStatus.CANCELLED, 1),
            ),
            load.tallies,
        )
    }

    @Test
    fun `a status the day has none of gets no tally, rather than a zero`() {
        val load = CalendarLayout.DayLoad(mapOf(ClassStatus.COMPLETED to 3))

        assertEquals(listOf(CalendarLayout.Tally(ClassStatus.COMPLETED, 3)), load.tallies)
        assertEquals(3, load.total)
    }

    @Test
    fun `a busy day never needs more than one tally per status`() {
        val loads = CalendarLayout.dayLoads(
            List(9) { hour -> klass("2026-07-15T0$hour:00:00Z", ClassStatus.COMPLETED) } +
                klass("2026-07-15T18:00:00Z"),
            zone,
        )
        val load = loads.getValue(LocalDate.of(2026, 7, 15))

        assertEquals(10, load.total)
        assertEquals(
            "however many lessons a day holds, its cell prints one number per status",
            listOf(
                CalendarLayout.Tally(ClassStatus.SCHEDULED, 1),
                CalendarLayout.Tally(ClassStatus.COMPLETED, 9),
            ),
            load.tallies,
        )
    }
}
