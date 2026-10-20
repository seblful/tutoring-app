package com.seblful.klas.domain.reports

import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TaughtTest {

    private val start = Instant.parse("2026-06-20T10:00:00Z")

    private fun klass(
        id: Long,
        studentId: Long = 1,
        at: Instant = start,
        minutes: Int = 60,
        status: LessonStatus = LessonStatus.COMPLETED,
        attended: Attended = Attended.EXPECTED,
        charge: Money = Money.ofMajor(30),
        groupId: Long? = null,
    ) = TutoringClass(
        id = id,
        lessonId = lessonIdFor(groupId, at, id),
        studentId = studentId,
        subjectId = 1,
        start = at,
        durationMinutes = minutes,
        status = status,
        attended = attended,
        charge = if (status.isBillable && attended.bills) charge else Money.ZERO,
        groupId = groupId,
    )

    private val lessonIds = mutableMapOf<Pair<Long, Any>, Long>()

    private fun lessonIdFor(groupId: Long?, start: Any, id: Long): Long =
        if (groupId == null) id else lessonIds.getOrPut(groupId to start) { 1_000L + lessonIds.size }

    @Test
    fun `no classes totals to nothing`() {
        assertEquals(Taught.Totals.NONE, Taught.byStudent(emptyList()))
        assertEquals(Taught.Totals.NONE, Taught.byGroup(emptyList()))
    }

    @Test
    fun `a student's totals count completed lessons, their minutes and their charges`() {
        val totals = Taught.byStudent(
            listOf(
                klass(1, minutes = 60, charge = Money.ofMajor(30)),
                klass(2, at = start.plus((86_400).seconds), minutes = 90, charge = Money.ofMajor(45)),
                klass(3, at = start.plus((172_800).seconds), status = LessonStatus.SCHEDULED),
                klass(4, at = start.plus((259_200).seconds), status = LessonStatus.CANCELLED),
            ),
        )
        assertEquals(2, totals.lessons)
        assertEquals(1, totals.upcoming)
        assertEquals(1, totals.cancelled)
        assertEquals(150, totals.minutes, "only completed lessons count towards hours")
        assertEquals(Money.ofMajor(75), totals.earned)
    }

    @Test
    fun `a cancelled lesson earns nothing and adds no hours`() {
        val totals = Taught.byStudent(listOf(klass(1, status = LessonStatus.CANCELLED)))
        assertEquals(0, totals.lessons)
        assertEquals(0, totals.minutes)
        assertEquals(Money.ZERO, totals.earned)
    }

    /**
     * The whole reason [Taught.byGroup] exists. Three members at one hour is **one** lesson of one
     * hour — the tutor's hour — while the money is genuinely three charges, because each member owes
     * their own.
     */
    @Test
    fun `a group lesson is one lesson and one hour however many members it has`() {
        val totals = Taught.byGroup(
            listOf(
                klass(1, studentId = 1, groupId = 7, charge = Money.ofMajor(20)),
                klass(2, studentId = 2, groupId = 7, charge = Money.ofMajor(20)),
                klass(3, studentId = 3, groupId = 7, charge = Money.ofMajor(20)),
            ),
        )
        assertEquals(1, totals.lessons)
        assertEquals(60, totals.minutes)
        assertEquals(Money.ofMajor(60), totals.earned, "each member owes their own share")
    }

    @Test
    fun `a group's lessons are counted per session, not per class`() {
        val second = start.plus((7 * 86_400).seconds)
        val totals = Taught.byGroup(
            listOf(
                klass(1, studentId = 1, groupId = 7, minutes = 90),
                klass(2, studentId = 2, groupId = 7, minutes = 90),
                klass(3, studentId = 1, at = second, groupId = 7, minutes = 60),
                klass(4, studentId = 2, at = second, groupId = 7, minutes = 60),
            ),
        )
        assertEquals(2, totals.lessons)
        assertEquals(150, totals.minutes)
    }

    /**
     * A per-member correction — the lesson happened, one member was marked absent — is still one lesson
     * taught. Requiring every class to bill would quietly lose it.
     */
    @Test
    fun `a lesson with one absentee still counts as taught`() {
        val totals = Taught.byGroup(
            listOf(
                klass(1, studentId = 1, groupId = 7, charge = Money.ofMajor(20)),
                klass(2, studentId = 2, groupId = 7, attended = Attended.ABSENT),
            ),
        )
        assertEquals(1, totals.lessons)
        assertEquals(0, totals.cancelled)
        assertEquals(60, totals.minutes)
        assertEquals(Money.ofMajor(20), totals.earned, "the absentee is not billed")
    }

    @Test
    fun `a group lesson cancelled for everyone counts as cancelled, not taught`() {
        val totals = Taught.byGroup(
            listOf(
                klass(1, studentId = 1, groupId = 7, status = LessonStatus.CANCELLED),
                klass(2, studentId = 2, groupId = 7, status = LessonStatus.CANCELLED),
            ),
        )
        assertEquals(0, totals.lessons)
        assertEquals(1, totals.cancelled)
        assertEquals(0, totals.minutes)
    }

    @Test
    fun `a scheduled group lesson is upcoming, once`() {
        val totals = Taught.byGroup(
            listOf(
                klass(1, studentId = 1, groupId = 7, status = LessonStatus.SCHEDULED),
                klass(2, studentId = 2, groupId = 7, status = LessonStatus.SCHEDULED),
            ),
        )
        assertEquals(0, totals.lessons)
        assertEquals(1, totals.upcoming)
    }

    /**
     * A member's class moved on its own leaves its session (the session key *is* the start time), so
     * it becomes a lesson of its own — which is the honest count: the tutor now teaches two.
     */
    @Test
    fun `a member's class moved off the session becomes its own lesson`() {
        val moved = start.plus((3_600).seconds)
        val totals = Taught.byGroup(
            listOf(
                klass(1, studentId = 1, groupId = 7),
                klass(2, studentId = 2, at = moved, groupId = 7),
            ),
        )
        assertEquals(2, totals.lessons)
        assertEquals(120, totals.minutes)
    }

    /**
     * The reports screen's headline count. Each half must be counted by its own rule: three members
     * in one room is one lesson of the tutor's, two students on separate hours is two.
     */
    @Test
    fun `overall counts a group lesson once and every individual class on its own`() {
        val evening = start.plus((7_200).seconds)
        val totals = Taught.overall(
            listOf(
                klass(1, studentId = 1, minutes = 60),
                klass(2, studentId = 2, at = evening, minutes = 45),
                klass(3, studentId = 3, at = evening, minutes = 90, groupId = 7),
                klass(4, studentId = 4, at = evening, minutes = 90, groupId = 7),
                klass(5, studentId = 5, at = evening, minutes = 90, groupId = 7),
            ),
        )

        assertEquals(3, totals.lessons, "two individual lessons plus one group lesson")
        // The tutor's own hours: 60 + 45 + the group's single 90, not three times 90.
        assertEquals(195, totals.minutes)
        // Money is the other way round — every member owes their share, so all five charges count.
        assertEquals(Money.ofMajor(150), totals.earned)
    }

    @Test
    fun `overall on one kind alone agrees with that kind's own rule`() {
        val individual = listOf(klass(1, studentId = 1), klass(2, studentId = 2))
        val group = listOf(klass(3, studentId = 1, groupId = 7), klass(4, studentId = 2, groupId = 7))

        assertEquals(Taught.byStudent(individual), Taught.overall(individual))
        assertEquals(Taught.byGroup(group), Taught.overall(group))
    }

    @Test
    fun `bySessions agrees with byGroup over the same classes`() {
        val classes = listOf(
            klass(1, studentId = 1, groupId = 7, status = LessonStatus.COMPLETED),
            klass(2, studentId = 2, groupId = 7, attended = Attended.ABSENT),
            klass(3, studentId = 1, groupId = 7, at = start.plus((86_400).seconds), status = LessonStatus.SCHEDULED),
        )

        assertEquals(Taught.byGroup(classes), Taught.bySessions(SessionRules.sessions(classes)))
    }

    private fun totals(upcoming: Int = 0, cancelled: Int = 0) =
        Taught.Totals(lessons = 4, upcoming = upcoming, cancelled = cancelled, minutes = 240, earned = Money.ZERO)

    @Test
    fun `lessons still to come are what the hint says`() {
        assertEquals(Taught.Hint.Upcoming(3), totals(upcoming = 3).hint)
    }

    @Test
    fun `upcoming beats cancelled when there is both`() {
        assertEquals(Taught.Hint.Upcoming(2), totals(upcoming = 2, cancelled = 5).hint)
    }

    @Test
    fun `cancelled is said only when nothing is still to come`() {
        assertEquals(Taught.Hint.Cancelled(5), totals(cancelled = 5).hint)
    }

    @Test
    fun `neither leaves the hint unsaid`() {
        assertNull(totals().hint)
    }
}
