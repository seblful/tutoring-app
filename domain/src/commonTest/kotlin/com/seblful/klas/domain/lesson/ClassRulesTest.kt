package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ClassRulesTest {

    private val scheduled = TutoringClass(
        id = 1, studentId = 1, subjectId = 1,
        start = Instant.parse("2026-06-18T10:00:00Z"), durationMinutes = 60,
    )

    @Test
    fun `class auto-completes once its end time passes`() {
        val justBeforeEnd = Instant.parse("2026-06-18T10:59:59Z")
        val atEnd = Instant.parse("2026-06-18T11:00:00Z")
        assertFalse(ClassRules.shouldAutoComplete(scheduled, justBeforeEnd))
        assertTrue(ClassRules.shouldAutoComplete(scheduled, atEnd))
    }

    @Test
    fun `already-completed class does not auto-complete again`() {
        val done = scheduled.copy(status = LessonStatus.COMPLETED)
        assertFalse(ClassRules.shouldAutoComplete(done, Instant.parse("2026-06-19T00:00:00Z")))
    }

    @Test
    fun `cancelled class never auto-completes`() {
        val cancelled = scheduled.copy(status = LessonStatus.CANCELLED)
        assertFalse(ClassRules.shouldAutoComplete(cancelled, Instant.parse("2026-06-19T00:00:00Z")))
    }

    @Test
    fun `the next auto-completion is the earliest end still to come`() {
        val later = scheduled.copy(id = 2, start = Instant.parse("2026-06-18T12:00:00Z"))
        val running = Instant.parse("2026-06-18T10:30:00Z")

        assertEquals(
            Instant.parse("2026-06-18T11:00:00Z"),
            ClassRules.nextAutoCompletion(listOf(later, scheduled), running),
        )
    }

    @Test
    fun `lessons already overdue or settled do not set the next auto-completion`() {
        val settled = scheduled.copy(
            id = 2,
            start = Instant.parse("2026-06-18T12:00:00Z"),
            status = LessonStatus.CANCELLED,
        )
        val atEnd = Instant.parse("2026-06-18T11:00:00Z")

        assertEquals(null, ClassRules.nextAutoCompletion(listOf(scheduled, settled), atEnd))
    }

    private val priced = scheduled.copy(price = Money.ofMajor(50))

    @Test
    fun `completing bills the class's own booked price`() {
        val completed = ClassRules.applyStatus(priced, LessonStatus.COMPLETED)
        assertEquals(LessonStatus.COMPLETED, completed.status)
        assertEquals(Money.ofMajor(50), completed.charge)
    }

    @Test
    fun `completing scales the hourly rate by the lesson duration`() {
        val rate = Money.ofMajor(30) // 30/hour
        val ninetyMin = scheduled.copy(price = rate, durationMinutes = 90)
        val thirtyMin = scheduled.copy(price = rate, durationMinutes = 30)
        assertEquals(Money.ofMajor(45), ClassRules.applyStatus(ninetyMin, LessonStatus.COMPLETED).charge)
        assertEquals(Money.ofMajor(15), ClassRules.applyStatus(thirtyMin, LessonStatus.COMPLETED).charge)
    }

    @Test
    fun `chargeFor rounds to the nearest kopeck`() {
        // 30/h for 50 min = 25 exactly.
        assertEquals(Money.ofMajor(25), ClassRules.chargeFor(Money.ofMajor(30), 50))
        // 20/h for 77 min = 25.6667 -> 25,67 (rounded half up).
        assertEquals(Money(2567), ClassRules.chargeFor(Money(2000), 77))
    }

    /**
     * Half-up means the same thing below zero, which `/` does not deliver: it truncates towards zero,
     * so it rounds up in magnitude there. −2 kopecks an hour for half an hour is −1 kopeck exactly and
     * must come back as −1; truncation gave 0, silently forgiving the kopeck.
     *
     * A negative rate is not something the money inputs accept, so this is about the arithmetic being
     * right rather than about a case the tutor can reach — but it is the one line that turns a rate
     * into money owed, and "half up" should not mean two different things either side of zero.
     */
    @Test
    fun `chargeFor rounds half up below zero too - not towards zero`() {
        assertEquals(Money(-1), ClassRules.chargeFor(Money(-2), 30))
        // Exactly on a half: -1,5 kopecks rounds up to -1, in the same direction 1,5 rounds to 2.
        assertEquals(Money(-1), ClassRules.chargeFor(Money(-3), 30))
        assertEquals(Money(2), ClassRules.chargeFor(Money(3), 30))
        // And the ordinary positive cases are untouched.
        assertEquals(Money(1), ClassRules.chargeFor(Money(2), 30))
    }

    @Test
    fun `each class bills its own booked price independently`() {
        val first = ClassRules.applyStatus(scheduled.copy(price = Money.ofMajor(50)), LessonStatus.COMPLETED)
        val second = ClassRules.applyStatus(scheduled.copy(id = 2, price = Money.ofMajor(80)), LessonStatus.COMPLETED)
        assertEquals(Money.ofMajor(50), first.charge)
        assertEquals(Money.ofMajor(80), second.charge)
    }

    @Test
    fun `cancelling a scheduled class keeps the charge at zero`() {
        val cancelled = ClassRules.applyStatus(priced, LessonStatus.CANCELLED)
        assertEquals(LessonStatus.CANCELLED, cancelled.status)
        assertTrue(cancelled.charge.isZero)
    }

    @Test
    fun `cancelling an auto-completed class voids the charge it had captured`() {
        val completed = ClassRules.applyStatus(priced, LessonStatus.COMPLETED)
        val cancelled = ClassRules.applyStatus(completed, LessonStatus.CANCELLED)
        assertEquals(LessonStatus.CANCELLED, cancelled.status)
        assertTrue(cancelled.charge.isZero)
    }

    // --- Transition matrix ---

    @Test
    fun `a scheduled class can be completed or cancelled`() {
        assertTrue(ClassRules.canTransition(LessonStatus.SCHEDULED, LessonStatus.COMPLETED))
        assertTrue(ClassRules.canTransition(LessonStatus.SCHEDULED, LessonStatus.CANCELLED))
    }

    @Test
    fun `a completed class can be cancelled but not sent back to scheduled`() {
        assertTrue(ClassRules.canTransition(LessonStatus.COMPLETED, LessonStatus.CANCELLED))
        assertFalse(ClassRules.canTransition(LessonStatus.COMPLETED, LessonStatus.SCHEDULED))
    }

    @Test
    fun `a cancelled class can be reinstated but not completed directly`() {
        assertTrue(ClassRules.canTransition(LessonStatus.CANCELLED, LessonStatus.SCHEDULED))
        assertFalse(ClassRules.canTransition(LessonStatus.CANCELLED, LessonStatus.COMPLETED))
    }

    @Test
    fun `applying an illegal transition is rejected`() {
        val cancelled = ClassRules.applyStatus(scheduled, LessonStatus.CANCELLED)
        assertFailsWith<IllegalArgumentException> {
            ClassRules.applyStatus(cancelled, LessonStatus.COMPLETED)
        }
    }

    // --- availableTransitions: drives the class actions menu ---

    @Test
    fun `available transitions never include the current status`() {
        // The actions menu must not offer the status the class is already in.
        LessonStatus.entries.forEach { status ->
            assertFalse(
                status in ClassRules.availableTransitions(status),
                "availableTransitions($status) must exclude $status",
            )
        }
    }

    @Test
    fun `available transitions list exactly the legal next statuses`() {
        assertEquals(
            setOf(LessonStatus.COMPLETED, LessonStatus.CANCELLED),
            ClassRules.availableTransitions(LessonStatus.SCHEDULED),
        )
        assertEquals(
            setOf(LessonStatus.CANCELLED),
            ClassRules.availableTransitions(LessonStatus.COMPLETED),
        )
        assertEquals(
            setOf(LessonStatus.SCHEDULED),
            ClassRules.availableTransitions(LessonStatus.CANCELLED),
        )
    }

    @Test
    fun `every offered transition is actually allowed`() {
        LessonStatus.entries.forEach { from ->
            ClassRules.availableTransitions(from).forEach { to ->
                assertTrue(ClassRules.canTransition(from, to), "$from -> $to should be allowed")
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
        assertEquals(LessonStatus.SCHEDULED, moved.status)
    }

    @Test
    fun `moving a class to the future re-plans it and voids any captured charge`() {
        val completed = ClassRules.applyStatus(priced, LessonStatus.COMPLETED)
        val now = Instant.parse("2026-06-19T00:00:00Z")
        val moved = ClassRules.move(completed, Instant.parse("2026-06-25T10:00:00Z"), now)
        assertEquals(LessonStatus.SCHEDULED, moved.status)
        assertTrue(moved.charge.isZero)
    }

    @Test
    fun `moving a class to the past leaves its status and charge unchanged`() {
        val completed = ClassRules.applyStatus(priced, LessonStatus.COMPLETED)
        val now = Instant.parse("2026-06-30T00:00:00Z")
        val moved = ClassRules.move(completed, Instant.parse("2026-06-25T10:00:00Z"), now)
        assertEquals(LessonStatus.COMPLETED, moved.status)
        assertEquals(Money.ofMajor(50), moved.charge)
    }

    // --- rebill: keeps a class's charge consistent with its details after an edit ---

    @Test
    fun `rebill recomputes a completed class's charge from its current price and duration`() {
        // A completed class booked at 50/h for 60 min, then edited to 30/h for 90 min.
        val completed = ClassRules.applyStatus(priced, LessonStatus.COMPLETED)
        val edited = completed.copy(price = Money.ofMajor(30), durationMinutes = 90)
        assertEquals(Money.ofMajor(45), ClassRules.rebill(edited).charge)
    }

    @Test
    fun `rebill re-attributes a completed class's charge when its owner changes`() {
        val completed = ClassRules.applyStatus(priced, LessonStatus.COMPLETED)
        val reassigned = completed.copy(studentId = 2, subjectId = 99, price = Money.ofMajor(40))
        val rebilled = ClassRules.rebill(reassigned)
        assertEquals(Money.ofMajor(40), rebilled.charge)
        assertEquals(2, rebilled.studentId)
        assertEquals(99, rebilled.subjectId)
    }

    @Test
    fun `rebill voids the charge for a non-billable class`() {
        val completed = ClassRules.applyStatus(priced, LessonStatus.COMPLETED)
        assertEquals(Money.ZERO, ClassRules.rebill(completed.copy(status = LessonStatus.SCHEDULED)).charge)
        assertEquals(Money.ZERO, ClassRules.rebill(completed.copy(status = LessonStatus.CANCELLED)).charge)
    }

    @Test
    fun `edit re-bills a completed class kept in the past from its new price and duration`() {
        val completed = ClassRules.applyStatus(priced, LessonStatus.COMPLETED)
        val now = Instant.parse("2026-06-30T00:00:00Z")
        val pastStart = Instant.parse("2026-06-25T10:00:00Z")
        val resolved = ClassRules.edit(
            completed,
            ClassEdit(
                studentId = 2,
                subjectId = 99,
                durationMinutes = 90,
                price = Money.ofMajor(30),
                mode = completed.mode,
            ),
            pastStart, now,
        )
        assertEquals(LessonStatus.COMPLETED, resolved.status)
        assertEquals(Money.ofMajor(45), resolved.charge)
        assertEquals(pastStart, resolved.start)
        assertEquals(2, resolved.studentId)
        assertEquals(99, resolved.subjectId)
    }

    @Test
    fun `edit moving a completed class to the future re-plans it and voids the charge`() {
        val completed = ClassRules.applyStatus(priced, LessonStatus.COMPLETED)
        val now = Instant.parse("2026-06-19T00:00:00Z")
        val futureStart = Instant.parse("2026-06-25T10:00:00Z")
        val resolved = ClassRules.edit(
            completed,
            ClassEdit(
                studentId = 1,
                subjectId = 1,
                durationMinutes = 60,
                price = Money.ofMajor(30),
                mode = completed.mode,
            ),
            futureStart, now,
        )
        assertEquals(LessonStatus.SCHEDULED, resolved.status)
        assertTrue(resolved.charge.isZero)
    }

    @Test
    fun `edit equals move then rebill`() {
        val now = Instant.parse("2026-06-19T00:00:00Z")
        val start = Instant.parse("2026-06-25T10:00:00Z")
        val changes =
            ClassEdit(studentId = 3, subjectId = 7, durationMinutes = 45, price = Money.ofMajor(40), mode = priced.mode)
        val viaEdit = ClassRules.edit(priced, changes, start, now)
        val byHand = ClassRules.rebill(
            ClassRules.move(
                priced.copy(studentId = 3, subjectId = 7, durationMinutes = 45, price = Money.ofMajor(40)),
                start, now,
            ),
        )
        assertEquals(byHand, viaEdit)
    }
}
