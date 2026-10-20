package com.seblful.klas.ui.students

import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StudentsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val env = TestEnv()

    private fun vm() = StudentsViewModel(env.studentRepo, env.balanceRepo, mainRule.dispatcher)

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
        env.fixtures.storedClass(
            studentId = active,
            subjectId = subj,
            startMillis = 1,
            durationMinutes = 60,
            status = LessonStatus.COMPLETED,
            chargeMinor = Money.ofMajor(30).minor,
        )
        env.paymentRepo.restore(
            Payment(studentId = active, date = Instant.fromEpochMilliseconds(1), amount = Money.ofMajor(10)),
        )

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

    @Test
    fun `with the archive shown, the archived come after everyone taught now`() = runTest(mainRule.dispatcher) {
        env.fixtures.archivedStudent("Аня")
        env.fixtures.student("Борис")
        val vm = vm()
        observing(vm.rows)
        vm.toggleArchived()
        advanceUntilIdle()

        assertEquals(listOf("Борис", "Аня"), vm.rows.current.map { it.student.name })
    }
}
