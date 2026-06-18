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
    fun `marking a completed class missed voids its charge`() {
        val completed = ClassRules.applyStatus(scheduled, ClassStatus.COMPLETED, Money.ofMajor(50))
        val missed = ClassRules.applyStatus(completed, ClassStatus.MISSED, Money.ofMajor(50))
        assertEquals(ClassStatus.MISSED, missed.status)
        assertTrue(missed.charge.isZero)
    }

    @Test
    fun `illegal transition is rejected`() {
        val cancelled = ClassRules.applyStatus(scheduled, ClassStatus.CANCELLED, Money.ZERO)
        assertThrows(IllegalArgumentException::class.java) {
            ClassRules.applyStatus(cancelled, ClassStatus.COMPLETED, Money.ofMajor(50))
        }
    }

    @Test
    fun `moving a class updates its start time and keeps it scheduled`() {
        val newStart = Instant.parse("2026-06-20T15:00:00Z")
        val moved = ClassRules.move(scheduled, newStart)
        assertEquals(newStart, moved.start)
        assertEquals(ClassStatus.SCHEDULED, moved.status)
    }
}
