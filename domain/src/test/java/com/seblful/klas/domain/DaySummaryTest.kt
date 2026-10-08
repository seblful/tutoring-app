package com.seblful.klas.domain

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the line under a day's heading says.
 *
 * The two rules it rests on are the ones a screen would have got wrong: a day's money is what its
 * lessons **come to** rather than what they have billed (so tomorrow reads as itself and not as zero),
 * and a group lesson is **one** lesson of the tutor's however many are in the room.
 */
class DaySummaryTest {

    private val zone: ZoneId = ZoneId.of("Europe/Minsk")
    private val day: LocalDate = LocalDate.of(2026, 6, 20)
    private fun at(hour: Int): Instant = day.atTime(hour, 0).atZone(zone).toInstant()

    private var nextId = 1L

    private fun klass(
        at: Instant,
        lessonId: Long,
        studentId: Long = 1,
        minutes: Int = 60,
        rate: Money = Money.ofMajor(30),
        status: LessonStatus = LessonStatus.SCHEDULED,
        attended: Attended = Attended.EXPECTED,
        groupId: Long? = null,
    ) = TutoringClass(
        id = nextId++,
        lessonId = lessonId,
        studentId = studentId,
        subjectId = 1,
        start = at,
        durationMinutes = minutes,
        price = rate,
        status = status,
        attended = attended,
        // The stored charge is deliberately left at zero even for a completed class, so a test that
        // passes can only be reading the rate — see the KDoc on [DaySummary.money].
        charge = Money.ZERO,
        groupId = groupId,
    )

    private fun summaryOf(vararg classes: TutoringClass): DaySummary =
        DaySummary.of(GroupRules.sessions(classes.toList()))

    @Test
    fun `an empty day says so`() {
        assertEquals(DaySummary.NONE, DaySummary.of(emptyList()))
        assertTrue(DaySummary.NONE.isEmpty)
    }

    @Test
    fun `a day counts its lessons, their length and what they come to`() {
        val summary = summaryOf(
            klass(at = at(9), lessonId = 1, minutes = 60, rate = Money.ofMajor(30)),
            klass(at = at(11), lessonId = 2, minutes = 90, rate = Money.ofMajor(40)),
        )

        assertEquals(2, summary.lessons)
        assertEquals(150, summary.minutes)
        // 30 for the hour, 60 for the hour and a half.
        assertEquals(Money.ofMajor(90), summary.money)
        assertEquals(0, summary.cancelled)
    }

    /** The whole reason this is not [Taught.Totals]: a day still to come is worth what it will bill. */
    @Test
    fun `a lesson not yet taught still counts towards the day`() {
        val scheduled = summaryOf(klass(at = at(9), lessonId = 1, status = LessonStatus.SCHEDULED))

        assertEquals(1, scheduled.lessons)
        assertEquals(Money.ofMajor(30), scheduled.money)
        assertEquals(Taught.Totals.NONE.earned, Taught.byStudent(listOf(klass(at(9), 1))).earned)
    }

    @Test
    fun `a completed lesson is counted at the same number it billed`() {
        val taught = summaryOf(
            klass(
                at = at(9),
                lessonId = 1,
                minutes = 90,
                rate = Money.ofMajor(30),
                status = LessonStatus.COMPLETED,
                attended = Attended.PRESENT,
            ),
        )

        // `ClassRules.rebill` is what a completed class's stored charge comes from, so the day's money
        // and the ledger's cannot disagree — this pins that they are the same arithmetic.
        assertEquals(ClassRules.chargeFor(Money.ofMajor(30), 90), taught.money)
        assertEquals(Money.ofMajor(45), taught.money)
    }

    @Test
    fun `a cancelled lesson is counted apart and adds nothing`() {
        val summary = summaryOf(
            klass(at = at(9), lessonId = 1),
            klass(at = at(11), lessonId = 2, status = LessonStatus.CANCELLED),
        )

        assertEquals(1, summary.lessons)
        assertEquals(60, summary.minutes)
        assertEquals(Money.ofMajor(30), summary.money)
        assertEquals(1, summary.cancelled)
    }

    @Test
    fun `a day of nothing but cancellations is not an empty day`() {
        val summary = summaryOf(klass(at = at(9), lessonId = 1, status = LessonStatus.CANCELLED))

        assertEquals(0, summary.lessons)
        assertEquals(1, summary.cancelled)
        assertTrue("A day that had a lesson and lost it is not the same as a day with none", !summary.isEmpty)
    }

    @Test
    fun `a group lesson is one lesson of the tutor's, and every member's money`() {
        val summary = summaryOf(
            klass(at = at(18), lessonId = 7, studentId = 1, minutes = 90, rate = Money.ofMajor(20), groupId = 5),
            klass(at = at(18), lessonId = 7, studentId = 2, minutes = 90, rate = Money.ofMajor(20), groupId = 5),
            klass(at = at(18), lessonId = 7, studentId = 3, minutes = 90, rate = Money.ofMajor(20), groupId = 5),
        )

        assertEquals("Three in the room is one lesson", 1, summary.lessons)
        assertEquals("And ninety minutes of the tutor's evening, not four and a half hours", 90, summary.minutes)
        // 20/h for 90 minutes is 30 each.
        assertEquals(Money.ofMajor(90), summary.money)
    }

