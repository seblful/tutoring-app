package com.seblful.klas.queries.billing

import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class DebtsQueryTest {

    private val env = TestEnv()

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    private val morning = Instant.parse("2026-05-01T08:00:00Z")

    private suspend fun owing(name: String, subject: Long, rate: Long, hour: Int): Long {
        val id = env.fixtures.pricedStudent(name, subject, rate = Money.ofMajor(rate))
        env.fixtures.completedLesson(
            subject,
            listOf(id),
            start = Instant.parse("2026-05-01T${hour.toString().padStart(2, '0')}:00:00Z"),
            rate = Money.ofMajor(rate),
        )
        return id
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
        val debts = env.debts.get()

        assertEquals(expected, students.map { debts.of(it.id) })
    }

    @Test
    fun `a student with only lessons to come has a zero balance`() = runTest {
        val subjectId = env.fixtures.subject()
        val studentId = env.fixtures.student()
        env.fixtures.lesson(subjectId, listOf(studentId), start = Instant.parse("2026-06-01T10:00:00Z"))

        assertEquals(Money.ZERO, env.debts.get().of(studentId).balance)
    }

    @Test
    fun `observed debts follow a payment`() = runTest {
        val studentId = env.fixtures.student()
        env.paymentRepo.record(studentId, Money.ofMajor(25), LocalDate(2026, 5, 1))

        assertEquals(Money.ofMajor(-25), env.debts.observe().first().of(studentId).balance)
    }

    @Test
    fun `debtors are listed most owed first, each with when they last paid`() = runTest {
        val subject = env.fixtures.subject("Алгебра")
        val anya = owing("Аня", subject, rate = 30, hour = 9)
        val boris = owing("Боря", subject, rate = 40, hour = 11)
        env.paymentRepo.restore(Payment(studentId = anya, date = morning, amount = Money.ofMajor(10)))

        val debts = env.debts.get()

        assertEquals(
            listOf(Debtor(boris, "Боря", Money.ofMajor(40), null), Debtor(anya, "Аня", Money.ofMajor(20), morning)),
            debts.debtors,
        )
        assertEquals(2, debts.outstanding.students)
        assertEquals(Money.ofMajor(60), debts.outstanding.total)
    }

    @Test
    fun `an archived student's debt is not outstanding, but stays theirs`() = runTest {
        val subject = env.fixtures.subject("Алгебра")
        val anya = owing("Аня", subject, rate = 30, hour = 9)
        env.studentRepo.setArchived(env.studentRepo.getById(anya)!!, archived = true)

        val debts = env.debts.get()

        assertTrue(debts.debtors.isEmpty())
        assertEquals(Money.ZERO, debts.outstanding.total)
        assertEquals(Money.ofMajor(30), debts.of(anya).balance)
    }

    /** "Owed to the tutor", not "the tutor's net position": an overpaid student does not reduce another's debt. */
    @Test
    fun `credit is not netted off against another student's debt`() = runTest {
        val subject = env.fixtures.subject("Алгебра")
        owing("Аня", subject, rate = 30, hour = 9)
        val boris = env.fixtures.pricedStudent("Боря", subject, rate = Money.ofMajor(30))
        // Boris has paid for a lesson he has not had.
        env.paymentRepo.restore(Payment(studentId = boris, date = morning, amount = Money.ofMajor(50)))

        val outstanding = env.debts.get().outstanding

        assertEquals(1, outstanding.students)
        assertEquals(Money.ofMajor(30), outstanding.total)
    }
}
