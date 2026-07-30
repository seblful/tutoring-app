package com.seblful.klas.ui.students

import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private fun vm() = StudentsViewModel(env.studentRepo, env.classRepo, env.paymentRepo, mainRule.dispatcher)

    /**
     * The list is [Loadable.Loading] until it has been read, and [Loadable.Ready] once it has — **even
     * when what it read was nothing.**
     *
     * The initial value used to be `emptyList()`, which is the same value an empty database produces, so
     * the screen could not tell them apart and drew its "add your first student" empty state at every
     * cold start — to a tutor with forty of them. Both halves are asserted here because only the pair
     * says anything: Loading alone could be a state nothing ever leaves.
     */
    @Test
    fun `the list is Loading until read, and Ready even when there is nothing`() = runTest(mainRule.dispatcher) {
        val vm = vm()

        assertEquals(Loadable.Loading, vm.rows.value)

        observing(vm.rows)
        advanceUntilIdle()

        assertEquals(Loadable.Ready(emptyList<StudentRow>()), vm.rows.value)
    }

    @Test
    fun `active list excludes archived students and carries each balance`() = runTest(mainRule.dispatcher) {
        val active = env.studentDao.upsert(StudentEntity(name = "Аня"))
        env.studentDao.upsert(StudentEntity(name = "Старый", archived = true))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.fixtures.storedClass(studentId = active, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor)
        env.paymentRepo.add(Payment(studentId = active, date = Instant.ofEpochMilli(1), amount = Money.ofMajor(10)))

        val vm = vm()
        observing(vm.rows)
        advanceUntilIdle()

        assertEquals(listOf("Аня"), vm.rows.current.map { it.student.name })
        assertEquals(Money.ofMajor(20), vm.rows.current.single().balance)
    }

    @Test
    fun `toggling archived shows all students including archived ones`() = runTest(mainRule.dispatcher) {
        env.studentDao.upsert(StudentEntity(name = "Аня"))
        env.studentDao.upsert(StudentEntity(name = "Старый", archived = true))

        val vm = vm()
        observing(vm.rows)
        advanceUntilIdle()
        assertEquals(1, vm.rows.current.size)

        vm.toggleArchived()
        advanceUntilIdle()
        assertEquals(2, vm.rows.current.size)
    }
}
