package com.seblful.klas.domain.billing

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class StatementTest {

    private var nextId = 1L

    private fun lesson(at: String, charge: Long, status: LessonStatus = LessonStatus.COMPLETED) = TutoringClass(
        id = nextId, lessonId = nextId++, studentId = 1, subjectId = 1, start = Instant.parse(at),
        durationMinutes = 60, status = status, charge = Money(charge), price = Money(charge),
        attended = if (status == LessonStatus.COMPLETED) Attended.PRESENT else Attended.EXPECTED,
    )

    private fun paid(at: String, amount: Long) =
        Payment(id = nextId++, studentId = 1, date = Instant.parse(at), amount = Money(amount))

    @Test
    fun `lines run in time order with the balance each one left`() {
        val statement = Statement.of(
            classes = listOf(lesson("2026-06-03T10:00:00Z", 3000), lesson("2026-06-01T10:00:00Z", 3000)),
            payments = listOf(paid("2026-06-02T10:00:00Z", 5000)),
        )

        assertEquals(listOf(3000L, -2000L, 1000L), statement.entries.map { it.balanceAfter.minor })
        assertEquals(Balance.Owed(Money(1000)), statement.balance)
    }

    @Test
    fun `lessons not billed are not lines`() {
        val statement = Statement.of(
            classes = listOf(
                lesson("2026-06-01T10:00:00Z", 3000),
                lesson("2026-06-08T10:00:00Z", 3000, LessonStatus.SCHEDULED),
                lesson("2026-06-09T10:00:00Z", 3000, LessonStatus.CANCELLED),
            ),
            payments = emptyList(),
        )

        assertEquals(1, statement.entries.size)
    }

    @Test
    fun `at one instant the lesson comes before the payment for it`() {
        val statement = Statement.of(
            classes = listOf(lesson("2026-06-01T10:00:00Z", 3000)),
            payments = listOf(paid("2026-06-01T10:00:00Z", 3000)),
        )

        assertTrue(statement.entries.first() is Statement.Lesson)
        assertEquals(Balance.Settled, statement.balance)
    }

    @Test
    fun `its last balance is the one billing computes`() {
        val classes = listOf(
            lesson("2026-05-01T10:00:00Z", 2500),
            lesson("2026-05-08T10:00:00Z", 2500),
            lesson("2026-05-15T10:00:00Z", 2500, LessonStatus.SCHEDULED),
        )
        val payments = listOf(paid("2026-05-02T10:00:00Z", 10000))

        val billed = Billing.balancesFor(listOf(Student(id = 1, name = "Аня")), classes, payments).single()

        assertEquals(Balance.of(billed.balance), Statement.of(classes, payments).balance)
        assertEquals(Balance.Credit(Money(5000)), Statement.of(classes, payments).balance)
    }
}
