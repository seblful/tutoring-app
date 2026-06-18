package com.seblful.tutoringapp.domain

import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ClassRulesTest {

    private val scheduled = TutoringClass(
        id = 1, studentId = 1, subjectId = 1,
        start = Instant.parse("2026-06-18T10:00:00Z"), durationMinutes = 60,
    )

    // --- Auto-completion ---

    @Test
    fun `class auto-completes once its end time passes`() {
        val justBeforeEnd = Instant.parse("2026-06-18T10:59:59Z")
        val atEnd = Instant.parse("2026-06-18T11:00:00Z")
        assertFalse(ClassRules.shouldAutoComplete(scheduled, justBeforeEnd))
        assertTrue(ClassRules.shouldAutoComplete(scheduled, atEnd))
    }

    @Test
    fun `already-completed class does not auto-complete again`() {
        val done = scheduled.copy(status = ClassStatus.COMPLETED)
        assertFalse(ClassRules.shouldAutoComplete(done, Instant.parse("2026-06-19T00:00:00Z")))
    }

    @Test
    fun `cancelled class never auto-completes`() {
        val cancelled = scheduled.copy(status = ClassStatus.CANCELLED)
        assertFalse(ClassRules.shouldAutoComplete(cancelled, Instant.parse("2026-06-19T00:00:00Z")))
    }

    // --- Billing on status change ---

    @Test
    fun `completing captures the current price as the charge`() {
        val completed = ClassRules.applyStatus(scheduled, ClassStatus.COMPLETED, Money.ofMajor(50))
        assertEquals(ClassStatus.COMPLETED, completed.status)
        assertEquals(Money.ofMajor(50), completed.charge)
    }

    @Test
    fun `editing the price never changes an already-completed class`() {
        // Complete at price 50, then "edit" the price to 80 and complete a future class.
        val firstAtOldPrice = ClassRules.applyStatus(scheduled, ClassStatus.COMPLETED, Money.ofMajor(50))
        val secondAtNewPrice = ClassRules.applyStatus(
            scheduled.copy(id = 2), ClassStatus.COMPLETED, Money.ofMajor(80),
        )
        assertEquals(Money.ofMajor(50), firstAtOldPrice.charge) // unchanged
        assertEquals(Money.ofMajor(80), secondAtNewPrice.charge)
    }

    @Test
    fun `cancelling a scheduled class keeps the charge at zero`() {
        val cancelled = ClassRules.applyStatus(scheduled, ClassStatus.CANCELLED, Money.ofMajor(50))
        assertEquals(ClassStatus.CANCELLED, cancelled.status)
        assertTrue(cancelled.charge.isZero)
    }

    @Test
    fun `cancelling an auto-completed class voids the charge it had captured`() {
        val completed = ClassRules.applyStatus(scheduled, ClassStatus.COMPLETED, Money.ofMajor(50))
        val cancelled = ClassRules.applyStatus(completed, ClassStatus.CANCELLED, Money.ofMajor(50))
        assertEquals(ClassStatus.CANCELLED, cancelled.status)
        assertTrue(cancelled.charge.isZero)
    }

    // --- Transition matrix ---

    @Test
    fun `a scheduled class can be completed or cancelled`() {
        assertTrue(ClassRules.canTransition(ClassStatus.SCHEDULED, ClassStatus.COMPLETED))
        assertTrue(ClassRules.canTransition(ClassStatus.SCHEDULED, ClassStatus.CANCELLED))
    }

    @Test
    fun `a completed class can be corrected to cancelled or back to scheduled`() {
        assertTrue(ClassRules.canTransition(ClassStatus.COMPLETED, ClassStatus.CANCELLED))
        assertTrue(ClassRules.canTransition(ClassStatus.COMPLETED, ClassStatus.SCHEDULED))
    }

    @Test
    fun `a cancelled class can be reinstated but not completed directly`() {
        assertTrue(ClassRules.canTransition(ClassStatus.CANCELLED, ClassStatus.SCHEDULED))
        assertFalse(ClassRules.canTransition(ClassStatus.CANCELLED, ClassStatus.COMPLETED))
    }

    @Test
    fun `applying an illegal transition is rejected`() {
        val cancelled = ClassRules.applyStatus(scheduled, ClassStatus.CANCELLED, Money.ZERO)
        assertThrows(IllegalArgumentException::class.java) {
            ClassRules.applyStatus(cancelled, ClassStatus.COMPLETED, Money.ofMajor(50))
        }
    }

    // --- availableTransitions: drives the class actions menu ---

    @Test
    fun `available transitions never include the current status`() {
        // The actions menu must not offer the status the class is already in.
        ClassStatus.entries.forEach { status ->
            assertFalse(
                "availableTransitions($status) must exclude $status",
                status in ClassRules.availableTransitions(status),
            )
        }
    }

    @Test
    fun `available transitions list exactly the legal next statuses`() {
        assertEquals(
            setOf(ClassStatus.COMPLETED, ClassStatus.CANCELLED),
            ClassRules.availableTransitions(ClassStatus.SCHEDULED),
        )
        assertEquals(
            setOf(ClassStatus.CANCELLED, ClassStatus.SCHEDULED),
            ClassRules.availableTransitions(ClassStatus.COMPLETED),
        )
        assertEquals(
            setOf(ClassStatus.SCHEDULED),
            ClassRules.availableTransitions(ClassStatus.CANCELLED),
        )
    }

    @Test
    fun `every offered transition is actually allowed`() {
        ClassStatus.entries.forEach { from ->
            ClassRules.availableTransitions(from).forEach { to ->
                assertTrue("$from -> $to should be allowed", ClassRules.canTransition(from, to))
            }
        }
    }

    // --- Move ---

    @Test
    fun `moving a class updates its start time and keeps it scheduled`() {
        val newStart = Instant.parse("2026-06-20T15:00:00Z")
        val moved = ClassRules.move(scheduled, newStart)
        assertEquals(newStart, moved.start)
        assertEquals(ClassStatus.SCHEDULED, moved.status)
    }
}
