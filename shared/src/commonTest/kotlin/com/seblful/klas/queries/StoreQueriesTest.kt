package com.seblful.klas.queries

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.reports.ReportPeriod
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.store
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class StoreQueriesTest {
    private val env = TestEnv()
    private val store = env.store()

    @AfterTest
    fun close() = env.close()

    @Test
    fun studentDetailsObservePaymentsAndUseTheRecordedLessonPrice() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject, rate = Money.ofMajor(30))
        env.fixtures.completedLesson(subject, listOf(student), env.clock.now() - 1.days)
        val balances = mutableListOf<Balance>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.studentDetails.observe(student).collect { balances += it.balance }
        }
        runCurrent()
        val before = store.studentDetails.observe(student).first()
        assertEquals(Money.ofMajor(30), before.classes.single().shownCharge)

        store.enrollment.reprice(student, subject, Money.ofMajor(50))
        store.payments.record(student, Money.ofMajor(30), env.clock.today())
        runCurrent()

        val after = store.studentDetails.observe(student).first()
        assertEquals(Money.ofMajor(50), after.subjects.single().price)
        assertEquals(Money.ofMajor(30), after.classes.single().shownCharge)
        assertEquals(Balance.Settled, after.balance)
        assertTrue(balances.distinct().size > 1)
        assertEquals(Balance.Settled, balances.last())
    }

    @Test
    fun studentDetailsIncludeGroupHomeworkWhenTheStudentMissesItsNextLesson() = runTest {
        val subject = env.fixtures.subject()
        val first = env.fixtures.pricedStudent("Ann", subject)
        val second = env.fixtures.pricedStudent("Bob", subject)
        val group = env.fixtures.group("Group", subject, setOf(first, second))
        env.fixtures.lesson(subject, listOf(second), env.clock.now() + 1.days, groupId = group)
        val homework = store.homework.give(Assignee.Group(group), "Exercises 1–3")

        val details = store.studentDetails.observe(first).first()

        assertEquals(homework.id, details.homework.current.single().homework.id)
        assertEquals("Group", details.homework.current.single().title)
        assertEquals(env.clock.now() + 1.days, details.homework.current.single().lessonAt)
    }

    @Test
    fun reportPeriodChangesRecomputeTheSameObservedQuery() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        env.fixtures.completedLesson(subject, listOf(student), env.clock.now() - 1.days)
        val periods = MutableStateFlow(ReportPeriod.currentYear(env.clock.today()))
        val income = mutableListOf<Money>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.reports.observe(periods).collect { income += it.totalIncome }
        }
        runCurrent()
        assertEquals(Money.ofMajor(30), income.last())

        periods.value = periods.value.shifted(-1)
        runCurrent()

        assertEquals(Money.ZERO, income.last())
    }

    @Test
    fun missingStudentIsDistinctFromAStudentWithNoHistory() = runTest {
        assertNull(store.studentDetails.observe(999).first().student)
        val student = env.fixtures.student("Ann")
        val details = store.studentDetails.observe(student).first()
        assertEquals("Ann", details.student?.name)
        assertEquals(Balance.Settled, details.balance)
        assertTrue(details.classes.isEmpty())
    }
}
