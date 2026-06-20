package com.seblful.tutoringapp.ui.students

import androidx.lifecycle.SavedStateHandle
import com.seblful.tutoringapp.data.local.ClassEntity
import com.seblful.tutoringapp.data.local.PriceEntity
import com.seblful.tutoringapp.data.local.StudentEntity
import com.seblful.tutoringapp.data.local.StudentSubjectCrossRef
import com.seblful.tutoringapp.data.local.SubjectEntity
import com.seblful.tutoringapp.domain.model.ClassMode
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.testutil.MainDispatcherRule
import com.seblful.tutoringapp.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StudentDetailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()

    private fun vm(id: Long) = StudentDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("id" to id)),
        students = env.studentRepo,
        subjects = env.subjectRepo,
        prices = env.priceRepo,
        classes = env.classRepo,
        payments = env.paymentRepo,
    )

    private suspend fun seedStudent(name: String = "Аня"): Long =
        env.studentDao.upsert(StudentEntity(name = name))

    @Test
    fun `balance is completed charges minus payments`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        // Two completed classes (30 + 45) and one cancelled (ignored), minus a 50 payment.
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 2, durationMinutes = 90, status = "COMPLETED", chargeMinor = Money.ofMajor(45).minor))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 3, durationMinutes = 60, status = "CANCELLED", chargeMinor = 0))
        env.paymentRepo.add(com.seblful.tutoringapp.domain.model.Payment(studentId = sid, date = Instant.ofEpochMilli(10), amount = Money.ofMajor(50)))

        val vm = vm(sid)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(Money.ofMajor(25), vm.state.value.balance)
    }

    @Test
    fun `subjects shown are exactly the priced ones, sorted by name`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val math = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        env.subjectDao.upsert(SubjectEntity(name = "Физика")) // exists but unpriced for this student
        env.priceDao.upsert(PriceEntity(sid, math, Money.ofMajor(30).minor))
        env.priceDao.upsert(PriceEntity(sid, algebra, Money.ofMajor(20).minor))

        val vm = vm(sid)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("Алгебра", "Математика"), vm.state.value.subjects.map { it.subject.name })
    }

    @Test
    fun `addSubject links and prices the subject together`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val math = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val vm = vm(sid)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.addSubject(math, Money.ofMajor(35))
        advanceUntilIdle()

        assertEquals(listOf(math), env.studentDao.getSubjectIds(sid))
        assertEquals(Money.ofMajor(35), env.priceRepo.get(sid, math)?.amount)
    }

    @Test
    fun `removing a subject with no classes unlinks and unprices it`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val math = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.studentDao.addSubjectLink(StudentSubjectCrossRef(sid, math))
        env.priceDao.upsert(PriceEntity(sid, math, Money.ofMajor(30).minor))
        val vm = vm(sid)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.removeSubject(math)
        advanceUntilIdle()

        assertTrue(env.studentDao.getSubjectIds(sid).isEmpty())
        assertNull(env.priceRepo.get(sid, math))
    }

    @Test
    fun `removing a subject the student has classes for is blocked and emits an event`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val math = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.studentDao.addSubjectLink(StudentSubjectCrossRef(sid, math))
        env.priceDao.upsert(PriceEntity(sid, math, Money.ofMajor(30).minor))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = math, startMillis = 1, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor))

        val vm = vm(sid)
        val events = mutableListOf<StudentDetailViewModel.Event>()
        backgroundScope.launch { vm.state.collect {} }
        backgroundScope.launch { vm.events.collect { events += it } }
        advanceUntilIdle()

        vm.removeSubject(math)
        advanceUntilIdle()

        assertEquals(listOf(StudentDetailViewModel.Event.REMOVE_BLOCKED_HAS_CLASSES), events)
        // Nothing removed: the priced subject must keep backing its billed class.
        assertEquals(listOf(math), env.studentDao.getSubjectIds(sid))
        assertEquals(Money.ofMajor(30), env.priceRepo.get(sid, math)?.amount)
    }

    @Test
    fun `addPayment ignores a zero amount`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val vm = vm(sid)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.addPayment(Money.ZERO)
        advanceUntilIdle()
        assertTrue(env.paymentDao.items.value.isEmpty())

        vm.addPayment(Money.ofMajor(10))
        advanceUntilIdle()
        assertEquals(1, env.paymentDao.items.value.size)
    }

    @Test
    fun `editStudent trims fields and ignores a blank name`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent("Аня")
        val vm = vm(sid)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.editStudent(name = "   ", phone = "x", email = "y", note = "z", mode = ClassMode.REMOTE)
        advanceUntilIdle()
        assertEquals("Аня", env.studentDao.getById(sid)?.name) // blank name rejected

        vm.editStudent(name = "  Борис  ", phone = " +375 ", email = " e@x.co ", note = " n ", mode = ClassMode.REMOTE)
        advanceUntilIdle()
        val updated = env.studentDao.getById(sid)!!
        assertEquals("Борис", updated.name)
        assertEquals("+375", updated.phone)
        assertEquals("e@x.co", updated.email)
        assertEquals("REMOTE", updated.defaultMode)
    }

    @Test
    fun `archiving and restoring a student is reflected in the entity`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val vm = vm(sid)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.setArchived(true)
        advanceUntilIdle()
        assertTrue(env.studentDao.getById(sid)!!.archived)

        vm.setArchived(false)
        advanceUntilIdle()
        assertFalse(env.studentDao.getById(sid)!!.archived)
    }
}
