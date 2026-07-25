package com.seblful.repetit.ui.groups

import androidx.lifecycle.SavedStateHandle
import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.PriceEntity
import com.seblful.repetit.data.local.StudentEntity
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
        students = env.studentRepo,
        subjects = env.subjectRepo,
        prices = env.priceRepo,
    )

    private suspend fun seedStudents(): Pair<Long, Long> {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        return ann to bob
    }

    @Test
    fun `a group needs a name and at least one member`() = runTest(mainRule.dispatcher) {
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
        assertTrue(vm.state.value.isValid)
    }

    @Test
    fun `saving creates the group with its roster and reports the new id`() = runTest(mainRule.dispatcher) {
        val (ann, bob) = seedStudents()
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.update { it.copy(name = "  11 «А»  ", note = " вечер ", mode = ClassMode.REMOTE) }
        vm.toggleMember(ann, true)
        vm.toggleMember(bob, true)
        var savedId = 0L
        vm.save { savedId = it }
        advanceUntilIdle()

        val saved = env.groupRepo.getById(savedId)!!
        assertEquals("11 «А»", saved.name)
        assertEquals("вечер", saved.note)
        assertEquals(ClassMode.REMOTE, saved.defaultMode)
        assertEquals(setOf(ann, bob), env.groupRepo.getMemberIds(savedId).toSet())
    }

    @Test
    fun `editing loads the group and its roster, and re-saving replaces it`() = runTest(mainRule.dispatcher) {
        val (ann, bob) = seedStudents()
        val id = env.groupRepo.save(Group(name = "Группа"), setOf(ann))

        val vm = vm(id)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        assertEquals("Группа", vm.state.value.form.name)
        assertEquals(setOf(ann), vm.state.value.form.memberIds)

        vm.toggleMember(ann, false)
        vm.toggleMember(bob, true)
        vm.save { }
        advanceUntilIdle()

        assertEquals(setOf(bob), env.groupRepo.getMemberIds(id).toSet())
    }

    @Test
    fun `the form shows only subjects every member is priced for`() = runTest(mainRule.dispatcher) {
        val (ann, bob) = seedStudents()
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val physics = env.subjectDao.upsert(SubjectEntity(name = "Физика"))
        env.priceDao.upsert(PriceEntity(ann, algebra, Money.ofMajor(30).minor))
        env.priceDao.upsert(PriceEntity(ann, physics, Money.ofMajor(40).minor))
        env.priceDao.upsert(PriceEntity(bob, algebra, Money.ofMajor(25).minor))

        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.toggleMember(ann, true)
        advanceUntilIdle()
        assertEquals(listOf("Алгебра", "Физика"), vm.state.value.bookableSubjects.map { it.name })

        // Adding Bob, who has no Physics price, narrows the group to what they share.
        vm.toggleMember(bob, true)
        advanceUntilIdle()
        assertEquals(listOf("Алгебра"), vm.state.value.bookableSubjects.map { it.name })
    }

    @Test
    fun `archived students are not offered as members`() = runTest(mainRule.dispatcher) {
        val (ann, bob) = seedStudents()
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        env.priceDao.upsert(PriceEntity(ann, algebra, Money.ofMajor(30).minor))
        env.priceDao.upsert(PriceEntity(bob, algebra, Money.ofMajor(25).minor))
        env.studentDao.upsert(env.studentDao.getById(bob)!!.copy(archived = true))

        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.setSubject(algebra)
        advanceUntilIdle()

        assertEquals(listOf(ann), vm.state.value.candidates.map { it.student.id })
    }

    @Test
    fun `members are picked subject first — only students priced for it are offered`() =
        runTest(mainRule.dispatcher) {
            val (ann, bob) = seedStudents()
            val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
            val physics = env.subjectDao.upsert(SubjectEntity(name = "Физика"))
            env.priceDao.upsert(PriceEntity(ann, algebra, Money.ofMajor(30).minor))
            env.priceDao.upsert(PriceEntity(ann, physics, Money.ofMajor(40).minor))
            env.priceDao.upsert(PriceEntity(bob, algebra, Money.ofMajor(25).minor))

            val vm = vm()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            // Nobody is offered until a subject narrows the list — that is what keeps the roster
            // bookable, so the form asks for the subject first.
            assertTrue(vm.state.value.candidates.isEmpty())
            assertTrue(vm.state.value.hasStudents)

            vm.setSubject(algebra)
            advanceUntilIdle()
            assertEquals(listOf(ann, bob), vm.state.value.candidates.map { it.student.id })
            assertEquals(
                "each candidate carries their own rate for the subject",
                listOf(Money.ofMajor(30), Money.ofMajor(25)),
                vm.state.value.candidates.map { it.rate },
            )

            // Physics: only Ann has a price for it.
            vm.setSubject(physics)
            advanceUntilIdle()
            assertEquals(listOf(ann), vm.state.value.candidates.map { it.student.id })

            // Tapping the chosen subject again clears the filter.
            vm.setSubject(physics)
            advanceUntilIdle()
            assertEquals(null, vm.state.value.form.subjectId)
        }

    @Test
    fun `a member kept from another subject stays listed, flagged as unpriced`() =
        runTest(mainRule.dispatcher) {
            val (ann, bob) = seedStudents()
            val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
            val physics = env.subjectDao.upsert(SubjectEntity(name = "Физика"))
            env.priceDao.upsert(PriceEntity(ann, algebra, Money.ofMajor(30).minor))
            env.priceDao.upsert(PriceEntity(ann, physics, Money.ofMajor(40).minor))
            env.priceDao.upsert(PriceEntity(bob, algebra, Money.ofMajor(25).minor))

            val vm = vm()
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()

            vm.setSubject(algebra)
            advanceUntilIdle()
            vm.toggleMember(ann, true)
            vm.toggleMember(bob, true)
            vm.setSubject(physics)
            advanceUntilIdle()

            // Bob has no Physics price, but he is in the roster — hiding him would leave a member
            // nobody can see or remove.
            val bobRow = vm.state.value.candidates.single { it.student.id == bob }
            assertTrue(bobRow.missingPrice)
            assertEquals(null, bobRow.rate)
            assertEquals(setOf(ann, bob), vm.state.value.form.memberIds)
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
