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

    private val priced = scheduled.copy(price = Money.ofMajor(50))

    @Test
    fun `completing bills the class's own booked price`() {
        val completed = ClassRules.applyStatus(priced, ClassStatus.COMPLETED)
        assertEquals(ClassStatus.COMPLETED, completed.status)
        assertEquals(Money.ofMajor(50), completed.charge)
    }

    @Test
    fun `each class bills its own booked price independently`() {
        val first = ClassRules.applyStatus(scheduled.copy(price = Money.ofMajor(50)), ClassStatus.COMPLETED)
        val second = ClassRules.applyStatus(scheduled.copy(id = 2, price = Money.ofMajor(80)), ClassStatus.COMPLETED)
        assertEquals(Money.ofMajor(50), first.charge)
        assertEquals(Money.ofMajor(80), second.charge)
    }

    @Test
    fun `cancelling a scheduled class keeps the charge at zero`() {
        val cancelled = ClassRules.applyStatus(priced, ClassStatus.CANCELLED)
        assertEquals(ClassStatus.CANCELLED, cancelled.status)
        assertTrue(cancelled.charge.isZero)
    }

    @Test
    fun `cancelling an auto-completed class voids the charge it had captured`() {
        val completed = ClassRules.applyStatus(priced, ClassStatus.COMPLETED)
        val cancelled = ClassRules.applyStatus(completed, ClassStatus.CANCELLED)
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
    fun `a completed class can be cancelled but not sent back to scheduled`() {
        assertTrue(ClassRules.canTransition(ClassStatus.COMPLETED, ClassStatus.CANCELLED))
        assertFalse(ClassRules.canTransition(ClassStatus.COMPLETED, ClassStatus.SCHEDULED))
    }

    @Test
    fun `a cancelled class can be reinstated but not completed directly`() {
        assertTrue(ClassRules.canTransition(ClassStatus.CANCELLED, ClassStatus.SCHEDULED))
        assertFalse(ClassRules.canTransition(ClassStatus.CANCELLED, ClassStatus.COMPLETED))
    }

    @Test
    fun `applying an illegal transition is rejected`() {
        val cancelled = ClassRules.applyStatus(scheduled, ClassStatus.CANCELLED)
        assertThrows(IllegalArgumentException::class.java) {
            ClassRules.applyStatus(cancelled, ClassStatus.COMPLETED)
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
            setOf(ClassStatus.CANCELLED),
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
    fun `moving a scheduled class to the future keeps it scheduled`() {
        val now = Instant.parse("2026-06-19T00:00:00Z")
        val newStart = Instant.parse("2026-06-20T15:00:00Z")
        val moved = ClassRules.move(scheduled, newStart, now)
        assertEquals(newStart, moved.start)
        assertEquals(ClassStatus.SCHEDULED, moved.status)
    }

    @Test
    fun `moving a class to the future re-plans it and voids any captured charge`() {
        val completed = ClassRules.applyStatus(priced, ClassStatus.COMPLETED)
        val now = Instant.parse("2026-06-19T00:00:00Z")
        val moved = ClassRules.move(completed, Instant.parse("2026-06-25T10:00:00Z"), now)
        assertEquals(ClassStatus.SCHEDULED, moved.status)
        assertTrue(moved.charge.isZero)
    }

    @Test
    fun `moving a class to the past leaves its status and charge unchanged`() {
        val completed = ClassRules.applyStatus(priced, ClassStatus.COMPLETED)
        val now = Instant.parse("2026-06-30T00:00:00Z")
        val moved = ClassRules.move(completed, Instant.parse("2026-06-25T10:00:00Z"), now)
        assertEquals(ClassStatus.COMPLETED, moved.status)
        assertEquals(Money.ofMajor(50), moved.charge)
    }

    // --- isMutable: gates edit/move so historical income can't be rewritten ---

    @Test
    fun `only a scheduled class is mutable`() {
        assertTrue(ClassRules.isMutable(ClassStatus.SCHEDULED))
        assertFalse(ClassRules.isMutable(ClassStatus.COMPLETED))
        assertFalse(ClassRules.isMutable(ClassStatus.CANCELLED))
    }
}
