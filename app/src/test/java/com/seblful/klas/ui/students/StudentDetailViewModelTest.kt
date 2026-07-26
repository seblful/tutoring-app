package com.seblful.klas.ui.students

import androidx.lifecycle.SavedStateHandle
import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.StudentSubjectCrossRef
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.LessonKind
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
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
        classes = env.classRepo,
        groups = env.groupRepo,
        payments = env.paymentRepo,
        enrollment = env.enrollmentRepo,
        rateHistory = env.rateHistoryRepo,
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
        env.paymentRepo.add(com.seblful.klas.domain.model.Payment(studentId = sid, date = Instant.ofEpochMilli(10), amount = Money.ofMajor(50)))

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
    fun `the stat row counts completed lessons, their hours and their income`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 2, durationMinutes = 90, status = "COMPLETED", chargeMinor = Money.ofMajor(45).minor))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 3, durationMinutes = 60, status = "SCHEDULED", chargeMinor = 0))

        val vm = vm(sid)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val totals = vm.state.value.totals
        assertEquals(2, totals.lessons)
        assertEquals(1, totals.upcoming)
        assertEquals("a scheduled lesson adds no hours yet", 150, totals.minutes)
        assertEquals(Money.ofMajor(75), totals.earned)
    }

    /**
     * A student's lesson list is the one place the two kinds of teaching mix, and they are billed at
     * different rates — the group's, and the student's own — so a row has to say which it was. The
     * name matters more than a flag: *which* group is what explains the amount.
     */
    @Test
    fun `a lesson says whether it was individual or which group it was with`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Биология"))
        val groupId = env.groupDao.upsert(GroupEntity(name = "11 «А»", subjectId = subj))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 2, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(20).minor, groupId = groupId))

        val vm = vm(sid)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val rows = vm.state.value.classes.sortedBy { it.klass.start }
        assertEquals(LessonKind.INDIVIDUAL, rows[0].kind)
        assertNull("an individual lesson has no group to name", rows[0].groupName)
        assertEquals(LessonKind.GROUP, rows[1].kind)
        assertEquals("11 «А»", rows[1].groupName)
    }

    /** The chips only earn their space when there is a mix to narrow; both counts must be honest. */
    @Test
    fun `the kind tally counts both kinds, and one kind alone offers no narrowing`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Биология"))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "COMPLETED", chargeMinor = Money.ofMajor(30).minor))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 2, durationMinutes = 60, status = "SCHEDULED", chargeMinor = 0))

        val vm = vm(sid)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(mapOf(LessonKind.INDIVIDUAL to 2), vm.state.value.lessonKinds)
        assertFalse("nothing to narrow", vm.state.value.hasBothKinds)

        val groupId = env.groupDao.upsert(GroupEntity(name = "11 «А»", subjectId = subj))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 3, durationMinutes = 60, status = "SCHEDULED", chargeMinor = 0, groupId = groupId))
        advanceUntilIdle()

        assertEquals(
            mapOf(LessonKind.INDIVIDUAL to 2, LessonKind.GROUP to 1),
            vm.state.value.lessonKinds,
        )
        assertTrue(vm.state.value.hasBothKinds)
    }

    /**
     * The history hangs off the subject it prices, so a student taking two subjects gets two separate
     * timelines rather than one pile the tutor has to disentangle.
     */
    @Test
    fun `each subject carries its own rate history, newest first`() = runTest(mainRule.dispatcher) {
        val sid = seedStudent()
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val geometry = env.subjectDao.upsert(SubjectEntity(name = "Геометрия"))
        env.enrollmentRepo.enroll(sid, algebra, Money.ofMajor(30))
        env.enrollmentRepo.enroll(sid, geometry, Money.ofMajor(25))
        env.enrollmentRepo.reprice(sid, algebra, Money.ofMajor(35))

        val vm = vm(sid)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        val bySubject = vm.state.value.subjects.associateBy { it.subject.name }
        assertEquals(2, bySubject.getValue("Алгебра").history.size)
        assertEquals(Money.ofMajor(35), bySubject.getValue("Алгебра").history.first().to)
        assertEquals(Money.ofMajor(30), bySubject.getValue("Алгебра").history.first().from)
        assertEquals("geometry never moved", 1, bySubject.getValue("Геометрия").history.size)
        assertTrue(bySubject.getValue("Геометрия").history.single().isInitial)
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
        assertEquals(Money.ofMajor(35), env.enrollmentRepo.priceFor(sid, math)?.amount)
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
        assertNull(env.enrollmentRepo.priceFor(sid, math))
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
        backgroundScope.launch { vm.events.stream.collect { events += it } }
        advanceUntilIdle()

        vm.removeSubject(math)
        advanceUntilIdle()

        assertEquals(listOf(StudentDetailViewModel.Event.REMOVE_BLOCKED_HAS_CLASSES), events)
        // Nothing removed: the priced subject must keep backing its billed class.
        assertEquals(listOf(math), env.studentDao.getSubjectIds(sid))
        assertEquals(Money.ofMajor(30), env.enrollmentRepo.priceFor(sid, math)?.amount)
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
