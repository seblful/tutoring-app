package com.seblful.klas.domain.schedule

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.TutoringClass
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class CalendarLayoutTest {

    private val zone = TimeZone.of("Europe/Minsk")

    /**
     * A class of its own — distinct [id], no group. Ids matter here: sessions key an individual class
     * by its id, so a fixture leaving them all at 0 would collapse a day's classes into one lesson.
     */
    private fun klass(
        start: String,
        status: LessonStatus = LessonStatus.SCHEDULED,
        id: Long = nextId++,
        groupId: Long? = null,
        studentId: Long = 1,
    ) = TutoringClass(
        id = id, lessonId = lessonIdFor(groupId, start, id), studentId = studentId, subjectId = 1,
        start = Instant.parse(start), durationMinutes = 60, status = status, groupId = groupId,
    )

    private val lessonIds = mutableMapOf<Pair<Long, Any>, Long>()

    /**
     * The lesson a fixture's class belongs to: one per (group, start) for a group's, its own id for an
     * individual class. A convention of the test — storage has a column for it — and it has to be stated
     * because that column is the whole of what makes classes one lesson (`SessionRules.sessions`).
     */
    private fun lessonIdFor(groupId: Long?, start: Any, id: Long): Long =
        if (groupId == null) id else lessonIds.getOrPut(groupId to start) { 1_000L + lessonIds.size }

    private var nextId = 1L

    /** The [size] classes one group lesson is stored as: same group, same start, one per member. */
    private fun groupLesson(
        start: String,
        size: Int,
        vararg statuses: LessonStatus,
        groupId: Long = 7,
    ) = (0 until size).map { member ->
        klass(
            start = start,
            status = statuses.getOrElse(member) { statuses.lastOrNull() ?: LessonStatus.SCHEDULED },
            groupId = groupId,
            studentId = (member + 1).toLong(),
        )
    }

    @Test
    fun `every week row is a full week starting on Monday`() {
        val grid = CalendarLayout.monthGrid(YearMonth(2026, 7))

        assertTrue(grid.weeks.all { it.size == CalendarLayout.DAYS_IN_WEEK })
        assertTrue(grid.weeks.all { it.first().dayOfWeek == DayOfWeek.MONDAY })
        assertTrue(grid.weeks.all { it.last().dayOfWeek == DayOfWeek.SUNDAY })
    }

    @Test
    fun `grid is contiguous and contains every day of the month`() {
        val grid = CalendarLayout.monthGrid(YearMonth(2026, 7))
        val cells = grid.weeks.flatten()

        cells.zipWithNext { a, b -> assertEquals(a.plus(1, DateTimeUnit.DAY), b) }
        (1..31).forEach { day -> assertTrue(LocalDate(2026, 7, day) in cells) }
    }

    @Test
    fun `padding cells come from the neighbouring months`() {
        // July 2026 starts on a Wednesday, so the first row leads with 29-30 June.
        val grid = CalendarLayout.monthGrid(YearMonth(2026, 7))

        assertEquals(LocalDate(2026, 6, 29), grid.firstDate)
        assertFalse(grid.isInMonth(LocalDate(2026, 6, 30)))
        assertTrue(grid.isInMonth(LocalDate(2026, 7, 1)))
        assertEquals(LocalDate(2026, 8, 3), grid.endExclusive)
    }

    @Test
    fun `a month that starts on Monday needs no leading padding`() {
        val grid = CalendarLayout.monthGrid(YearMonth(2026, 6))

        assertEquals(LocalDate(2026, 6, 1), grid.firstDate)
        assertEquals(5, grid.weeks.size)
    }

    @Test
    fun `a non-leap February starting on Monday fits in exactly four rows`() {
        val grid = CalendarLayout.monthGrid(YearMonth(2027, 2))

        assertEquals(4, grid.weeks.size)
        assertEquals(LocalDate(2027, 2, 1), grid.firstDate)
        assertEquals(LocalDate(2027, 3, 1), grid.endExclusive)
    }

    @Test
    fun `a long month starting late in the week needs six rows`() {
        // 31 days starting on a Sunday is the worst case: 6 leading pad days + 31 = 37 cells.
        val grid = CalendarLayout.monthGrid(YearMonth(2026, 3))

        assertEquals(DayOfWeek.SUNDAY, LocalDate(2026, 3, 1).dayOfWeek)
        assertEquals(6, grid.weeks.size)
    }

    @Test
    fun `a leap day gets its own cell`() {
        val grid = CalendarLayout.monthGrid(YearMonth(2028, 2))

        assertTrue(LocalDate(2028, 2, 29) in grid.weeks.flatten())
    }

    @Test
    fun `a week is seven days anchored on its Monday`() {
        // 15 July 2026 is a Wednesday.
        val week = CalendarLayout.week(LocalDate(2026, 7, 15))

        assertEquals(CalendarLayout.DAYS_IN_WEEK, week.size)
        assertEquals(LocalDate(2026, 7, 13), week.first())
        assertEquals(LocalDate(2026, 7, 19), week.last())
        week.zipWithNext { a, b -> assertEquals(a.plus(1, DateTimeUnit.DAY), b) }
    }

    @Test
    fun `every day of a week resolves to the same start`() {
        val monday = LocalDate(2026, 7, 13)

        CalendarLayout.week(monday).forEach { day ->
            assertEquals(monday, CalendarLayout.startOfWeek(day))
        }
    }

    @Test
    fun `a week spanning a month and a year boundary stays contiguous`() {
        // 31 December 2026 is a Thursday, so its week runs 28 Dec 2026 – 3 Jan 2027.
        val week = CalendarLayout.week(LocalDate(2026, 12, 31))

        assertEquals(LocalDate(2026, 12, 28), week.first())
        assertEquals(LocalDate(2027, 1, 3), week.last())
        week.zipWithNext { a, b -> assertEquals(a.plus(1, DateTimeUnit.DAY), b) }
    }

    @Test
    fun `a Sunday-first week starts on the Sunday before`() {
        val week = CalendarLayout.week(LocalDate(2026, 7, 15), DayOfWeek.SUNDAY)

        assertEquals(LocalDate(2026, 7, 12), week.first())
        assertEquals(DayOfWeek.SATURDAY, week.last().dayOfWeek)
    }

    @Test
    fun `every month grid row is exactly one week`() {
        val grid = CalendarLayout.monthGrid(YearMonth(2026, 7))

        grid.weeks.forEach { row -> assertEquals(row, CalendarLayout.week(row.first())) }
    }

    /**
     * There is no separate list of weekday headers to keep in step: the header row reads its names off the
     * grid's first week (`ui/schedule/WeekdayHeader`), so this is the only place a week start is decided.
     */
    @Test
    fun `a Sunday-first month grid is laid out Sunday to Saturday`() {
        val grid = CalendarLayout.monthGrid(YearMonth(2026, 7), DayOfWeek.SUNDAY)

        assertEquals(DayOfWeek.SUNDAY, grid.firstDate.dayOfWeek)
        grid.weeks.forEach { row ->
            assertEquals(DayOfWeek.SUNDAY, row.first().dayOfWeek)
            assertEquals(DayOfWeek.SATURDAY, row.last().dayOfWeek)
        }
        // Every day of July still has a cell — a different week start pads the page, it never drops a day.
        val cells = grid.weeks.flatten()
        assertTrue(cells.containsAll((1..31).map { LocalDate(2026, 7, it) }))
    }

    @Test
    fun `a group lesson counts once, however many members it has`() {
        val loads = CalendarLayout.dayLoads(groupLesson("2026-07-15T07:00:00Z", size = 2), zone)
        val load = loads.getValue(LocalDate(2026, 7, 15))

        assertEquals(1, load.total, "two students, one lesson taught")
        assertEquals(listOf(CalendarLayout.Tally(LessonStatus.SCHEDULED, 1)), load.tallies)
    }

    @Test
    fun `a lesson is a lesson, group or individual`() {
        val loads = CalendarLayout.dayLoads(
            groupLesson("2026-07-15T07:00:00Z", size = 3) + klass("2026-07-15T10:00:00Z"),
            zone,
        )

        assertEquals(2, loads.getValue(LocalDate(2026, 7, 15)).total)
    }

    @Test
    fun `two group lessons at different times stay two lessons`() {
        val loads = CalendarLayout.dayLoads(
            groupLesson("2026-07-15T07:00:00Z", size = 2) +
                groupLesson("2026-07-15T10:00:00Z", size = 2),
            zone,
        )

        assertEquals(2, loads.getValue(LocalDate(2026, 7, 15)).total)
    }

    /**
     * A cell counts lessons, in the three statuses a lesson can be in. A group whose absentee was
     * cancelled on their own used to be counted in a fourth, null bucket and printed as its own number —
     * a count of «Разные», which is not a status anything can be in. Such a lesson cannot even be
     * described any more: its members share its status and differ only in whether they were there.
     */
    @Test
    fun `a group lesson with an absent member counts under the lesson's own status`() {
        val loads = CalendarLayout.dayLoads(
            groupLesson("2026-07-15T07:00:00Z", size = 2, LessonStatus.COMPLETED) +
                klass("2026-07-15T10:00:00Z", LessonStatus.COMPLETED),
            zone,
        )
        val load = loads.getValue(LocalDate(2026, 7, 15))

        assertEquals(2, load.total)
        assertEquals(
            listOf(CalendarLayout.Tally(LessonStatus.COMPLETED, 2)),
            load.tallies,
            "two lessons taught, one of them with somebody missing",
        )
    }

    @Test
    fun `day loads count a day's classes per status`() {
        val loads = CalendarLayout.dayLoads(
            listOf(
                klass("2026-07-15T07:00:00Z"),
                klass("2026-07-15T10:00:00Z"),
                klass("2026-07-15T12:00:00Z", LessonStatus.CANCELLED),
                klass("2026-07-16T07:00:00Z", LessonStatus.COMPLETED),
            ),
            zone,
        )

        assertEquals(3, loads.getValue(LocalDate(2026, 7, 15)).total)
        assertEquals(
            mapOf(LessonStatus.SCHEDULED to 2, LessonStatus.CANCELLED to 1),
            loads.getValue(LocalDate(2026, 7, 15)).counts,
        )
        assertEquals(1, loads.getValue(LocalDate(2026, 7, 16)).total)
        assertFalse(LocalDate(2026, 7, 17) in loads, "days without classes are absent")
    }

    @Test
    fun `day loads bucket a class by its local date, not UTC`() {
        // 22:30 UTC is already the next day in Minsk (UTC+3).
        val loads = CalendarLayout.dayLoads(listOf(klass("2026-07-15T22:30:00Z")), zone)

        assertEquals(setOf(LocalDate(2026, 7, 16)), loads.keys)
    }

    @Test
    fun `tallies read scheduled first, whatever order the counts came in`() {
        val load = CalendarLayout.DayLoad(
            mapOf(
                LessonStatus.CANCELLED to 1,
                LessonStatus.COMPLETED to 4,
                LessonStatus.SCHEDULED to 2,
            )
        )

        assertEquals(
            listOf(
                CalendarLayout.Tally(LessonStatus.SCHEDULED, 2),
                CalendarLayout.Tally(LessonStatus.COMPLETED, 4),
                CalendarLayout.Tally(LessonStatus.CANCELLED, 1),
            ),
            load.tallies,
        )
    }

    @Test
    fun `a status the day has none of gets no tally, rather than a zero`() {
        val load = CalendarLayout.DayLoad(mapOf(LessonStatus.COMPLETED to 3))

        assertEquals(listOf(CalendarLayout.Tally(LessonStatus.COMPLETED, 3)), load.tallies)
        assertEquals(3, load.total)
    }

    @Test
    fun `a busy day never needs more than one tally per status`() {
        val loads = CalendarLayout.dayLoads(
            List(9) { hour -> klass("2026-07-15T0$hour:00:00Z", LessonStatus.COMPLETED) } +
                klass("2026-07-15T18:00:00Z"),
            zone,
        )
        val load = loads.getValue(LocalDate(2026, 7, 15))

        assertEquals(10, load.total)
        assertEquals(
            listOf(
                CalendarLayout.Tally(LessonStatus.SCHEDULED, 1),
                CalendarLayout.Tally(LessonStatus.COMPLETED, 9),
            ),
            load.tallies,
            "however many lessons a day holds, its cell prints one number per status",
        )
    }
}
