package com.seblful.klas.ui.students

import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StudentsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()

    private fun vm() = StudentsViewModel(env.studentRepo, env.classRepo, env.paymentRepo)

    @Test
    fun `active list excludes archived students and carries each balance`() = runTest(mainRule.dispatcher) {
        val active = env.studentDao.upsert(StudentEntity(name = "Аня"))
        env.studentDao.upsert(StudentEntity(name = "Старый", archived = true))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.classDao.upsert(ClassEntity(studentId = active, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor))
        env.paymentRepo.add(Payment(studentId = active, date = Instant.ofEpochMilli(1), amount = Money.ofMajor(10)))

        val vm = vm()
        backgroundScope.launch { vm.rows.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("Аня"), vm.rows.value.map { it.student.name })
        assertEquals(Money.ofMajor(20), vm.rows.value.single().balance)
    }

    @Test
    fun `toggling archived shows all students including archived ones`() = runTest(mainRule.dispatcher) {
        env.studentDao.upsert(StudentEntity(name = "Аня"))
        env.studentDao.upsert(StudentEntity(name = "Старый", archived = true))

        val vm = vm()
        backgroundScope.launch { vm.rows.collect {} }
        advanceUntilIdle()
        assertEquals(1, vm.rows.value.size)

        vm.toggleArchived()
        advanceUntilIdle()
        assertEquals(2, vm.rows.value.size)
    }
}
