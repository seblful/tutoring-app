package com.seblful.klas.ui.groups

import androidx.lifecycle.SavedStateHandle
import com.seblful.klas.data.people.StudentSubjectCrossRef
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.format.MoneyField
import com.seblful.klas.testutil.TestMain
import com.seblful.klas.testutil.UiTest
import com.seblful.klas.testutil.UiTestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.FormGap
import com.seblful.klas.ui.common.state.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupFormViewModelTest : UiTest() {

    @BeforeTest
    fun startMain() = main.start()

    @AfterTest
    fun finishMainThenCloseDatabase() {
        try {
            main.finish()
        } finally {
            env.close()
        }
    }

    private val main = TestMain()

    private val env = UiTestEnv()

    private fun vm(groupId: Long = 0L) = main.own(
        GroupFormViewModel(
            savedStateHandle = SavedStateHandle(if (groupId == 0L) emptyMap() else mapOf("id" to groupId)),
            groups = env.groupRepo,
            settings = env.settingsRepo,
            students = env.studentRepo,
            subjects = env.subjectRepo,
            rateHistory = env.rateHistoryRepo,
            enrollment = env.enrollmentRepo,
            appScope = main.appScope,
            computation = main.dispatcher,
        ),
    )

    private suspend fun seedStudents(): Pair<Long, Long> {
        val ann = env.fixtures.student(name = "Аня")
        val bob = env.fixtures.student(name = "Борис")
        return ann to bob
    }

    @Test
    fun `eligibility can be repaired without losing the unfinished group`() = runTest(main.dispatcher) {
        val subject = env.fixtures.subject()
        val student = env.fixtures.student("New member")
        val vm = vm()
        observing(vm.state)
        vm.update { it.copy(name = "Evening group", note = "Keep this draft", price = MoneyField("20")) }
        vm.setSubject(subject)
        advanceUntilIdle()
        assertEquals(listOf(student), vm.state.current.unpriced.map { it.id })
        assertFalse(vm.state.current.canSave)

        vm.enrollMember(student, subject, Money.ZERO)
        advanceUntilIdle()

        assertEquals(setOf(student), vm.state.current.selectedMemberIds)
        assertTrue(vm.state.current.unpriced.isEmpty())
        assertEquals("Keep this draft", vm.state.current.form.note)
        assertEquals(Money.ofMajor(20), vm.state.current.price)
        assertEquals(Money.ZERO, env.enrollmentRepo.priceFor(student, subject)?.amount)
        var savedId = 0L
        vm.save { savedId = it }
        advanceUntilIdle()
        assertEquals(setOf(student), env.groupRepo.getMemberIds(savedId).toSet())
    }

    @Test
    fun `an existing group's form is Loading until the group has been read`() = runTest(main.dispatcher) {
        val (ann, _) = seedStudents()
        val id = env.groupRepo.save(
            Group(name = "11 «А»", price = Money.ofMajor(20)),
            memberIds = setOf(ann),
        )

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
    fun `a new group's form is Ready without waiting`() = runTest(main.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals("", vm.state.current.form.name)
        assertFalse(vm.state.current.canSave)
    }

    @Test
    fun `a group needs a name - a rate and at least one member`() = runTest(main.dispatcher) {
        val (ann, _) = seedStudents()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertFalse(vm.state.current.canSave)
        assertEquals(listOf(FormGap.NAME, FormGap.PRICE, FormGap.MEMBERS), vm.state.current.missing)

        vm.update { it.copy(name = "11 «А»") }
        advanceUntilIdle()
        assertFalse(vm.state.current.canSave, "a group with nobody in it can't be booked")

        vm.toggleMember(ann, true)
        advanceUntilIdle()
        assertFalse(vm.state.current.canSave, "a group that doesn't say what it costs can't be billed")

        vm.update { it.copy(price = MoneyField("20")) }
        advanceUntilIdle()
        assertTrue(vm.state.current.canSave)
    }

    @Test
    fun `a rate of zero is a rate - but a blank one is not`() = runTest(main.dispatcher) {
        val (ann, _) = seedStudents()
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.update { it.copy(name = "Бесплатно") }
        vm.toggleMember(ann, true)
        vm.update { it.copy(price = MoneyField("0")) }
        advanceUntilIdle()
        assertTrue(vm.state.current.canSave, "a group taught for free still bills — at 0")
        assertEquals(Money.ZERO, vm.state.current.price)

        vm.update { it.copy(price = MoneyField("")) }
        advanceUntilIdle()
        assertFalse(vm.state.current.canSave)
    }

    @Test
    fun `saving creates the group with its rate and roster - and reports the new id`() =
        runTest(main.dispatcher) {
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
    fun `editing loads the group's rate and roster - and re-saving replaces them`() =
        runTest(main.dispatcher) {
            val (ann, bob) = seedStudents()
            val id = env.groupRepo.save(Group(name = "Группа", price = Money.ofMajor(20)), setOf(ann))

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
    fun `a new group starts on the tutor's default rate`() = runTest(main.dispatcher) {
        seedStudents()
        env.settingsRepo.update { it.copy(defaultGroupPrice = Money.ofMajor(15)) }

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(Money.ofMajor(15), vm.state.current.price)
    }

    @Test
    fun `with no default set the rate is left blank for the tutor to fill`() =
        runTest(main.dispatcher) {
            seedStudents()

            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()

            assertEquals("", vm.state.current.form.price.text)
            assertFalse(vm.state.current.canSave)
        }

    @Test
    fun `a prefilled default rate is not unsaved work`() = runTest(main.dispatcher) {
        seedStudents()
        env.settingsRepo.update { it.copy(defaultGroupPrice = Money.ofMajor(15)) }

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(Money.ofMajor(15), vm.state.current.price)
        assertFalse(vm.state.current.isDirty, "the app filled that in, not the tutor")
    }

    @Test
    fun `a rate typed before the default arrives stays - and stays dirty`() = runTest(main.dispatcher) {
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
    fun `an edited group is clean until changed - and clean again once saved`() = runTest(main.dispatcher) {
        val (ann, _) = seedStudents()
        val id = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(ann))

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        assertFalse(vm.state.current.isDirty, "loading a group is not editing it")

        vm.update { it.copy(name = "11 «Б»") }
        advanceUntilIdle()
        assertTrue(vm.state.current.isDirty)

        vm.update { it.copy(name = "11 «А»") }
        advanceUntilIdle()
        assertFalse(vm.state.current.isDirty, "renamed back — nothing left to save")

        vm.update { it.copy(price = MoneyField.of(Money.ofMajor(30))) }
        advanceUntilIdle()
        assertTrue(vm.state.current.isDirty)

        vm.save { }
        advanceUntilIdle()
        assertFalse(vm.state.current.isDirty, "saved is the new baseline")
    }

    @Test
    fun `the default never overwrites the rate of a group being edited`() = runTest(main.dispatcher) {
        val (ann, _) = seedStudents()
        env.settingsRepo.update { it.copy(defaultGroupPrice = Money.ofMajor(15)) }
        val id = env.groupRepo.save(Group(name = "Группа", price = Money.ofMajor(40)), setOf(ann))

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(Money.ofMajor(40), vm.state.current.price, "the group's own rate, not the default for new ones")
    }

    @Test
    fun `any active student may join a group - priced for something or not`() =
        runTest(main.dispatcher) {
            val (ann, bob) = seedStudents()

            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()

            assertEquals(listOf(ann, bob), vm.state.current.candidates.map { it.id })
            assertTrue(vm.state.current.hasStudents)
        }

    @Test
    fun `archived students are not offered as members`() = runTest(main.dispatcher) {
        val (ann, bob) = seedStudents()
        env.studentRepo.setArchived(env.studentRepo.getById(bob)!!, archived = true)

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(listOf(ann), vm.state.current.candidates.map { it.id })
    }

    @Test
    fun `a save that leaves out an unpriced member says so first`() = runTest(main.dispatcher) {
        val (ann, bob) = seedStudents()
        val chem = env.fixtures.subject(name = "Химия")
        env.enrollmentRepo.enroll(ann, chem, Money.ofMajor(25))
        env.enrollmentRepo.enroll(bob, chem, Money.ofMajor(25))
        val id = env.groupRepo.save(
            Group(name = "Химия, 11 класс", subjectId = chem, price = Money.ofMajor(20)),
            setOf(ann, bob),
        )
        env.enrollmentRepo.unenroll(ann, chem)

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        vm.update { it.copy(name = "Химия, вечер") }
        assertEquals(1, vm.state.current.leavingOnSave, "one member would leave, and the form knows before saving")

        vm.save { }
        advanceUntilIdle()

        assertEquals(listOf(bob), env.groupRepo.getMemberIds(id), "and they really were dropped")
    }

    @Test
    fun `a group whose every member lost the price cannot be saved at all`() = runTest(main.dispatcher) {
        val (ann, _) = seedStudents()
        val chem = env.fixtures.subject(name = "Химия")
        env.enrollmentRepo.enroll(ann, chem, Money.ofMajor(25))
        val id = env.groupRepo.save(
            Group(name = "Химия, 11 класс", subjectId = chem, price = Money.ofMajor(20)),
            setOf(ann),
        )
        env.enrollmentRepo.unenroll(ann, chem)

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        assertFalse(vm.state.current.canSave, "nobody left who could be billed")
        assertEquals(listOf(ann), env.groupRepo.getMemberIds(id), "nothing written, so the roster is untouched")
    }

    @Test
    fun `a save where everybody is priced says nothing`() = runTest(main.dispatcher) {
        val (ann, _) = seedStudents()
        val chem = env.fixtures.subject(name = "Химия")
        env.enrollmentRepo.enroll(ann, chem, Money.ofMajor(25))
        val id = env.groupRepo.save(
            Group(name = "Химия, 11 класс", subjectId = chem, price = Money.ofMajor(20)),
            setOf(ann),
        )

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        vm.update { it.copy(name = "Химия, вечер") }
        assertEquals(0, vm.state.current.leavingOnSave, "nothing would be left out, so nothing to ask")
        vm.save { }
        advanceUntilIdle()

        assertEquals(listOf(ann), env.groupRepo.getMemberIds(id))
    }

    @Test
    fun `a second tap while saving saves nothing more`() = runTest(main.dispatcher) {
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
        runTest(main.dispatcher) {
            val (ann, bob) = seedStudents()
            val algebra = env.fixtures.subject(name = "Алгебра")
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
        runTest(main.dispatcher) {
            val (ann, bob) = seedStudents()
            val algebra = env.fixtures.subject(name = "Алгебра")
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
        runTest(main.dispatcher) {
            val (ann, _) = seedStudents()
            val algebra = env.fixtures.subject(name = "Алгебра")
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
    fun `the form carries the group's rate history - newest first`() = runTest(main.dispatcher) {
        val (ann, _) = seedStudents()
        val id = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(ann))
        env.groupRepo.save(env.groupRepo.getById(id)!!.copy(price = Money.ofMajor(25)), setOf(ann))

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        val changes = vm.state.current.rateHistory
        assertEquals(2, changes.size)
        assertEquals(Money.ofMajor(25), changes.first().to)
        assertTrue(changes.last().from == null, "the oldest row is where the rate started")
    }

    /** A group that does not exist yet has no rate to have a history of. */
    @Test
    fun `a new group's form shows no rate history`() = runTest(main.dispatcher) {
        seedStudents()

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertTrue(vm.state.current.rateHistory.isEmpty())
    }

    @Test
    fun `saving the form keeps the group retired`() = runTest(main.dispatcher) {
        val (ann, _) = seedStudents()
        val id = env.groupRepo.save(Group(name = "Выпускники", price = Money.ofMajor(20)), setOf(ann))
        env.groupRepo.setArchived(env.groupRepo.getById(id)!!, true)

        val vm = vm(id)
        observing(vm.state)
        advanceUntilIdle()

        vm.update { it.copy(name = "Выпускники 2024") }
        vm.save { }
        advanceUntilIdle()

        val saved = env.groupRepo.getById(id)
        assertEquals("Выпускники 2024", saved?.name)
        assertTrue(saved?.archived == true, "a rename is not an un-archive")
    }
}
