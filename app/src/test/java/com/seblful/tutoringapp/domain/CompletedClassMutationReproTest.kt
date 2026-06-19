package com.seblful.tutoringapp.domain

import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * BUG-9 / BUG-10 rationale. These tests show the raw-domain *consequence* of mutating a COMPLETED
 * class — moving it relocates recognized income to another month; re-attributing it files the old
 * snapshot charge under a different student/subject. This is exactly why the fix gates edit/move on
 * [ClassRules.isMutable] (see ClassRulesTest, ClassRepository.move and ClassEditViewModel.save):
 * the pure functions below still behave this way, but the app no longer lets a completed class
 * reach them. They remain as a guard against anyone re-enabling that path.
 */
class CompletedClassMutationReproTest {

    private val zone = ZoneId.of("Europe/Minsk")

    private fun completedInMay() = TutoringClass(
        id = 1, studentId = 1, subjectId = 1,
        start = Instant.parse("2026-05-10T10:00:00Z"),
        durationMinutes = 60, status = ClassStatus.COMPLETED, charge = Money(5000),
    )

    @Test
    fun `moving a completed class to the future re-plans it and removes its income (no relocation)`() {
        val c = completedInMay()
        val mayKey = 2026 * 12 + 4 // May

        val before = Reports.incomeByMonth(listOf(c), zone)
        assertEquals(listOf(mayKey), before.map { it.monthKey })
        assertEquals(Money(5000), before.single().total)

        // Moving it forward re-plans the class: it becomes scheduled and its charge is voided, so
        // the income does NOT relocate to the new month — it simply stops being recognized until
        // the rescheduled class completes again.
        val now = Instant.parse("2026-05-20T10:00:00Z")
        val moved = ClassRules.move(c, Instant.parse("2026-06-10T10:00:00Z"), now)

        assertEquals(ClassStatus.SCHEDULED, moved.status)
        assertEquals(Money.ZERO, moved.charge)
        assertEquals(emptyList<Reports.MonthlyIncome>(), Reports.incomeByMonth(listOf(moved), zone))
    }

    @Test
    fun `moving a completed class to the past keeps its recognized income`() {
        val c = completedInMay()
        // now is after both old and new start, so the move target stays in the past.
        val now = Instant.parse("2026-06-20T10:00:00Z")
        val moved = ClassRules.move(c, Instant.parse("2026-06-10T10:00:00Z"), now)

        assertEquals(ClassStatus.COMPLETED, moved.status) // status untouched
        assertEquals(Money(5000), moved.charge) // still billed
    }

    @Test
    fun `BUG-10 re-attributing a completed class keeps the old charge under a new student and subject`() {
        // Editing a completed class (existing.copy(studentId=.., subjectId=..)) preserves the charge
        // snapshotted from the ORIGINAL subject's price, then files it under the new owner.
        val original = completedInMay() // student 1, subject 1, charge 5000 (from subject 1's price)
        val reassigned = original.copy(studentId = 2, subjectId = 99)

        // Income now credits the wrong student and a subject the charge never came from.
        assertEquals(Money(5000), Reports.incomeByStudent(listOf(reassigned))[2])
        assertEquals(null, Reports.incomeByStudent(listOf(reassigned))[1])
        assertEquals(Money(5000), Reports.incomeBySubject(listOf(reassigned))[99])
    }
}
