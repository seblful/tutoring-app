package com.seblful.tutoringapp.domain

import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.Payment
import com.seblful.tutoringapp.domain.model.Student
import com.seblful.tutoringapp.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class BillingTest {

    private fun completed(charge: Long) = TutoringClass(
        studentId = 1, subjectId = 1, start = Instant.EPOCH, durationMinutes = 60,
        status = ClassStatus.COMPLETED, charge = Money(charge),
    )

    private fun payment(amount: Long) = Payment(
        studentId = 1, date = Instant.EPOCH, amount = Money(amount),
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
            TutoringClass(studentId = 1, subjectId = 1, start = Instant.EPOCH, durationMinutes = 60,
                status = ClassStatus.CANCELLED, charge = Money.ZERO),
            TutoringClass(studentId = 1, subjectId = 1, start = Instant.EPOCH, durationMinutes = 60,
                status = ClassStatus.SCHEDULED),
        )
        val balance = Billing.balanceOf(1, classes, emptyList())
        assertEquals(Money(5000), balance.balance) // only the completed class counts
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
