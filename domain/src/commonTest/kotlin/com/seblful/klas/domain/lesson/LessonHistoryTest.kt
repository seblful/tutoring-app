package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.lesson.GroupRules
import com.seblful.klas.domain.lesson.SessionEdit
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reports.Taught
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class LessonHistoryTest {

    @Test
    fun `a group session's length is its first class's not the sum of its members'`() {
        val at = Instant.parse("2026-03-02T09:00:00Z")
        val totals = Taught.byGroup(
            listOf(
                completed(id = 1, at = at, groupId = 7, studentId = 1, minutes = 60),
                completed(id = 2, at = at, groupId = 7, studentId = 2, minutes = 90),
            ),
        )

        assertEquals(1, totals.lessons)
        assertEquals(60, totals.minutes)
    }

    @Test
    fun `a member ticked into a cancelled lesson is not billed for it`() {
        val at = Instant.parse("2026-03-02T09:00:00Z")
        val cancelled = ClassSession(
            groupId = 7,
            classes = listOf(
                TutoringClass(
                    id = 1, studentId = 1, subjectId = 1, start = at, durationMinutes = 60,
                    status = LessonStatus.CANCELLED, groupId = 7,
                ),
            ),
        )

        val change = GroupRules.rosterChange(cancelled, setOf(1L, 2L), edit(subjectId = 1), at)
        val joiner = ClassRules.rebill(change.joining.single())
        assertEquals(LessonStatus.CANCELLED, joiner.status)
        assertTrue(joiner.charge.isZero, "a cancelled lesson bills nobody, joiners included")
    }

    @Test
    fun `emptying a booked lesson refuses the edit and retains its roster`() {
        val at = Instant.parse("2026-03-02T09:00:00Z")
        val before = ClassSession(
            groupId = 7,
            classes = listOf(completed(id = 1, at = at, groupId = 7)),
        )

        assertFailsWith<IllegalArgumentException> {
            GroupRules.rosterChange(before, emptySet(), edit(subjectId = 1), at)
        }
        assertEquals(setOf(1L), before.classes.map { it.studentId }.toSet())
    }

    private fun edit(subjectId: Long) = SessionEdit(
        subjectId = subjectId,
        durationMinutes = 60,
        mode = ClassMode.IN_PERSON,
        price = Money.ofMajor(20),
    )

    private fun completed(
        id: Long,
        at: Instant,
        groupId: Long? = null,
        studentId: Long = 1,
        minutes: Int = 60,
    ) = TutoringClass(
        id = id,
        studentId = studentId,
        subjectId = 1,
        start = at,
        durationMinutes = minutes,
        price = Money.ofMajor(30),
        status = LessonStatus.COMPLETED,
        charge = ClassRules.chargeFor(Money.ofMajor(30), minutes),
        groupId = groupId,
    )
}
