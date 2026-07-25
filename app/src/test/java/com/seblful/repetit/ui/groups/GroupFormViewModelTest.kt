package com.seblful.repetit.ui.groups

import androidx.lifecycle.SavedStateHandle
import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.StudentSubjectCrossRef
import com.seblful.repetit.data.local.SubjectEntity
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.Group
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.testutil.MainDispatcherRule
import com.seblful.repetit.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupFormViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()

    private fun vm(groupId: Long = 0L) = GroupFormViewModel(
        savedStateHandle = SavedStateHandle(if (groupId == 0L) emptyMap() else mapOf("id" to groupId)),
        groups = env.groupRepo,
        settings = env.settingsRepo,
        students = env.studentRepo,
        subjects = env.subjectRepo,
    )

    private suspend fun seedStudents(): Pair<Long, Long> {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        return ann to bob
    }

    @Test
    fun `a group needs a name, a rate and at least one member`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertFalse(vm.state.value.isValid)

        vm.update { it.copy(name = "11 «А»") }
        advanceUntilIdle()
        assertFalse("a group with nobody in it can't be booked", vm.state.value.isValid)

        vm.toggleMember(ann, true)
        advanceUntilIdle()
        assertFalse("a group that doesn't say what it costs can't be billed", vm.state.value.isValid)

        vm.update { it.copy(priceText = "20") }
        advanceUntilIdle()
        assertTrue(vm.state.value.isValid)
    }

    @Test
    fun `a rate of zero is a rate, but a blank one is not`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.update { it.copy(name = "Бесплатно") }
        vm.toggleMember(ann, true)
        vm.update { it.copy(priceText = "0") }
        advanceUntilIdle()
        assertTrue("a group taught for free still bills — at 0", vm.state.value.isValid)
        assertEquals(Money.ZERO, vm.state.value.price)

        vm.update { it.copy(priceText = "") }
        advanceUntilIdle()
        assertFalse(vm.state.value.isValid)
    }

    @Test
    fun `saving creates the group with its rate and roster, and reports the new id`() =
        runTest(mainRule.dispatcher) {
            val (ann, bob) = seedStudents()
            val vm = vm()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            vm.update {
                it.copy(name = "  11 «А»  ", note = " вечер ", priceText = "20", mode = ClassMode.REMOTE)
            }
            vm.toggleMember(ann, true)
            vm.toggleMember(bob, true)
            var savedId = 0L
            vm.save { savedId = it }
            advanceUntilIdle()

            val saved = env.groupRepo.getById(savedId)!!
            assertEquals("11 «А»", saved.name)
            assertEquals("вечер", saved.note)
            assertEquals(Money.ofMajor(20), saved.price)
            assertEquals(ClassMode.REMOTE, saved.defaultMode)
            assertEquals(setOf(ann, bob), env.groupRepo.getMemberIds(savedId).toSet())
        }

    @Test
    fun `editing loads the group's rate and roster, and re-saving replaces them`() =
        runTest(mainRule.dispatcher) {
            val (ann, bob) = seedStudents()
            val id = env.groupRepo.save(Group(name = "Группа", price = Money.ofMajor(20)), setOf(ann))

            val vm = vm(id)
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertEquals("Группа", vm.state.value.form.name)
            assertEquals(Money.ofMajor(20), vm.state.value.price)
            assertEquals(setOf(ann), vm.state.value.form.memberIds)

            vm.toggleMember(ann, false)
            vm.toggleMember(bob, true)
            vm.update { it.copy(priceText = "25") }
            vm.save { }
            advanceUntilIdle()

            assertEquals(setOf(bob), env.groupRepo.getMemberIds(id).toSet())
            assertEquals(Money.ofMajor(25), env.groupRepo.getById(id)!!.price)
        }

    @Test
    fun `a new group starts on the tutor's default rate`() = runTest(mainRule.dispatcher) {
        seedStudents()
        env.settingsRepo.setDefaultGroupPriceMinor(Money.ofMajor(15).minor)

        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(Money.ofMajor(15), vm.state.value.price)
    }

    @Test
    fun `with no default set the rate is left blank for the tutor to fill`() =
        runTest(mainRule.dispatcher) {
            seedStudents()

            val vm = vm()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            // 0 means "not set": pre-filling it would invent an amount, so the field stays empty and
            // the form stays invalid until the tutor says what the group costs.
            assertEquals("", vm.state.value.form.priceText)
            assertFalse(vm.state.value.isValid)
        }

    @Test
    fun `the default never overwrites the rate of a group being edited`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        env.settingsRepo.setDefaultGroupPriceMinor(Money.ofMajor(15).minor)
        val id = env.groupRepo.save(Group(name = "Группа", price = Money.ofMajor(40)), setOf(ann))

        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals("the group's own rate, not the default for new ones", Money.ofMajor(40), vm.state.value.price)
    }

    @Test
    fun `any active student may join a group, priced for something or not`() =
        runTest(mainRule.dispatcher) {
            // Neither student has a single price row: a group bills its own rate, so there is no
            // per-student price to arrange before they can be put in one.
            val (ann, bob) = seedStudents()

            val vm = vm()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            assertEquals(listOf(ann, bob), vm.state.value.candidates.map { it.id })
            assertTrue(vm.state.value.hasStudents)
        }

    @Test
    fun `archived students are not offered as members`() = runTest(mainRule.dispatcher) {
        val (ann, bob) = seedStudents()
        env.studentDao.upsert(env.studentDao.getById(bob)!!.copy(archived = true))

        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals(listOf(ann), vm.state.value.candidates.map { it.id })
    }

    @Test
    fun `archiving a group keeps it and its roster`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val id = env.groupRepo.save(Group(name = "Выпускники"), setOf(ann))
        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.setArchived(true)
        advanceUntilIdle()

        assertTrue(vm.state.value.archived)
        assertTrue(env.groupRepo.getById(id)!!.archived)
        assertEquals(listOf(ann), env.groupRepo.getMemberIds(id))
    }

    @Test
    fun `deleting is refused while the group has lessons`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val subject = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val id = env.groupRepo.save(Group(name = "11 «А»"), setOf(ann))
        env.classDao.upsert(
            ClassEntity(
                studentId = ann, subjectId = subject, startMillis = 1, durationMinutes = 60,
                status = "COMPLETED", groupId = id,
            ),
        )
        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        var result: Boolean? = null
        vm.delete { result = it }
        advanceUntilIdle()

        assertEquals(false, result)
        assertNotNull("the group survives so its lessons still name it", env.groupRepo.getById(id))
    }

    @Test
    fun `picking a subject narrows the candidates to the students priced for it`() =
        runTest(mainRule.dispatcher) {
            val (ann, bob) = seedStudents()
            val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
            env.studentDao.addSubjectLink(StudentSubjectCrossRef(ann, algebra))
            val vm = vm()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            // No subject yet: everyone is a candidate, as before v6.
            assertEquals(setOf(ann, bob), vm.state.value.candidates.map { it.id }.toSet())

            vm.setSubject(algebra)
            advanceUntilIdle()

            assertEquals(listOf(ann), vm.state.value.candidates.map { it.id })
        }

    @Test
    fun `a tick on someone the new subject excludes is neither counted nor saved`() =
        runTest(mainRule.dispatcher) {
            val (ann, bob) = seedStudents()
            val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
            env.studentDao.addSubjectLink(StudentSubjectCrossRef(ann, algebra))
            val vm = vm()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            vm.update { it.copy(name = "11 «А»", priceText = "300") }
            vm.toggleMember(ann, true)
            vm.toggleMember(bob, true)
            advanceUntilIdle()
            assertEquals(2, vm.state.value.selectedMemberIds.size)

            // Bob isn't priced for algebra, so he stops counting the moment the subject is chosen.
            vm.setSubject(algebra)
            advanceUntilIdle()
            assertEquals(setOf(ann), vm.state.value.selectedMemberIds)
            assertTrue(vm.state.value.isValid)

            var savedId = 0L
            vm.save { savedId = it }
            advanceUntilIdle()

            assertEquals(setOf(ann), env.groupRepo.getMemberIds(savedId).toSet())
            assertEquals(algebra, env.groupRepo.getById(savedId)?.subjectId)
        }

    @Test
    fun `a subject with nobody priced for it leaves the group unsaveable`() =
        runTest(mainRule.dispatcher) {
            val (ann, _) = seedStudents()
            val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
            val vm = vm()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            vm.update { it.copy(name = "11 «А»", priceText = "300") }
            vm.toggleMember(ann, true)
            vm.setSubject(algebra)
            advanceUntilIdle()

            // Nobody is priced for algebra, so there is no roster to book and nothing to save.
            assertFalse(vm.state.value.hasStudents)
            assertFalse(vm.state.value.isValid)
        }

    @Test
    fun `an unused group can be deleted`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val id = env.groupRepo.save(Group(name = "Ошибка"), setOf(ann))
        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        var result: Boolean? = null
        vm.delete { result = it }
        advanceUntilIdle()

        assertEquals(true, result)
        assertEquals(null, env.groupRepo.getById(id))
    }
}
