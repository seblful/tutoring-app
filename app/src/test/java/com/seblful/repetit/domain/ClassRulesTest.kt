package com.seblful.repetit.domain

import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.TutoringClass
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
    fun `completing scales the hourly rate by the lesson duration`() {
        val rate = Money.ofMajor(30) // 30/hour
        val ninetyMin = scheduled.copy(price = rate, durationMinutes = 90)
        val thirtyMin = scheduled.copy(price = rate, durationMinutes = 30)
        assertEquals(Money.ofMajor(45), ClassRules.applyStatus(ninetyMin, ClassStatus.COMPLETED).charge)
        assertEquals(Money.ofMajor(15), ClassRules.applyStatus(thirtyMin, ClassStatus.COMPLETED).charge)
    }

    @Test
    fun `chargeFor rounds to the nearest kopeck`() {
        // 30/h for 50 min = 25 exactly.
        assertEquals(Money.ofMajor(25), ClassRules.chargeFor(Money.ofMajor(30), 50))
        // 20/h for 77 min = 25.6667 -> 25,67 (rounded half up).
        assertEquals(Money(2567), ClassRules.chargeFor(Money(2000), 77))
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

    // --- rebill: keeps a class's charge consistent with its details after an edit ---

    @Test
    fun `rebill recomputes a completed class's charge from its current price and duration`() {
        // A completed class booked at 50/h for 60 min, then edited to 30/h for 90 min.
        val completed = ClassRules.applyStatus(priced, ClassStatus.COMPLETED)
        val edited = completed.copy(price = Money.ofMajor(30), durationMinutes = 90)
        assertEquals(Money.ofMajor(45), ClassRules.rebill(edited).charge)
    }

    @Test
    fun `rebill re-attributes a completed class's charge when its owner changes`() {
        // Editing a completed class to a new student and subject derives the charge from the current
        // price rather than carrying the old snapshot — so income credits the new owner correctly.
        val completed = ClassRules.applyStatus(priced, ClassStatus.COMPLETED)
        val reassigned = completed.copy(studentId = 2, subjectId = 99, price = Money.ofMajor(40))
        val rebilled = ClassRules.rebill(reassigned)
        assertEquals(Money.ofMajor(40), rebilled.charge)
        assertEquals(2, rebilled.studentId)
        assertEquals(99, rebilled.subjectId)
    }

    @Test
    fun `rebill voids the charge for a non-billable class`() {
        val completed = ClassRules.applyStatus(priced, ClassStatus.COMPLETED)
        assertEquals(Money.ZERO, ClassRules.rebill(completed.copy(status = ClassStatus.SCHEDULED)).charge)
        assertEquals(Money.ZERO, ClassRules.rebill(completed.copy(status = ClassStatus.CANCELLED)).charge)
    }
}
