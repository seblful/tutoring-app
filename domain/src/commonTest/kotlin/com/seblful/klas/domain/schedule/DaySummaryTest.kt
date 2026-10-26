package com.seblful.klas.domain.schedule

import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reports.Taught
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class DaySummaryTest {

    private val zone: TimeZone = TimeZone.of("Europe/Minsk")
    private val day: LocalDate = LocalDate(2026, 6, 20)
    private fun at(hour: Int): Instant = day.atTime(hour, 0).toInstant(zone)

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
        charge: Money = ClassRules.chargeFor(rate, minutes),
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
        charge = charge,
        groupId = groupId,
    )

    private fun summaryOf(vararg classes: TutoringClass): DaySummary =
        DaySummary.of(SessionRules.sessions(classes.toList()))

    @Test
    fun `an empty day says so`() {
        assertEquals(DaySummary.NONE, DaySummary.of(emptyList()))
        assertTrue(DaySummary.NONE.isEmpty)
    }

    @Test
    fun `a day counts its lessons - their length and what they billed`() {
        val summary = summaryOf(
            klass(at = at(9), lessonId = 1, minutes = 60, rate = Money.ofMajor(30), status = LessonStatus.COMPLETED),
            klass(at = at(11), lessonId = 2, minutes = 90, rate = Money.ofMajor(40), status = LessonStatus.COMPLETED),
        )

        assertEquals(2, summary.lessons)
        assertEquals(150, summary.minutes)
        assertEquals(Money.ofMajor(90), summary.money)
        assertEquals(0, summary.cancelled)
    }

    @Test
    fun `a lesson not yet taught counts its time towards the day, but no money`() {
        val scheduled = summaryOf(klass(at = at(9), lessonId = 1, status = LessonStatus.SCHEDULED))

        assertEquals(1, scheduled.lessons)
        assertEquals(60, scheduled.minutes)
        assertEquals(Money.ZERO, scheduled.money)
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

        assertEquals(ClassRules.chargeFor(Money.ofMajor(30), 90), taught.money)
        assertEquals(Money.ofMajor(45), taught.money)
    }

    @Test
    fun `the day's money is what its rows billed, as the balances and the reports sum it`() {
        val billed = summaryOf(
            klass(at = at(9), lessonId = 1, status = LessonStatus.COMPLETED, charge = Money.ofMajor(7)),
            klass(at = at(11), lessonId = 2, status = LessonStatus.COMPLETED, charge = Money.ZERO),
        )

        assertEquals(Money.ofMajor(7), billed.money)
    }

    @Test
    fun `a cancelled lesson is counted apart and adds nothing`() {
        val summary = summaryOf(
            klass(at = at(9), lessonId = 1, status = LessonStatus.COMPLETED),
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
        assertTrue(!summary.isEmpty, "A day that had a lesson and lost it is not the same as a day with none")
    }

    @Test
    fun `a group lesson is one lesson of the tutor's - and every member's money`() {
        fun member(id: Long) = klass(
            at = at(18), lessonId = 7, studentId = id, minutes = 90, rate = Money.ofMajor(20), groupId = 5,
            status = LessonStatus.COMPLETED, attended = Attended.PRESENT,
        )
        val summary = summaryOf(member(1), member(2), member(3))

        assertEquals(1, summary.lessons, "Three in the room is one lesson")
        assertEquals(90, summary.minutes, "And ninety minutes of the tutor's evening, not four and a half hours")
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

        assertEquals(1, summary.lessons, "Still one lesson, whoever was in it")
        // Two owe for it: the one who came, and the late cancellation. The plain absence does not.
        assertEquals(Money.ofMajor(60), summary.money)
    }

    // ── parts ────────────────────────────────────────────────────────────────────────────────────
    // Which clauses the sentence has. These rules used to sit inside `format/daySummaryText`, which
    // takes `Resources` — so the two surfaces that must agree about a day (the schedule's heading and

    private fun summary(lessons: Int, minutes: Int, money: Money, cancelled: Int) =
        DaySummary(lessons = lessons, minutes = minutes, money = money, cancelled = cancelled)

    @Test
    fun `an ordinary day counts its lessons - its hours and its money`() {
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

    @Test
    fun `a day that was entirely called off says only that`() {
        val parts = summary(0, 0, Money.ZERO, cancelled = 3).parts

        assertEquals(listOf(DaySummary.Part.Cancelled(3)), parts)
    }

    @Test
    fun `an empty day has no parts at all`() {
        assertEquals(emptyList<DaySummary.Part>(), DaySummary.NONE.parts)
    }

    @Test
    fun `a day that billed nothing - still to come - names its lessons and hours, and no money`() {
        val parts = summary(1, 60, Money.ZERO, cancelled = 0).parts

        assertEquals(listOf(DaySummary.Part.Lessons(1), DaySummary.Part.Hours(60)), parts)
    }
}

class NowMarkerTest {

    private val zone: TimeZone = TimeZone.of("Europe/Minsk")
    private val day: LocalDate = LocalDate(2026, 6, 20)
    private fun at(hour: Int, minute: Int = 0): Instant =
        day.atTime(hour, minute).toInstant(zone)

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

    @Test
    fun `a lesson beginning exactly now counts as begun`() {
        assertEquals(1, positionAt(at(9)))
    }

    @Test
    fun `a day that is not today has no mark`() {
        assertNull(positionAt(at(10), on = day.minus(1, DateTimeUnit.DAY)))
        assertNull(positionAt(at(10), on = day.plus(1, DateTimeUnit.DAY)))
    }

    @Test
    fun `an empty today marks position zero rather than nothing`() {
        assertEquals(0, NowMarker.positionIn(emptyList(), day, at(10), zone))
    }

    @Test
    fun `the day is read in the app's zone`() {
        val justAfterMidnight = day.atTime(0, 30).toInstant(zone)

        assertEquals(0, NowMarker.positionIn(lessons, day, justAfterMidnight, zone))
        assertNull(NowMarker.positionIn(lessons, day.minus(1, DateTimeUnit.DAY), justAfterMidnight, zone))
    }
}
