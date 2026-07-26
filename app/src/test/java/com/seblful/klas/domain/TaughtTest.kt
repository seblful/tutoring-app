package com.seblful.klas.domain

import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * The stat row's arithmetic. The interesting half is [Taught.byGroup]: a group's card counts the
 * tutor's **lessons**, not its members' classes, and getting that wrong reports a three-member group
 * as three times as busy as it was.
 */
class TaughtTest {

    private val start = Instant.parse("2026-06-20T10:00:00Z")

    private fun klass(
        id: Long,
        studentId: Long = 1,
        at: Instant = start,
        minutes: Int = 60,
        status: ClassStatus = ClassStatus.COMPLETED,
        charge: Money = Money.ofMajor(30),
        groupId: Long? = null,
    ) = TutoringClass(
        id = id,
        studentId = studentId,
        subjectId = 1,
        start = at,
        durationMinutes = minutes,
        status = status,
        // Only a completed class carries recognized income, so mirror that here rather than letting a
        // scheduled class contribute a charge the app would never have stored.
        charge = if (status.isBillable) charge else Money.ZERO,
        groupId = groupId,
    )

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
                klass(2, at = start.plusSeconds(86_400), minutes = 90, charge = Money.ofMajor(45)),
                klass(3, at = start.plusSeconds(172_800), status = ClassStatus.SCHEDULED),
                klass(4, at = start.plusSeconds(259_200), status = ClassStatus.CANCELLED),
            )
        )
        assertEquals(2, totals.lessons)
        assertEquals(1, totals.upcoming)
        assertEquals(1, totals.cancelled)
        assertEquals("only completed lessons count towards hours", 150, totals.minutes)
        assertEquals(Money.ofMajor(75), totals.earned)
    }

    @Test
    fun `a cancelled lesson earns nothing and adds no hours`() {
        val totals = Taught.byStudent(listOf(klass(1, status = ClassStatus.CANCELLED)))
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
            )
        )
        assertEquals(1, totals.lessons)
        assertEquals(60, totals.minutes)
        assertEquals("each member owes their own share", Money.ofMajor(60), totals.earned)
    }

    @Test
    fun `a group's lessons are counted per session, not per class`() {
        val second = start.plusSeconds(7 * 86_400)
        val totals = Taught.byGroup(
            listOf(
                klass(1, studentId = 1, groupId = 7, minutes = 90),
                klass(2, studentId = 2, groupId = 7, minutes = 90),
                klass(3, studentId = 1, at = second, groupId = 7, minutes = 60),
                klass(4, studentId = 2, at = second, groupId = 7, minutes = 60),
            )
        )
        assertEquals(2, totals.lessons)
        assertEquals(150, totals.minutes)
    }

    /**
     * A per-member correction — the lesson happened, one absentee's class was cancelled — is still one
     * lesson taught. Requiring every class to be completed would quietly lose it.
     */
    @Test
    fun `a lesson with one absentee cancelled still counts as taught`() {
        val totals = Taught.byGroup(
            listOf(
                klass(1, studentId = 1, groupId = 7, charge = Money.ofMajor(20)),
                klass(2, studentId = 2, groupId = 7, status = ClassStatus.CANCELLED),
            )
        )
        assertEquals(1, totals.lessons)
        assertEquals(0, totals.cancelled)
        assertEquals(60, totals.minutes)
        assertEquals("the absentee is not billed", Money.ofMajor(20), totals.earned)
    }

    @Test
    fun `a group lesson cancelled for everyone counts as cancelled, not taught`() {
        val totals = Taught.byGroup(
            listOf(
                klass(1, studentId = 1, groupId = 7, status = ClassStatus.CANCELLED),
                klass(2, studentId = 2, groupId = 7, status = ClassStatus.CANCELLED),
            )
        )
        assertEquals(0, totals.lessons)
        assertEquals(1, totals.cancelled)
        assertEquals(0, totals.minutes)
    }

    @Test
    fun `a scheduled group lesson is upcoming, once`() {
        val totals = Taught.byGroup(
            listOf(
                klass(1, studentId = 1, groupId = 7, status = ClassStatus.SCHEDULED),
                klass(2, studentId = 2, groupId = 7, status = ClassStatus.SCHEDULED),
            )
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
        val moved = start.plusSeconds(3_600)
        val totals = Taught.byGroup(
            listOf(
                klass(1, studentId = 1, groupId = 7),
                klass(2, studentId = 2, at = moved, groupId = 7),
            )
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
        val evening = start.plusSeconds(7_200)
        val totals = Taught.overall(
            listOf(
                klass(1, studentId = 1, minutes = 60),
                klass(2, studentId = 2, at = evening, minutes = 45),
                klass(3, studentId = 3, at = evening, minutes = 90, groupId = 7),
                klass(4, studentId = 4, at = evening, minutes = 90, groupId = 7),
                klass(5, studentId = 5, at = evening, minutes = 90, groupId = 7),
            )
        )

        assertEquals("two individual lessons plus one group lesson", 3, totals.lessons)
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
}