    @Test
    fun `an absence that stops a charge stops the day's money too`() {
        val summary = summaryOf(
            klass(
                at = at(18), lessonId = 7, studentId = 1, groupId = 5,
                status = LessonStatus.COMPLETED, attended = Attended.PRESENT,
            ),
            klass(
                at = at(18), lessonId = 7, studentId = 2, groupId = 5,
                status = LessonStatus.COMPLETED, attended = Attended.ABSENT,
            ),
            klass(
                at = at(18), lessonId = 7, studentId = 3, groupId = 5,
                status = LessonStatus.COMPLETED, attended = Attended.ABSENT_BILLED,
            ),
        )

        assertEquals("Still one lesson, whoever was in it", 1, summary.lessons)
        // Two owe for it: the one who came, and the late cancellation. The plain absence does not.
        assertEquals(Money.ofMajor(60), summary.money)
    }

    // ── parts ────────────────────────────────────────────────────────────────────────────────────
    // Which clauses the sentence has. These rules used to sit inside `format/daySummaryText`, which
    // takes `Resources` — so the two surfaces that must agree about a day (the schedule's heading and
    // the morning digest) rested on a function no JVM test could call.

    private fun summary(lessons: Int, minutes: Int, money: Money, cancelled: Int) =
        DaySummary(lessons = lessons, minutes = minutes, money = money, cancelled = cancelled)

    @Test
    fun `an ordinary day counts its lessons, its hours and its money`() {
        val parts = summary(4, 300, Money.ofMajor(240), cancelled = 0).parts

        assertEquals(
            listOf(
                DaySummary.Part.Lessons(4),
                DaySummary.Part.Hours(300),
                DaySummary.Part.Earned(Money.ofMajor(240)),
            ),
            parts,
        )
    }

    @Test
    fun `a day with something called off names it last`() {
        val parts = summary(2, 120, Money.ofMajor(60), cancelled = 1).parts

        assertEquals(DaySummary.Part.Cancelled(1), parts.last())
        assertEquals(4, parts.size)
    }

    /**
     * There are no hours and no money on a day that was entirely called off, and printing zeroes for
     * them would dress a cancelled day up as a counted one.
     */
    @Test
    fun `a day that was entirely called off says only that`() {
        val parts = summary(0, 0, Money.ZERO, cancelled = 3).parts

        assertEquals(listOf(DaySummary.Part.Cancelled(3)), parts)
    }

    /** Nothing on and nothing called off is nothing to say — not «0 занятий · 0 ч». */
    @Test
    fun `an empty day has no parts at all`() {
        assertEquals(emptyList<DaySummary.Part>(), DaySummary.NONE.parts)
    }

    /** A day whose lessons earn nothing still counts them: free lessons are lessons. */
    @Test
    fun `a day that earned nothing still names its lessons and hours`() {
        val parts = summary(1, 60, Money.ZERO, cancelled = 0).parts

        assertEquals(
            listOf(
                DaySummary.Part.Lessons(1),
                DaySummary.Part.Hours(60),
                DaySummary.Part.Earned(Money.ZERO),
            ),
            parts,
        )
    }
}

/**
 * Where the "you are here" mark goes, which is only ever interesting at a boundary.
 */
class NowMarkerTest {

    private val zone: ZoneId = ZoneId.of("Europe/Minsk")
    private val day: LocalDate = LocalDate.of(2026, 6, 20)
    private fun at(hour: Int, minute: Int = 0): Instant =
        day.atTime(hour, minute).atZone(zone).toInstant()

    private fun sessionAt(hour: Int, id: Long): ClassSession = ClassSession(
        groupId = null,
        classes = listOf(
            TutoringClass(
                id = id,
                lessonId = id,
                studentId = 1,
                subjectId = 1,
                start = at(hour),
                durationMinutes = 60,
            ),
        ),
    )

    private val lessons = listOf(sessionAt(9, 1), sessionAt(12, 2), sessionAt(17, 3))

    private fun positionAt(now: Instant, on: LocalDate = day): Int? =
        NowMarker.positionIn(lessons, on, now, zone)

    @Test
    fun `before the first lesson the mark is at the top`() {
        assertEquals(0, positionAt(at(7)))
    }

    @Test
    fun `between two lessons it sits between them`() {
        assertEquals(1, positionAt(at(10)))
        assertEquals(2, positionAt(at(13)))
    }

    @Test
    fun `after the last lesson it sits at the end`() {
        assertEquals(lessons.size, positionAt(at(23)))
    }

    /** A lesson that has started has started: the mark belongs below it, not above. */
    @Test
    fun `a lesson beginning exactly now counts as begun`() {
        assertEquals(1, positionAt(at(9)))
    }

    @Test
    fun `a day that is not today has no mark`() {
        assertNull(positionAt(at(10), on = day.minusDays(1)))
        assertNull(positionAt(at(10), on = day.plusDays(1)))
    }

    /** An empty today still has a "now" — the day is simply free, and the caller may say so or not. */
    @Test
    fun `an empty today marks position zero rather than nothing`() {
        assertEquals(0, NowMarker.positionIn(emptyList(), day, at(10), zone))
    }

    /**
     * The zone is the app's, not UTC: 00:30 in Minsk is the previous day's 21:30 in UTC, and reading the
     * date off the wrong one puts the mark on the wrong day for the two hours after midnight.
     */
    @Test
    fun `the day is read in the app's zone`() {
        val justAfterMidnight = day.atTime(0, 30).atZone(zone).toInstant()

        assertEquals(0, NowMarker.positionIn(lessons, day, justAfterMidnight, zone))
        assertNull(NowMarker.positionIn(lessons, day.minusDays(1), justAfterMidnight, zone))
    }
}
