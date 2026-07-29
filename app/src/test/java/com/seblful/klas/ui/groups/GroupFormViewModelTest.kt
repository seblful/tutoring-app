package com.seblful.klas.ui.groups

import androidx.lifecycle.SavedStateHandle
import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.StudentSubjectCrossRef
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.format.MoneyField
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
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
        computation = mainRule.dispatcher,
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
        observing(vm.state)
        advanceUntilIdle()

        assertFalse(vm.state.value.canSave)

        vm.update { it.copy(name = "11 «А»") }
        advanceUntilIdle()
        assertFalse("a group with nobody in it can't be booked", vm.state.value.canSave)

        vm.toggleMember(ann, true)
        advanceUntilIdle()
        assertFalse("a group that doesn't say what it costs can't be billed", vm.state.value.canSave)

        vm.update { it.copy(price = MoneyField("20")) }
        advanceUntilIdle()
        assertTrue(vm.state.value.canSave)
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
        assertTrue("a group taught for free still bills — at 0", vm.state.value.canSave)
        assertEquals(Money.ZERO, vm.state.value.price)

        vm.update { it.copy(price = MoneyField("")) }
        advanceUntilIdle()
        assertFalse(vm.state.value.canSave)
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
            val id = env.groupRepo.save(Group(name = "Группа", price = Money.ofMajor(20)), setOf(ann))

            val vm = vm(id)
            observing(vm.state)
            advanceUntilIdle()

            assertEquals("Группа", vm.state.value.form.name)
            assertEquals(Money.ofMajor(20), vm.state.value.price)
            assertEquals(setOf(ann), vm.state.value.form.memberIds)

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
        env.settingsRepo.update { it.copy(defaultGroupPriceMinor = Money.ofMajor(15).minor) }

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(Money.ofMajor(15), vm.state.value.price)
    }

    @Test
    fun `with no default set the rate is left blank for the tutor to fill`() =
        runTest(mainRule.dispatcher) {
            seedStudents()

            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()

            // 0 means "not set": pre-filling it would invent an amount, so the field stays empty and
            // the form stays invalid until the tutor says what the group costs.
            assertEquals("", vm.state.value.form.price.text)
            assertFalse(vm.state.value.canSave)
        }

    /**
     * What back consults. Everything this form fills in for the tutor — a loaded group, the default
     * rate for a new one — arrives from the database *after* the screen is up, so a naive "has the
     * form changed since construction" would report unsaved work on a form nobody has touched.
     */
    @Test
    fun `a prefilled default rate is not unsaved work`() = runTest(mainRule.dispatcher) {
        seedStudents()
        env.settingsRepo.update { it.copy(defaultGroupPriceMinor = Money.ofMajor(15).minor) }

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(Money.ofMajor(15), vm.state.value.price)
        assertFalse("the app filled that in, not the tutor", vm.state.value.isDirty)
    }

    /** Typing before the default lands must survive it — and must still count as unsaved work. */
    @Test
    fun `a rate typed before the default arrives stays, and stays dirty`() = runTest(mainRule.dispatcher) {
        seedStudents()
        env.settingsRepo.update { it.copy(defaultGroupPriceMinor = Money.ofMajor(15).minor) }

        val vm = vm()
        observing(vm.state)
        vm.update { it.copy(price = MoneyField.of(Money.ofMajor(25))) }
        advanceUntilIdle()

        assertEquals(Money.ofMajor(25), vm.state.value.price)
        assertTrue(vm.state.value.isDirty)
    }

    @Test
    fun `an edited group is clean until changed, and clean again once saved`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val id = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(ann))

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        assertFalse("loading a group is not editing it", vm.state.value.isDirty)

        vm.update { it.copy(name = "11 «Б»") }
        advanceUntilIdle()
        assertTrue(vm.state.value.isDirty)

        vm.update { it.copy(name = "11 «А»") }
        advanceUntilIdle()
        assertFalse("renamed back — nothing left to save", vm.state.value.isDirty)

        vm.update { it.copy(price = MoneyField.of(Money.ofMajor(30))) }
        advanceUntilIdle()
        assertTrue(vm.state.value.isDirty)

        vm.save { }
        advanceUntilIdle()
        assertFalse("saved is the new baseline", vm.state.value.isDirty)
    }

    /** Archiving writes to the database immediately, so it is never pending work. */
    @Test
    fun `archiving does not leave the form looking unsaved`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val id = env.groupRepo.save(Group(name = "Выпускники", price = Money.ofMajor(20)), setOf(ann))

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        vm.setArchived(true)
        advanceUntilIdle()

        assertTrue(vm.state.value.archived)
        assertFalse(vm.state.value.isDirty)
    }

    @Test
    fun `the default never overwrites the rate of a group being edited`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        env.settingsRepo.update { it.copy(defaultGroupPriceMinor = Money.ofMajor(15).minor) }
        val id = env.groupRepo.save(Group(name = "Группа", price = Money.ofMajor(40)), setOf(ann))

        val vm = vm(id)
        observing(vm.state)
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
            observing(vm.state)
            advanceUntilIdle()

            assertEquals(listOf(ann, bob), vm.state.value.candidates.map { it.id })
            assertTrue(vm.state.value.hasStudents)
        }

    @Test
    fun `archived students are not offered as members`() = runTest(mainRule.dispatcher) {
        val (ann, bob) = seedStudents()
        env.studentDao.upsert(env.studentDao.getById(bob)!!.copy(archived = true))

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(listOf(ann), vm.state.value.candidates.map { it.id })
    }

    @Test
    fun `archiving a group keeps it and its roster`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val id = env.groupRepo.save(Group(name = "Выпускники"), setOf(ann))
        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        vm.setArchived(true)
        advanceUntilIdle()

        assertTrue(vm.state.value.archived)
        assertTrue(env.groupRepo.getById(id)!!.archived)
        assertEquals(listOf(ann), env.groupRepo.getMemberIds(id))
    }

    /**
     * A group with a subject keeps only the members priced for it, and the save now says when that
     * left somebody out. The tutor need not have touched the roster: un-enrolling a member from the
     * subject one-to-one is allowed, and the next save — **a rename is enough**, since every save
     * re-intersects — is what drops them. It used to happen in silence.
     */
    @Test
    fun `a save that leaves out an unpriced member says so`() = runTest(mainRule.dispatcher) {
        val (ann, bob) = seedStudents()
        val chem = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        env.enrollmentRepo.enroll(ann, chem, Money.ofMajor(25))
        env.enrollmentRepo.enroll(bob, chem, Money.ofMajor(25))
        val id = env.groupRepo.save(
            Group(name = "Химия, 11 класс", subjectId = chem, price = Money.ofMajor(20)),
            setOf(ann, bob),
        )
        // The tutor stops teaching Аня Химия one-to-one; the group still teaches it, and Борис is
        // still priced, so the form stays saveable — which is what makes the drop silent.
        env.enrollmentRepo.unenroll(ann, chem)

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()
        val events = mutableListOf<GroupFormViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }

        // Nothing but the name changes.
        vm.update { it.copy(name = "Химия, вечер") }
        vm.save { }
        advanceUntilIdle()

        assertEquals(listOf(GroupFormViewModel.Event.MEMBERS_NOT_PRICED_FOR_SUBJECT), events)
        assertEquals("and they really were dropped", listOf(bob), env.groupRepo.getMemberIds(id))
    }

    /**
     * When the subject rule strands **everybody**, the form goes invalid instead — the save button is
     * disabled and there is nothing to announce, because nothing is written. Worth pinning next to the
     * case above so the two are not confused: one is a silent drop, the other is a visible dead end.
     */
    @Test
    fun `a group whose every member lost the price cannot be saved at all`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val chem = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        env.enrollmentRepo.enroll(ann, chem, Money.ofMajor(25))
        val id = env.groupRepo.save(
            Group(name = "Химия, 11 класс", subjectId = chem, price = Money.ofMajor(20)),
            setOf(ann),
        )
        env.enrollmentRepo.unenroll(ann, chem)

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        assertFalse("nobody left who could be billed", vm.state.value.canSave)
        assertEquals(setOf(ann), vm.state.value.strandedMemberIds)
        assertEquals("nothing written, so the roster is untouched", listOf(ann), env.groupRepo.getMemberIds(id))
    }

    @Test
    fun `a save where everybody is priced says nothing`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val chem = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        env.enrollmentRepo.enroll(ann, chem, Money.ofMajor(25))
        val id = env.groupRepo.save(Group(name = "Химия, 11 класс", subjectId = chem, price = Money.ofMajor(20)), setOf(ann))

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()
        val events = mutableListOf<GroupFormViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }

        vm.update { it.copy(name = "Химия, вечер") }
        vm.save { }
        advanceUntilIdle()

        assertTrue("nothing was left out, so nothing to announce", events.isEmpty())
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
        observing(vm.state)
        advanceUntilIdle()

        var deleted = false
        val events = mutableListOf<GroupFormViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }
        vm.delete { deleted = true }
        advanceUntilIdle()

        assertFalse(deleted)
        assertEquals(listOf(GroupFormViewModel.Event.DELETE_BLOCKED_HAS_LESSONS), events)
        assertNotNull("the group survives so its lessons still name it", env.groupRepo.getById(id))
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

            // No subject yet: everyone is a candidate.
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
            observing(vm.state)
            advanceUntilIdle()

            vm.update { it.copy(name = "11 «А»", price = MoneyField("300")) }
            vm.toggleMember(ann, true)
            vm.toggleMember(bob, true)
            advanceUntilIdle()
            assertEquals(2, vm.state.value.selectedMemberIds.size)

            // Bob isn't priced for algebra, so he stops counting the moment the subject is chosen.
            vm.setSubject(algebra)
            advanceUntilIdle()
            assertEquals(setOf(ann), vm.state.value.selectedMemberIds)
            assertTrue(vm.state.value.canSave)

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

            // Nobody is priced for algebra, so there is no roster to book and nothing to save.
            assertFalse(vm.state.value.hasStudents)
            assertFalse(vm.state.value.canSave)
        }

    @Test
    fun `an unused group can be deleted`() = runTest(mainRule.dispatcher) {
        val (ann, _) = seedStudents()
        val id = env.groupRepo.save(Group(name = "Ошибка"), setOf(ann))
        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        var deleted = false
        vm.delete { deleted = true }
        advanceUntilIdle()

        assertTrue(deleted)
        assertEquals(null, env.groupRepo.getById(id))
    }
}
