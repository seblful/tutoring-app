package com.seblful.tutoringapp.domain

import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * BUG-9 / BUG-10 rationale. A completed class may now be edited and moved, so the danger is no
 * longer avoided by forbidding the path — it is neutralized by recomputing the charge:
 *
 * - Moving a completed class to the future re-plans it ([ClassRules.move] voids the charge), so
 *   income is not silently relocated to another month; moving it into the past keeps its charge.
 * - Editing a completed class re-bills it ([ClassRules.rebill], called from ClassEditViewModel.save):
 *   the charge is recomputed from the edited price and duration, so it is attributed to the current
 *   student/subject at the current rate instead of carrying a stale snapshot from the old owner.
 *
 * These tests pin that behavior so the BUG-9/BUG-10 mis-attribution can't return.
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
    fun `BUG-10 re-attributing a completed class re-bills it under the new student and subject`() {
        // Editing a completed class to a new owner mirrors ClassEditViewModel.save: copy the new
        // student/subject/price, then rebill. The charge is recomputed from the edited price (40/h
        // for 60 min = 4000) rather than carried over, so income credits the new owner correctly.
        val original = completedInMay() // student 1, subject 1, charge 5000 (from subject 1's price)
        val reassigned = ClassRules.rebill(
            original.copy(studentId = 2, subjectId = 99, price = Money(4000)),
        )

        assertEquals(Money(4000), reassigned.charge)
        // Income credits the new owner at the new rate, and nothing is left under the old student.
        assertEquals(Money(4000), Reports.incomeByStudent(listOf(reassigned))[2])
        assertEquals(null, Reports.incomeByStudent(listOf(reassigned))[1])
        assertEquals(Money(4000), Reports.incomeBySubject(listOf(reassigned))[99])
    }
}
