package com.seblful.klas.domain.reminders

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.schedule.DaySummary
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DigestsTest {

    private val minsk = TimeZone.of("Europe/Minsk")

    private val nine = 9 * 60

    @Test
    fun `before the hour, the digest is today's`() {
        // 05:00 UTC = 08:00 in Minsk, an hour to go.
        assertEquals(
            Instant.parse("2026-06-15T06:00:00Z"),
            Digests.nextDaily(Instant.parse("2026-06-15T05:00:00Z"), minsk, nine),
        )
    }

    @Test
    fun `after the hour, it is tomorrow's`() {
        assertEquals(
            Instant.parse("2026-06-16T06:00:00Z"),
            Digests.nextDaily(Instant.parse("2026-06-15T12:00:00Z"), minsk, nine),
        )
    }

    @Test
    fun `at the hour exactly, the next one is tomorrow`() {
        assertEquals(
            Instant.parse("2026-06-16T06:00:00Z"),
            Digests.nextDaily(Instant.parse("2026-06-15T06:00:00Z"), minsk, nine),
        )
    }

    @Test
    fun `a day that loses an hour still gets its digest at the chosen time`() {
        val warsaw = TimeZone.of("Europe/Warsaw")
        val saturdayEvening = Instant.parse("2026-03-28T21:00:00Z") // 22:00 local, CET (+1)
        val next = Digests.nextDaily(saturdayEvening, warsaw, nine)

        assertEquals(
            LocalTime(9, 0),
            next.toLocalDateTime(warsaw).time,
            "nine in the morning, whatever that costs in elapsed hours",
        )
        assertEquals(Instant.parse("2026-03-29T07:00:00Z"), next)
    }

    @Test
    fun `a time inside a spring-forward gap resolves rather than throwing`() {
        val warsaw = TimeZone.of("Europe/Warsaw")
        val next = Digests.nextDaily(Instant.parse("2026-03-28T21:00:00Z"), warsaw, 2 * 60 + 30)

        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), next)
        assertEquals(LocalTime(3, 30), next.toLocalDateTime(warsaw).time)
    }

    @Test
    fun `the chosen day being today counts, if the hour is still ahead`() {
        assertEquals(
            Instant.parse("2026-06-15T06:00:00Z"),
            Digests.nextWeekly(
                Instant.parse("2026-06-15T05:00:00Z"),
                minsk,
                DayOfWeek.MONDAY,
                nine,
            ),
        )
    }

    @Test
    fun `the chosen day being today but past the hour rolls to next week`() {
        assertEquals(
            Instant.parse("2026-06-22T06:00:00Z"),
            Digests.nextWeekly(
                Instant.parse("2026-06-15T12:00:00Z"),
                minsk,
                DayOfWeek.MONDAY,
                nine,
            ),
        )
    }

    @Test
    fun `a day later in the week is this week's`() {
        // Monday, asking about Friday.
        assertEquals(
            Instant.parse("2026-06-19T06:00:00Z"),
            Digests.nextWeekly(
                Instant.parse("2026-06-15T12:00:00Z"),
                minsk,
                DayOfWeek.FRIDAY,
                nine,
            ),
        )
    }

    @Test
    fun `a day already gone is next week's`() {
        assertEquals(
            Instant.parse("2026-06-22T06:00:00Z"),
            Digests.nextWeekly(
                Instant.parse("2026-06-17T12:00:00Z"),
                minsk,
                DayOfWeek.MONDAY,
                nine,
            ),
        )
    }

    @Test
    fun `a minute of day is read as a wall clock time`() {
        assertEquals(LocalTime(0, 0), Digests.timeOf(0))
        assertEquals(LocalTime(9, 0), Digests.timeOf(9 * 60))
        assertEquals(LocalTime(23, 59), Digests.timeOf(23 * 60 + 59))
    }

    @Test
    fun `an impossible minute is clamped into the day, not thrown`() {
        assertEquals(LocalTime(0, 0), Digests.timeOf(-1))
        assertEquals(LocalTime(23, 59), Digests.timeOf(24 * 60))
        assertEquals(LocalTime(23, 59), Digests.timeOf(Int.MAX_VALUE))
    }

    @Test
    fun `a weekday outside the week falls back to Monday`() {
        assertEquals(DayOfWeek.MONDAY, Digests.dayOfWeekOf(1))
        assertEquals(DayOfWeek.SUNDAY, Digests.dayOfWeekOf(7))
        assertEquals(DayOfWeek.MONDAY, Digests.dayOfWeekOf(0))
        assertEquals(DayOfWeek.MONDAY, Digests.dayOfWeekOf(8))
    }

    @Test
    fun `a switched-off digest is planned for nothing`() {
        val plan = Digests.plan(
            now = Instant.parse("2026-06-15T05:00:00Z"),
            zone = minsk,
            agendaEnabled = true,
            agendaMinuteOfDay = nine,
            unfinishedEnabled = false,
            unfinishedMinuteOfDay = nine,
            debtsEnabled = false,
            debtDayOfWeek = 1,
            debtMinuteOfDay = nine,
        )

        assertEquals(Instant.parse("2026-06-15T06:00:00Z"), plan.agendaAt)
        assertEquals(null, plan.debtsAt, "nothing arranged, which is what cancels it")
        assertEquals(null, plan.unfinishedAt, "and the same for the evening one")
    }

    @Test
    fun `the morning and evening digests are planned at their own hours`() {
        val plan = Digests.plan(
            now = Instant.parse("2026-06-15T15:00:00Z"),
            zone = minsk,
            agendaEnabled = true,
            agendaMinuteOfDay = nine,
            unfinishedEnabled = true,
            unfinishedMinuteOfDay = 21 * 60,
            debtsEnabled = false,
            debtDayOfWeek = 1,
            debtMinuteOfDay = nine,
        )

        assertEquals(Instant.parse("2026-06-16T06:00:00Z"), plan.agendaAt, "tomorrow at nine")
        assertEquals(Instant.parse("2026-06-15T18:00:00Z"), plan.unfinishedAt, "tonight at nine")
    }

    @Test
    fun `an empty day is not announced`() {
        assertFalse(Digests.Agenda(Day, DaySummary.NONE, lessons = emptyList()).isWorthPosting)
    }

    @Test
    fun `a day of nothing but cancellations is not announced`() {
        val allOff = DaySummary(lessons = 0, minutes = 0, money = Money.ZERO, cancelled = 2)

        assertFalse(Digests.Agenda(Day, allOff, lessons = emptyList()).isWorthPosting)
    }

    @Test
    fun `a day with a lesson on it is announced`() {
        val oneOn = DaySummary(lessons = 1, minutes = 60, money = Money.ofMajor(30), cancelled = 1)

        assertTrue(Digests.Agenda(Day, oneOn, lessons = emptyList()).isWorthPosting)
    }

    @Test
    fun `an evening with nothing unmarked is not announced`() {
        assertFalse(Digests.Unfinished(Day, lessons = emptyList()).isWorthPosting)
    }

    @Test
    fun `an evening with an unmarked lesson is announced, and counts them`() {
        val digest = Digests.Unfinished(
            day = Day,
            lessons = listOf(
                Digests.Agenda.Entry(Instant.parse("2026-06-15T13:00:00Z"), "Математика", "Иван"),
                Digests.Agenda.Entry(Instant.parse("2026-06-15T15:00:00Z"), "Физика", "11 «А»"),
            ),
        )

        assertTrue(digest.isWorthPosting)
        assertEquals(2, digest.count)
    }

    @Test
    fun `nothing owed means nothing to say`() {
        assertFalse(Digests.Debts(students = 0, total = Money.ZERO).isWorthPosting)
        assertTrue(Digests.Debts(students = 1, total = Money.ofMajor(30)).isWorthPosting)
    }

    @Test
    fun `the delay to a future instant is the distance to it`() {
        val now = Instant.parse("2026-09-01T06:00:00Z")

        assertEquals(
            90.minutes,
            Digests.delayUntil(at = now.plus((90 * 60).seconds), now = now),
        )
    }

    @Test
    fun `the delay to an instant already past is zero, not negative`() {
        val now = Instant.parse("2026-09-01T06:00:00Z")

        assertEquals(Duration.ZERO, Digests.delayUntil(at = now.minus(600.seconds), now = now))
        assertEquals(Duration.ZERO, Digests.delayUntil(at = now, now = now))
    }

    @Test
    fun `a due digest that has not happened is still owed`() {
        val nine = Instant.parse("2026-09-01T09:00:00Z")
        val past = nine.plus((12 * 60).seconds)

        assertTrue(
            Digests.stillOwed(arranged = nine, planned = nine.plus((24 * 3600).seconds), now = past),
        )
    }

    @Test
    fun `an arrangement still in the future is replaced, not kept`() {
        val now = Instant.parse("2026-09-01T08:00:00Z")
        val nine = Instant.parse("2026-09-01T09:00:00Z")

        assertFalse(Digests.stillOwed(arranged = nine, planned = nine.plus(3600.seconds), now = now))
    }

    @Test
    fun `switching a digest off cancels even one that is due`() {
        val nine = Instant.parse("2026-09-01T09:00:00Z")

        assertFalse(Digests.stillOwed(arranged = nine, planned = null, now = nine.plus(600.seconds)))
    }

    @Test
    fun `nothing arranged is nothing to protect`() {
        val now = Instant.parse("2026-09-01T09:10:00Z")

        assertFalse(Digests.stillOwed(arranged = null, planned = now.plus(3600.seconds), now = now))
    }

    @Test
    fun `a plan that is not later than the arrangement replaces it`() {
        val nine = Instant.parse("2026-09-01T09:00:00Z")
        val past = nine.plus(600.seconds)

        assertFalse(Digests.stillOwed(arranged = nine, planned = nine, now = past))
        assertFalse(Digests.stillOwed(arranged = nine, planned = nine.minus(60.seconds), now = past))
    }

    private val daily = 1.days

    @Test
    fun `no plan means take down what is enqueued`() {
        val now = Instant.parse("2026-09-01T09:10:00Z")

        assertEquals(
            Digests.Arrangement.Cancel,
            Digests.arrangementFor(planned = null, arranged = now.plus(600.seconds), now = now, every = daily),
        )
    }

    @Test
    fun `no plan cancels a due run too`() {
        val nine = Instant.parse("2026-09-01T09:00:00Z")
        val past = nine.plus(720.seconds)

        assertEquals(
            Digests.Arrangement.Cancel,
            Digests.arrangementFor(planned = null, arranged = nine, now = past, every = daily),
        )
    }

    @Test
    fun `a run already due is left exactly where it is`() {
        val nine = Instant.parse("2026-09-01T09:00:00Z")
        val past = nine.plus(720.seconds)

        assertEquals(
            Digests.Arrangement.LeaveAlone,
            Digests.arrangementFor(planned = nine.plus((86_400).seconds), arranged = nine, now = past, every = daily),
        )
    }

    @Test
    fun `an ordinary plan is enqueued at its own delay`() {
        val now = Instant.parse("2026-09-01T07:00:00Z")
        val nine = Instant.parse("2026-09-01T09:00:00Z")

        assertEquals(
            Digests.Arrangement.Enqueue(delay = 2.hours, every = daily),
            Digests.arrangementFor(planned = nine, arranged = null, now = now, every = daily),
        )
    }

    @Test
    fun `the repeat interval is carried through untouched`() {
        val now = Instant.parse("2026-09-01T07:00:00Z")
        val weekly = 7.days

        val arrangement = Digests.arrangementFor(
            planned = now.plus((3_600).seconds),
            arranged = null,
            now = now,
            every = weekly,
        )

        assertEquals(Digests.Arrangement.Enqueue(delay = 1.hours, every = weekly), arrangement)
    }

    @Test
    fun `a plan that has just gone past is enqueued with no delay`() {
        val now = Instant.parse("2026-09-01T09:00:05Z")
        val nine = Instant.parse("2026-09-01T09:00:00Z")

        assertEquals(
            Digests.Arrangement.Enqueue(delay = Duration.ZERO, every = daily),
            Digests.arrangementFor(planned = nine, arranged = null, now = now, every = daily),
        )
    }

    private companion object {
        /** The day a digest is about — which none of the worth-posting rules read. */
        val Day: LocalDate = LocalDate(2026, 6, 15)
    }
}
