package com.seblful.klas.domain

import com.seblful.klas.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * When a digest fires — the whole of it, on the JVM.
 *
 * Every interesting case here is a boundary that on a device could only be reached by waiting: the chosen
 * minute having already gone today, the chosen weekday *being* today, a clock that springs forward over
 * the hour the tutor picked. That is the argument for the firing instant being computed in a pure function
 * rather than inside the worker that arms it.
 */
class DigestsTest {

    private val minsk = ZoneId.of("Europe/Minsk")

    /** Minsk is UTC+3 all year — no DST — so 09:00 local is 06:00 UTC. */
    private val nine = 9 * 60

    // --- The daily one ------------------------------------------------------------------------------

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

    /**
     * **Strictly** after, and this is the case that matters: the worker that has just fired at nine asks
     * for the next one, and an inclusive comparison would hand back the instant it is standing on — so the
     * digest would post again, and again, until the minute had passed.
     */
    @Test
    fun `at the hour exactly, the next one is tomorrow`() {
        assertEquals(
            Instant.parse("2026-06-16T06:00:00Z"),
            Digests.nextDaily(Instant.parse("2026-06-15T06:00:00Z"), minsk, nine),
        )
    }

    /**
     * A wall-clock promise, not an elapsed-time one. Warsaw springs forward at 02:00 on 29 March 2026, so
     * the day is 23 hours long — and a digest set for nine still lands at nine, 22 hours after the nine
     * before it rather than 24.
     */
    @Test
    fun `a day that loses an hour still gets its digest at the chosen time`() {
        val warsaw = ZoneId.of("Europe/Warsaw")
        val saturdayEvening = Instant.parse("2026-03-28T21:00:00Z") // 22:00 local, CET (+1)
        val next = Digests.nextDaily(saturdayEvening, warsaw, nine)

        assertEquals(
            "nine in the morning, whatever that costs in elapsed hours",
            LocalTime.of(9, 0),
            next.atZone(warsaw).toLocalTime(),
        )
        // CEST (+2) by then, so nine local is 07:00 UTC rather than 08:00.
        assertEquals(Instant.parse("2026-03-29T07:00:00Z"), next)
    }

