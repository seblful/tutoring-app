package com.seblful.klas.data.repository

import com.seblful.klas.data.billing.balanceOf
import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class BalanceRepositoryTest {

    private val env = TestEnv()

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    @Test
    fun `summed balances agree with billing for every status and attendance`() = runTest {
        val subjectId = env.fixtures.subject()
        var hour = 0L
        LessonStatus.entries.forEach { status ->
            Attended.entries.forEach { state ->
                val studentId = env.fixtures.student("${status.name} ${state.name}")
                // A charge on every row, as a malformed one could carry: only the rule decides what counts.
                env.fixtures.storedClass(
                    studentId = studentId,
                    subjectId = subjectId,
                    startMillis = hour++ * 3_600_000L,
                    status = status,
                    state = state,
                    chargeMinor = 3_000,
                )
                env.fixtures.storedClass(
                    studentId = studentId,
                    subjectId = subjectId,
                    startMillis = hour++ * 3_600_000L,
                    status = LessonStatus.COMPLETED,
                    chargeMinor = 1_500,
                )
                env.paymentRepo.record(studentId, Money.ofMajor(10), LocalDate(2026, 5, 1))
            }
        }
        val students = env.studentRepo.observeAll().first()

        val expected = Billing.balancesFor(students, env.classRepo.getAll(), env.paymentRepo.getAll())
        val summed = env.balanceRepo.get()

        assertEquals(expected, students.map { summed.balanceOf(it.id) })
    }

    @Test
    fun `a student with only lessons to come has a zero balance`() = runTest {
        val subjectId = env.fixtures.subject()
        val studentId = env.fixtures.student()
        env.fixtures.lesson(subjectId, listOf(studentId), start = Instant.parse("2026-06-01T10:00:00Z"))

        val balance = env.balanceRepo.get().balanceOf(studentId)

        assertEquals(Money.ZERO, balance.balance)
    }

    @Test
    fun `observed balances follow a payment`() = runTest {
        val studentId = env.fixtures.student()
        env.paymentRepo.record(studentId, Money.ofMajor(25), LocalDate(2026, 5, 1))

        assertEquals(Money.ofMajor(-25), env.balanceRepo.observe().first().balanceOf(studentId).balance)
    }
}
