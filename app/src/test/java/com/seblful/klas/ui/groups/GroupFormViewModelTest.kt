package com.seblful.klas.ui.groups

import androidx.lifecycle.SavedStateHandle
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.StudentSubjectCrossRef
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.format.MoneyField
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GroupFormViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val env = TestEnv()

    private fun vm(groupId: Long = 0L) = GroupFormViewModel(
        savedStateHandle = SavedStateHandle(if (groupId == 0L) emptyMap() else mapOf("id" to groupId)),
        groups = env.groupRepo,
        settings = env.settingsRepo,
        students = env.studentRepo,
        subjects = env.subjectRepo,
        rateHistory = env.rateHistoryRepo,
        appScope = mainRule.appScope,
        computation = mainRule.dispatcher,
    )

    private suspend fun seedStudents(): Pair<Long, Long> {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        return ann to bob
    }

    @Test
    fun `an existing group's form is Loading until the group has been read`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val id = env.groupRepo.save(
            Group(name = "11 «А»", price = Money.ofMajor(20)),
            memberIds = setOf(ann),
        ).id

        val vm = vm(id)
        assertEquals(Loadable.Loading, vm.state.value)

        observing(vm.state)
        advanceUntilIdle()

        // And what it becomes is the group, not a blank: the pair is what says the wait ended correctly.
        assertEquals("11 «А»", vm.state.current.form.name)
        assertEquals(Money.ofMajor(20), vm.state.current.price)
    }

    /**
     * A **new** group is [Loadable.Ready] as it stands: there is no record to wait for, and an empty form
     * is what "add a group" is. Only the prefilled rate arrives late, into a field that is honest without
     * it — so the screen must not be made to wait here, or adding a group would open on a skeleton.
     */
    @Test
    fun `a new group's form is Ready without waiting`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals("", vm.state.current.form.name)
        assertFalse(vm.state.current.canSave)
    }

    @Test
    fun `a group needs a name, a rate and at least one member`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertFalse(vm.state.current.canSave)

        vm.update { it.copy(name = "11 «А»") }
        advanceUntilIdle()
        assertFalse("a group with nobody in it can't be booked", vm.state.current.canSave)

        vm.toggleMember(ann, true)
        advanceUntilIdle()
        assertFalse("a group that doesn't say what it costs can't be billed", vm.state.current.canSave)

        vm.update { it.copy(price = MoneyField("20")) }
        advanceUntilIdle()
        assertTrue(vm.state.current.canSave)
    }

    @Test
    fun `a rate of zero is a rate, but a blank one is not`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.update { it.copy(name = "Бесплатно") }
        vm.toggleMember(ann, true)
        vm.update { it.copy(price = MoneyField("0")) }
        advanceUntilIdle()
        assertTrue("a group taught for free still bills — at 0", vm.state.current.canSave)
        assertEquals(Money.ZERO, vm.state.current.price)

        vm.update { it.copy(price = MoneyField("")) }
        advanceUntilIdle()
        assertFalse(vm.state.current.canSave)
    }

    @Test
    fun `saving creates the group with its rate and roster, and reports the new id`() =
        runTest(mainRule.dispatcher) {
            val (ann, bob) = seedStudents()
            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()

            vm.update {
                it.copy(name = "  11 «А»  ", note = " вечер ", price = MoneyField("20"), mode = ClassMode.REMOTE)
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
            val id = env.groupRepo.save(Group(name = "Группа", price = Money.ofMajor(20)), setOf(ann)).id

            val vm = vm(id)
            observing(vm.state)
            advanceUntilIdle()

            assertEquals("Группа", vm.state.current.form.name)
            assertEquals(Money.ofMajor(20), vm.state.current.price)
            assertEquals(setOf(ann), vm.state.current.form.memberIds)

            vm.toggleMember(ann, false)
            vm.toggleMember(bob, true)
            vm.update { it.copy(price = MoneyField("25")) }
            vm.save { }
            advanceUntilIdle()

            assertEquals(setOf(bob), env.groupRepo.getMemberIds(id).toSet())
            assertEquals(Money.ofMajor(25), env.groupRepo.getById(id)!!.price)
        }

    @Test
    fun `a new group starts on the tutor's default rate`() = runTest(mainRule.dispatcher) {
        seedStudents()
        env.settingsRepo.update { it.copy(defaultGroupPrice = Money.ofMajor(15)) }

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(Money.ofMajor(15), vm.state.current.price)
    }

    @Test
    fun `with no default set the rate is left blank for the tutor to fill`() =
        runTest(mainRule.dispatcher) {
            seedStudents()

            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()

            assertEquals("", vm.state.current.form.price.text)
            assertFalse(vm.state.current.canSave)
        }

    @Test
    fun `a prefilled default rate is not unsaved work`() = runTest(mainRule.dispatcher) {
        seedStudents()
        env.settingsRepo.update { it.copy(defaultGroupPrice = Money.ofMajor(15)) }

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(Money.ofMajor(15), vm.state.current.price)
        assertFalse("the app filled that in, not the tutor", vm.state.current.isDirty)
    }

    @Test
    fun `a rate typed before the default arrives stays, and stays dirty`() = runTest(mainRule.dispatcher) {
        seedStudents()
        env.settingsRepo.update { it.copy(defaultGroupPrice = Money.ofMajor(15)) }

        val vm = vm()
        observing(vm.state)
        vm.update { it.copy(price = MoneyField.of(Money.ofMajor(25))) }
        advanceUntilIdle()

        assertEquals(Money.ofMajor(25), vm.state.current.price)
        assertTrue(vm.state.current.isDirty)
    }

    @Test
    fun `an edited group is clean until changed, and clean again once saved`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val id = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(ann)).id

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        assertFalse("loading a group is not editing it", vm.state.current.isDirty)

        vm.update { it.copy(name = "11 «Б»") }
        advanceUntilIdle()
        assertTrue(vm.state.current.isDirty)

        vm.update { it.copy(name = "11 «А»") }
        advanceUntilIdle()
        assertFalse("renamed back — nothing left to save", vm.state.current.isDirty)

        vm.update { it.copy(price = MoneyField.of(Money.ofMajor(30))) }
        advanceUntilIdle()
        assertTrue(vm.state.current.isDirty)

        vm.save { }
        advanceUntilIdle()
        assertFalse("saved is the new baseline", vm.state.current.isDirty)
    }

    @Test
    fun `the default never overwrites the rate of a group being edited`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        env.settingsRepo.update { it.copy(defaultGroupPrice = Money.ofMajor(15)) }
        val id = env.groupRepo.save(Group(name = "Группа", price = Money.ofMajor(40)), setOf(ann)).id

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals("the group's own rate, not the default for new ones", Money.ofMajor(40), vm.state.current.price)
    }

    @Test
    fun `any active student may join a group, priced for something or not`() =
        runTest(mainRule.dispatcher) {
            val (ann, bob) = seedStudents()

            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()

            assertEquals(listOf(ann, bob), vm.state.current.candidates.map { it.id })
            assertTrue(vm.state.current.hasStudents)
        }

    @Test
    fun `archived students are not offered as members`() = runTest(mainRule.dispatcher) {
        val (ann, bob) = seedStudents()
        env.studentDao.upsert(env.studentDao.getById(bob)!!.copy(archived = true))

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(listOf(ann), vm.state.current.candidates.map { it.id })
    }

    @Test
    fun `a save that leaves out an unpriced member says so first`() = runTest(mainRule.dispatcher) {
        val (ann, bob) = seedStudents()
        val chem = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        env.enrollmentRepo.enroll(ann, chem, Money.ofMajor(25))
        env.enrollmentRepo.enroll(bob, chem, Money.ofMajor(25))
        val id = env.groupRepo.save(
            Group(name = "Химия, 11 класс", subjectId = chem, price = Money.ofMajor(20)),
            setOf(ann, bob),
        ).id
        env.enrollmentRepo.unenroll(ann, chem)

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        vm.update { it.copy(name = "Химия, вечер") }
        assertEquals("one member would leave, and the form knows before saving", 1, vm.state.current.leavingOnSave)

        vm.save { }
        advanceUntilIdle()

        assertEquals("and they really were dropped", listOf(bob), env.groupRepo.getMemberIds(id))
    }

    @Test
    fun `a group whose every member lost the price cannot be saved at all`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val chem = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        env.enrollmentRepo.enroll(ann, chem, Money.ofMajor(25))
        val id = env.groupRepo.save(
            Group(name = "Химия, 11 класс", subjectId = chem, price = Money.ofMajor(20)),
            setOf(ann),
        ).id
        env.enrollmentRepo.unenroll(ann, chem)

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        assertFalse("nobody left who could be billed", vm.state.current.canSave)
        assertEquals(setOf(ann), vm.state.current.strandedMemberIds)
        assertEquals("nothing written, so the roster is untouched", listOf(ann), env.groupRepo.getMemberIds(id))
    }

    @Test
    fun `a save where everybody is priced says nothing`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val chem = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        env.enrollmentRepo.enroll(ann, chem, Money.ofMajor(25))
        val id = env.groupRepo.save(
            Group(name = "Химия, 11 класс", subjectId = chem, price = Money.ofMajor(20)),
            setOf(ann),
        ).id

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        vm.update { it.copy(name = "Химия, вечер") }
        assertEquals("nothing would be left out, so nothing to ask", 0, vm.state.current.leavingOnSave)
        vm.save { }
        advanceUntilIdle()

        assertEquals(listOf(ann), env.groupRepo.getMemberIds(id))
    }

    @Test
    fun `a second tap while saving saves nothing more`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()
        vm.update { it.copy(name = "Вечерняя", price = it.price.typed("20")) }
        vm.toggleMember(ann, on = true)

        var saved = 0
        vm.save { saved++ }
        vm.save { saved++ }
        advanceUntilIdle()

        assertEquals(1, saved)
        assertEquals(1, env.stored().groups.size)
    }

    @Test
    fun `picking a subject narrows the candidates to the students priced for it`() =
        runTest(mainRule.dispatcher) {
            val (ann, bob) = seedStudents()
            val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
            env.studentDao.addSubjectLink(StudentSubjectCrossRef(ann, algebra))
            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()

            assertEquals(setOf(ann, bob), vm.state.current.candidates.map { it.id }.toSet())

            vm.setSubject(algebra)
            advanceUntilIdle()

            assertEquals(listOf(ann), vm.state.current.candidates.map { it.id })
        }

    @Test
    fun `a tick on someone the new subject excludes is neither counted nor saved`() =
        runTest(mainRule.dispatcher) {
            val (ann, bob) = seedStudents()
            val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
            env.studentDao.addSubjectLink(StudentSubjectCrossRef(ann, algebra))
            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()

            vm.update { it.copy(name = "11 «А»", price = MoneyField("300")) }
            vm.toggleMember(ann, true)
            vm.toggleMember(bob, true)
            advanceUntilIdle()
            assertEquals(2, vm.state.current.selectedMemberIds.size)

            vm.setSubject(algebra)
            advanceUntilIdle()
            assertEquals(setOf(ann), vm.state.current.selectedMemberIds)
            assertTrue(vm.state.current.canSave)

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
            observing(vm.state)
            advanceUntilIdle()

            vm.update { it.copy(name = "11 «А»", price = MoneyField("300")) }
            vm.toggleMember(ann, true)
            vm.setSubject(algebra)
            advanceUntilIdle()

            assertFalse(vm.state.current.hasStudents)
            assertFalse(vm.state.current.canSave)
        }

    @Test
    fun `the form carries the group's rate history, newest first`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val id = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(ann)).id
        env.groupRepo.save(env.groupRepo.getById(id)!!.copy(price = Money.ofMajor(25)), setOf(ann))

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        val changes = vm.state.current.rateHistory
        assertEquals(2, changes.size)
        assertEquals(Money.ofMajor(25), changes.first().to)
        assertTrue("the oldest row is where the rate started", changes.last().isInitial)
    }

    /** A group that does not exist yet has no rate to have a history of. */
    @Test
    fun `a new group's form shows no rate history`() = runTest(mainRule.dispatcher) {
        seedStudents()

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertTrue(vm.state.current.rateHistory.isEmpty())
    }

    @Test
    fun `saving the form keeps the group retired`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val id = env.groupRepo.save(Group(name = "Выпускники", price = Money.ofMajor(20)), setOf(ann)).id
        env.groupRepo.setArchived(env.groupRepo.getById(id)!!, true)

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()
        assertTrue("the form loaded the flag", vm.state.current.archived)

        vm.update { it.copy(name = "Выпускники 2024") }
        vm.save { }
        advanceUntilIdle()

        val saved = env.groupRepo.getById(id)
        assertEquals("Выпускники 2024", saved?.name)
        assertTrue("a rename is not an un-archive", saved?.archived == true)
    }
}
