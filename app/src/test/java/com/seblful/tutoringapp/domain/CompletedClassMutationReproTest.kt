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
    fun `BUG-9 moving a completed class relocates its recognized income to another month`() {
        val c = completedInMay()
        val mayKey = 2026 * 12 + 4 // May
        val juneKey = 2026 * 12 + 5 // June

        val before = Reports.incomeByMonth(listOf(c), zone)
        assertEquals(listOf(mayKey), before.map { it.monthKey })
        assertEquals(Money(5000), before.single().total)

        // ClassRules.move keeps status COMPLETED and the snapshot charge, only changing start —
        // exactly what the UI's "Move" action does, with no re-billing.
        val moved = ClassRules.move(c, Instant.parse("2026-06-10T10:00:00Z"))

        val after = Reports.incomeByMonth(listOf(moved), zone)
        assertEquals(listOf(juneKey), after.map { it.monthKey }) // income jumped May -> June
        assertEquals(Money(5000), after.single().total)
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
