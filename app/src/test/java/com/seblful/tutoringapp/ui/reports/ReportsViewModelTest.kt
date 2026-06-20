package com.seblful.tutoringapp.ui.reports

import com.seblful.tutoringapp.data.local.ClassEntity
import com.seblful.tutoringapp.data.local.StudentEntity
import com.seblful.tutoringapp.data.local.SubjectEntity
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.Payment
import com.seblful.tutoringapp.testutil.MainDispatcherRule
import com.seblful.tutoringapp.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()

    private fun vm() = ReportsViewModel(env.classRepo, env.studentRepo, env.subjectRepo, env.paymentRepo)

    @Test
    fun `archived students are excluded from income, breakdowns and debts`() = runTest(mainRule.dispatcher) {
        val active = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val archived = env.studentDao.upsert(StudentEntity(name = "Старый", archived = true))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        // Active student: completed 30. Archived student: completed 100 (must be ignored everywhere).
        env.classDao.upsert(ClassEntity(studentId = active, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor))
        env.classDao.upsert(ClassEntity(studentId = archived, subjectId = subj, startMillis = 2, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(100).minor))

        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(Money.ofMajor(30), state.totalIncome)
        assertEquals(1, state.byStudent.size)
        assertEquals("Аня", state.byStudent.single().label)
        // Active student owes 30 (no payment), archived one's debt never appears.
        assertEquals(Money.ofMajor(30), state.totalOutstanding)
        assertEquals(listOf("Аня"), state.debts.map { it.label })
    }

    @Test
    fun `breakdowns drop zero entries and order by amount, largest first`() = runTest(mainRule.dispatcher) {
        val a = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val b = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.classDao.upsert(ClassEntity(studentId = a, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(20).minor))
        env.classDao.upsert(ClassEntity(studentId = b, subjectId = subj, startMillis = 2, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(50).minor))

        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("Борис", "Аня"), vm.state.value.byStudent.map { it.label })
    }

    @Test
    fun `a fully paid student adds no debt`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor))
        env.paymentRepo.add(Payment(studentId = sid, date = Instant.ofEpochMilli(10), amount = Money.ofMajor(30)))

        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertTrue(vm.state.value.debts.isEmpty())
        assertEquals(Money.ZERO, vm.state.value.totalOutstanding)
        assertEquals(Money.ofMajor(30), vm.state.value.totalIncome)
    }

    @Test
    fun `income by month is ordered newest first`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val zone = java.time.ZoneId.systemDefault()
        val jan = LocalDate.of(2026, 1, 10).atStartOfDay(zone).toInstant().toEpochMilli()
        val mar = LocalDate.of(2026, 3, 10).atStartOfDay(zone).toInstant().toEpochMilli()
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = jan, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(10).minor))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = mar, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(20).minor))

        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        // Newest month (March, 20) comes before the older one (January, 10).
        assertEquals(listOf(Money.ofMajor(20), Money.ofMajor(10)), vm.state.value.byMonth.map { it.amount })
    }

    @Test
    fun `an empty database reports as empty`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertTrue(vm.state.value.isEmpty)
    }
}
