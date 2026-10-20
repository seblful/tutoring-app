package com.seblful.klas.domain.schedule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class RecurrenceTest {

    private val monday = LocalDate.parse("2026-09-07")
    private val atFour = LocalTime(16, 0)
    private val minsk: TimeZone = TimeZone.of("Europe/Minsk")

    @Test
    fun `a booking that does not repeat is the one day it was booked for`() {
        assertEquals(listOf(monday), Recurrence.dates(monday, Repeat.NONE, count = 4))
    }

    @Test
    fun `the count is ignored while nothing is being repeated`() {
        assertEquals(listOf(monday), Recurrence.dates(monday, Repeat.NONE, count = 52))
    }

    @Test
    fun `a weekly run is the same weekday - a week apart - first one included`() {
        assertEquals(
            listOf("2026-09-07", "2026-09-14", "2026-09-21", "2026-09-28").map(LocalDate::parse),
            Recurrence.dates(monday, Repeat.WEEKLY, count = 4),
        )
    }

    @Test
    fun `a fortnightly run skips a week each time`() {
        assertEquals(
            listOf("2026-09-07", "2026-09-21", "2026-10-05").map(LocalDate::parse),
            Recurrence.dates(monday, Repeat.FORTNIGHTLY, count = 3),
        )
    }

    @Test
    fun `every occurrence falls on the weekday the first one did`() {
        Recurrence.dates(monday, Repeat.WEEKLY, count = 12).forEach {
            assertEquals(monday.dayOfWeek, it.dayOfWeek)
        }
    }

    @Test
    fun `a run crosses a month and a year without special-casing either`() {
        val fromDecember = LocalDate.parse("2026-12-21")
        assertEquals(
            listOf("2026-12-21", "2026-12-28", "2027-01-04").map(LocalDate::parse),
            Recurrence.dates(fromDecember, Repeat.WEEKLY, count = 3),
        )
    }

    @Test
    fun `a run is never empty - whatever the count says`() {
        listOf(0, -1, Int.MIN_VALUE).forEach { count ->
            assertEquals(listOf(monday), Recurrence.dates(monday, Repeat.WEEKLY, count))
        }
    }

    @Test
    fun `a run is capped - so no single save can write unbounded rows`() {
        val dates = Recurrence.dates(monday, Repeat.WEEKLY, count = Int.MAX_VALUE)
        assertEquals(Recurrence.MAX_COUNT, dates.size)
    }

    @Test
    fun `the last date is the one the run actually ends on`() {
        assertEquals(
            Recurrence.dates(monday, Repeat.WEEKLY, count = 8).last(),
            Recurrence.lastDate(monday, Repeat.WEEKLY, count = 8),
        )
    }

    @Test
    fun `a booking that does not repeat ends on the day it starts`() {
        assertEquals(monday, Recurrence.lastDate(monday, Repeat.NONE, count = 4))
    }

    @Test
    fun `every occurrence starts at the wall-clock time that was picked`() {
        Recurrence.startsFor(monday, atFour, Repeat.WEEKLY, count = 6, zone = minsk).forEach {
            assertEquals(atFour, it.toLocalDateTime(minsk).time)
        }
    }

    @Test
    fun `a run through a daylight-saving change keeps its hour rather than its interval`() {
        val brussels = TimeZone.of("Europe/Brussels")
        val sunday = LocalDate.parse("2026-10-11")
        val starts = Recurrence.startsFor(sunday, atFour, Repeat.WEEKLY, count = 4, zone = brussels)

        starts.forEach { assertEquals(atFour, it.toLocalDateTime(brussels).time) }
        assertEquals(
            169.hours,
            ((starts[2]) - (starts[1])),
            "the week spanning the change is an hour longer, and still ends at four",
        )
        assertEquals(168.hours, ((starts[3]) - (starts[2])))
    }

    @Test
    fun `the first start is the lesson the tutor filled in`() {
        val starts = Recurrence.startsFor(monday, atFour, Repeat.WEEKLY, count = 4, zone = minsk)
        assertEquals(monday.atTime(atFour).toInstant(minsk), starts.first())
    }

    @Test
    fun `occurrences cannot overlap each other - whatever the lesson's length`() {
        // The guarantee `ClassRepository.bookSeries` relies on to check the schedule as it stands and
        // never fold in the rows it is about to write: a week apart beats the longest lesson the
        // duration picker can express (23 h 59 m).
        val starts = Recurrence.startsFor(monday, atFour, Repeat.WEEKLY, count = 12, zone = minsk)
        starts.zipWithNext().forEach { (earlier, later) ->
            val gap = (later - earlier)
            assertTrue(gap >= (23 * 60 + 59).minutes, "Occurrences $earlier and $later can overlap")
        }
    }
}