    /**
     * A time inside the hour a spring-forward deletes cannot happen at all, and `java.time` resolves it by
     * **shifting it forward by the length of the gap** — 02:30 becomes 03:30. The digest therefore lands an
     * hour later than its stored minute on that one day a year, and on every other day at the minute
     * asked for.
     *
     * What this test is really for is that it **resolves rather than throwing**: this runs on a background
     * path where an exception reports nothing to anybody, so the alternative failure mode is the digest
     * silently never happening again.
     */
    @Test
    fun `a time inside a spring-forward gap resolves rather than throwing`() {
        val warsaw = ZoneId.of("Europe/Warsaw")
        val next = Digests.nextDaily(Instant.parse("2026-03-28T21:00:00Z"), warsaw, 2 * 60 + 30)

        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), next)
        assertEquals(LocalTime.of(3, 30), next.atZone(warsaw).toLocalTime())
    }

    // --- The weekly one -----------------------------------------------------------------------------

    /**
     * The chosen day being **today** is the ordinary case for half of every Monday, and jumping to next
     * week would make switching the digest on that morning silently cost the tutor a week.
     */
    @Test
    fun `the chosen day being today counts, if the hour is still ahead`() {
        // Monday 15 June 2026, 05:00 UTC = 08:00 local.
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
        // Wednesday, asking about Monday.
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

    // --- Reading stored values ----------------------------------------------------------------------
    //
    // Both are total, for the reason every other read of stored state in this app is (`ClassStatus.parse`,
    // `Currency.parse`): they run on a background path where a throw reports nothing to anybody, so an
    // impossible value would make the digest quietly stop happening rather than say anything.

    @Test
    fun `a minute of day is read as a wall clock time`() {
        assertEquals(LocalTime.of(0, 0), Digests.timeOf(0))
        assertEquals(LocalTime.of(9, 0), Digests.timeOf(9 * 60))
        assertEquals(LocalTime.of(23, 59), Digests.timeOf(23 * 60 + 59))
    }

    @Test
    fun `an impossible minute is clamped into the day, not thrown`() {
        assertEquals(LocalTime.MIDNIGHT, Digests.timeOf(-1))
        assertEquals(LocalTime.of(23, 59), Digests.timeOf(24 * 60))
        assertEquals(LocalTime.of(23, 59), Digests.timeOf(Int.MAX_VALUE))
    }

    @Test
    fun `a weekday outside the week falls back to Monday`() {
        assertEquals(DayOfWeek.MONDAY, Digests.dayOfWeekOf(1))
        assertEquals(DayOfWeek.SUNDAY, Digests.dayOfWeekOf(7))
        assertEquals(DayOfWeek.MONDAY, Digests.dayOfWeekOf(0))
        assertEquals(DayOfWeek.MONDAY, Digests.dayOfWeekOf(8))
    }

    // --- The plan -----------------------------------------------------------------------------------

    @Test
    fun `a switched-off digest is planned for nothing`() {
        val plan = Digests.plan(
            now = Instant.parse("2026-06-15T05:00:00Z"),
            zone = minsk,
            agendaEnabled = true,
            agendaMinuteOfDay = nine,
            debtsEnabled = false,
            debtDayOfWeek = 1,
            debtMinuteOfDay = nine,
        )

        assertEquals(Instant.parse("2026-06-15T06:00:00Z"), plan.agendaAt)
        assertEquals("nothing arranged, which is what cancels it", null, plan.debtsAt)
    }

    // --- Whether a digest is worth sending ----------------------------------------------------------

    @Test
    fun `an empty day is not announced`() {
        assertFalse(Digests.Agenda(DaySummary.NONE, lessons = emptyList()).isWorthPosting)
    }

    /**
     * A day whose only lesson was called off is **not** worth posting, though [DaySummary] counts it as
     * non-empty. The digest's promise is what is on today, and the tutor is the one who cancelled it.
     */
    @Test
    fun `a day of nothing but cancellations is not announced`() {
        val allOff = DaySummary(lessons = 0, minutes = 0, money = Money.ZERO, cancelled = 2)

        assertFalse(Digests.Agenda(allOff, lessons = emptyList()).isWorthPosting)
    }

    @Test
    fun `a day with a lesson on it is announced`() {
        val oneOn = DaySummary(lessons = 1, minutes = 60, money = Money.ofMajor(30), cancelled = 1)

        assertTrue(Digests.Agenda(oneOn, lessons = emptyList()).isWorthPosting)
    }

    @Test
    fun `nothing owed means nothing to say`() {
        assertFalse(Digests.Debts(students = 0, total = Money.ZERO).isWorthPosting)
        assertTrue(Digests.Debts(students = 1, total = Money.ofMajor(30)).isWorthPosting)
    }

    // --- What an adapter is handed: the delay, and whether to leave a due run alone ---

    @Test
    fun `the delay to a future instant is the distance to it`() {
        val now = Instant.parse("2026-09-01T06:00:00Z")

        assertEquals(
            java.time.Duration.ofMinutes(90),
            Digests.delayUntil(at = now.plusSeconds(90 * 60), now = now),
        )
    }

    /**
     * A plan read some moments ago can name an instant that has since passed. Zero, not negative: "this one
     * is due now" is the honest reading, and a negative delay is not a thing to hand a scheduler.
     */
    @Test
    fun `the delay to an instant already past is zero, not negative`() {
        val now = Instant.parse("2026-09-01T06:00:00Z")

        assertEquals(java.time.Duration.ZERO, Digests.delayUntil(at = now.minusSeconds(600), now = now))
        assertEquals(java.time.Duration.ZERO, Digests.delayUntil(at = now, now = now))
    }

    /**
     * **The case that turned a late digest into a missing one.**
     *
     * `plan` looks strictly forward and `Maintenance` re-plans on every app start, every thirty minutes,
     * after a reboot and behind every write — so a 09:00 agenda WorkManager had not run yet at 09:12 was
     * re-planned for tomorrow, and applying that plan cancelled the pending run. Today's never arrived.
     */
    @Test
    fun `a due digest that has not happened is still owed`() {
        val nine = Instant.parse("2026-09-01T09:00:00Z")
        val past = nine.plusSeconds(12 * 60)

        assertTrue(
            Digests.stillOwed(arranged = nine, planned = nine.plusSeconds(24 * 3600), now = past),
        )
    }

    @Test
    fun `an arrangement still in the future is replaced, not kept`() {
        val now = Instant.parse("2026-09-01T08:00:00Z")
        val nine = Instant.parse("2026-09-01T09:00:00Z")

        // Nothing is owed yet, so a new plan simply applies — which is what corrects drift.
        assertFalse(Digests.stillOwed(arranged = nine, planned = nine.plusSeconds(3600), now = now))
    }

    @Test
    fun `switching a digest off cancels even one that is due`() {
        val nine = Instant.parse("2026-09-01T09:00:00Z")

        assertFalse(Digests.stillOwed(arranged = nine, planned = null, now = nine.plusSeconds(600)))
    }

    @Test
    fun `nothing arranged is nothing to protect`() {
        val now = Instant.parse("2026-09-01T09:10:00Z")

        assertFalse(Digests.stillOwed(arranged = null, planned = now.plusSeconds(3600), now = now))
    }

    /**
     * The plan must be *later* than what is arranged. An equal or earlier plan is not the schedule rolling
     * past a missed firing — it is a different arrangement, and it applies.
     */
    @Test
    fun `a plan that is not later than the arrangement replaces it`() {
        val nine = Instant.parse("2026-09-01T09:00:00Z")
        val past = nine.plusSeconds(600)

        assertFalse(Digests.stillOwed(arranged = nine, planned = nine, now = past))
        assertFalse(Digests.stillOwed(arranged = nine, planned = nine.minusSeconds(60), now = past))
    }
}
