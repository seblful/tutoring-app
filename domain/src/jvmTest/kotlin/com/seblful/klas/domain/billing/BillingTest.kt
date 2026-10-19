package com.seblful.klas.domain.billing

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class BillingTest {

    private fun completed(charge: Long) = TutoringClass(
        studentId = 1, subjectId = 1, start = Instant.fromEpochMilliseconds(0), durationMinutes = 60,
        status = LessonStatus.COMPLETED, charge = Money(charge),
    )

    private fun payment(amount: Long) = Payment(
        studentId = 1, date = Instant.fromEpochMilliseconds(0), amount = Money(amount),
    )

    @Test
    fun `balance is completed charges minus payments`() {
        val classes = listOf(completed(5000), completed(5000))
        val payments = listOf(payment(3000))
        val balance = Billing.balanceOf(1, classes, payments)
        assertEquals(Money(7000), balance.balance) // 10000 charged - 3000 paid
    }

    @Test
    fun `partial payment leaves a positive balance`() {
        val balance = Billing.balanceOf(1, listOf(completed(5000)), listOf(payment(2000)))
        assertEquals(Money(3000), balance.balance)
    }

    @Test
    fun `over-payment leaves a negative balance as credit`() {
        val balance = Billing.balanceOf(1, listOf(completed(5000)), listOf(payment(8000)))
        assertEquals(Money(-3000), balance.balance)
    }

    @Test
    fun `only completed classes add debt`() {
        val classes = listOf(
            completed(5000),
            TutoringClass(studentId = 1, subjectId = 1, start = Instant.fromEpochMilliseconds(0), durationMinutes = 60,
                status = LessonStatus.CANCELLED, charge = Money.ZERO),
            TutoringClass(studentId = 1, subjectId = 1, start = Instant.fromEpochMilliseconds(0), durationMinutes = 60,
                status = LessonStatus.SCHEDULED),
        )
        val balance = Billing.balanceOf(1, classes, emptyList())
        assertEquals(Money(5000), balance.balance) // only the completed class counts
    }

    @Test
    fun `billedOrNull is null for lessons that billed nobody`() {
        val scheduled = completed(5000).copy(status = LessonStatus.SCHEDULED, charge = Money.ZERO)
        val cancelled = completed(5000).copy(status = LessonStatus.CANCELLED, charge = Money.ZERO)
        val missed = completed(5000).copy(attended = Attended.ABSENT, charge = Money.ZERO)
        assertNull(Billing.billedOrNull(emptyList()))
        assertNull(Billing.billedOrNull(listOf(scheduled)))
        assertNull(Billing.billedOrNull(listOf(cancelled)))
        // Taught, but this student was marked absent and not charged: no amount, not «0».
        assertNull(Billing.billedOrNull(listOf(missed)))
    }

    @Test
    fun `billedOrNull is what a billed lesson earned, zero included`() {
        assertEquals(Money(5000), Billing.billedOrNull(listOf(completed(5000))))
        // A late cancellation bills the hour.
        assertEquals(Money(5000), Billing.billedOrNull(listOf(completed(5000).copy(attended = Attended.ABSENT_BILLED))))
        // A taught lesson at a zero rate did earn — zero — and says so.
        assertEquals(Money.ZERO, Billing.billedOrNull(listOf(completed(0))))
        // A group lesson: only the members it billed count.
        val room = listOf(completed(2000), completed(2000).copy(attended = Attended.ABSENT, charge = Money.ZERO))
        assertEquals(Money(2000), Billing.billedOrNull(room))
    }

    @Test
    fun `balancesFor returns one balance per student, zero when none`() {
        val students = listOf(Student(1, "A"), Student(2, "B"), Student(3, "C"))
        val classes = listOf(
            completed(5000).copy(studentId = 1),
            completed(3000).copy(studentId = 2),
            // student 3 has no classes
        )
        val payments = listOf(payment(2000).copy(studentId = 1))

        val balances = Billing.balancesFor(students, classes, payments)

        assertEquals(listOf(1L, 2L, 3L), balances.map { it.studentId }) // aligned with input order
        assertEquals(Money(3000), balances[0].balance) // 5000 charged - 2000 paid
        assertEquals(Money(3000), balances[1].balance)
        assertEquals(Money.ZERO, balances[2].balance) // no activity
    }
}
