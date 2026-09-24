package com.seblful.klas.ui.students

import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.valueOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudentFormViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()

    private fun vm() = StudentFormViewModel(env.subjectRepo, env.enrollmentRepo, mainRule.appScope, mainRule.dispatcher)

    /**
     * **The form is usable at once; only its subject list waits.**
     *
     * Empty is a *refusal* on this screen — «Сначала добавьте предметы», in the error red — so an initial
     * `emptyList()` said it to a tutor with nine subjects for the frame or two before they were read. But
     * the fields around it are legitimately blank (this screen only ever *adds* a student), so wrapping the
     * whole state would make «Добавить ученика» open on a skeleton for no reason. Hence the split: the
     * state is a plain value and [StudentFormViewModel.UiState.allSubjects] carries its own answer.
     */
    @Test
    fun `only the subject list waits to be read`() = runTest(mainRule.dispatcher) {
        env.fixtures.subject("Математика")
        val vm = vm()

        assertEquals(Loadable.Loading, vm.state.value.allSubjects)
        assertEquals("but the form itself is already there", "", vm.state.value.form.name)

        observing(vm.state)
        advanceUntilIdle()

        assertEquals(
            listOf("Математика"),
            vm.state.value.allSubjects.valueOrNull()?.map { it.name },
        )
    }

    @Test
    fun `form starts invalid and becomes valid with a name and a priced subject`() = runTest(mainRule.dispatcher) {
        val mathId = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val vm = vm()
        observing(vm.state)

        assertFalse(vm.state.value.canSave)

        vm.update { it.copy(name = "Аня") }
        assertFalse("a name alone isn't enough — needs a priced subject", vm.state.value.canSave)

        vm.toggleSubject(mathId, on = true)
        assertFalse("a checked subject with no price is invalid", vm.state.value.canSave)

        vm.setPriceText(mathId, "30")
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `a blank or unparseable price keeps the form invalid`() = runTest(mainRule.dispatcher) {
        val id = env.subjectDao.upsert(SubjectEntity(name = "Физика"))
        val vm = vm()
        observing(vm.state)

        vm.update { it.copy(name = "Иван") }
        vm.toggleSubject(id, on = true)
        vm.setPriceText(id, "abc")
        assertFalse(vm.state.value.canSave)

        vm.setPriceText(id, "")
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `setPriceText is ignored for a subject that isn't selected`() = runTest(mainRule.dispatcher) {
        val id = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        val vm = vm()
        observing(vm.state)

        vm.setPriceText(id, "40") // never toggled on
        assertTrue(vm.state.value.form.subjectPrices.isEmpty())
    }

    @Test
    fun `toggling a subject off drops its price text`() = runTest(mainRule.dispatcher) {
        val id = env.subjectDao.upsert(SubjectEntity(name = "История"))
        val vm = vm()
        observing(vm.state)

        vm.toggleSubject(id, on = true)
        vm.setPriceText(id, "25")
        vm.toggleSubject(id, on = false)
        assertTrue(vm.state.value.form.subjectPrices.isEmpty())
    }

    @Test
    fun `save persists a trimmed student with linked, priced subjects and signals done`() = runTest(mainRule.dispatcher) {
        val mathId = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val vm = vm()
        observing(vm.state)

        vm.update { it.copy(name = "  Аня  ", email = " a@b.co ", phone = "  +375 29  ", note = "  hi ") }
        vm.toggleSubject(mathId, on = true)
        vm.setPriceText(mathId, "30,50")

        var done = false
        vm.save { done = true }

        assertTrue(done)
        val student = env.studentDao.items.value.single()
        assertEquals("Аня", student.name)
        assertEquals("a@b.co", student.email)
        assertEquals("+375 29", student.phone)
        assertEquals("hi", student.note)
        // Subject is linked AND priced — the two must stay in lock-step.
        assertEquals(listOf(mathId), env.studentDao.getSubjectIds(student.id))
        assertEquals(Money(3050), env.enrollmentRepo.priceFor(student.id, mathId)?.amount)
    }

    /**
     * A tutor with no subjects reached this form from the schedule's «Добавить ученика» and met a refusal
     * with nowhere to go. A subject made here exists at once and comes back ticked, waiting for its price.
     */
    @Test
    fun `a subject added from the form is made and ticked`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.addSubject("  Физика ")
        advanceUntilIdle()

        val physics = env.subjectDao.items.value.single()
        assertEquals("Физика", physics.name)
        assertEquals(setOf(physics.id), vm.state.value.selectedSubjectIds)
        assertEquals(listOf("Физика"), vm.state.value.allSubjects.valueOrNull()?.map { it.name })
    }

    /** Typing the name of a subject that exists ticks that one rather than making a twin of it. */
    @Test
    fun `a subject added by a name that exists ticks the existing one`() = runTest(mainRule.dispatcher) {
        val math = env.fixtures.subject("Математика")
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.addSubject("математика")
        advanceUntilIdle()

        assertEquals(1, env.subjectDao.items.value.size)
        assertEquals(setOf(math), vm.state.value.selectedSubjectIds)
    }

    @Test
    fun `a blank subject name makes nothing`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)

        vm.addSubject("   ")
        advanceUntilIdle()

        assertTrue(env.subjectDao.items.value.isEmpty())
    }

    @Test
    fun `a second tap while saving creates no second student`() = runTest(mainRule.dispatcher) {
        val mathId = env.fixtures.subject("Математика")
        val vm = vm()
        observing(vm.state)
        vm.update { it.copy(name = "Аня") }
        vm.toggleSubject(mathId, on = true)
        vm.setPriceText(mathId, "30")

        var saved = 0
        vm.save { saved++ }
        vm.save { saved++ }
        advanceUntilIdle()

        assertEquals(1, saved)
        assertEquals(1, env.studentDao.items.value.size)
    }

    /** The form opens on the dialling code; a student saved without a number has none, not «+375». */
    @Test
    fun `a phone left on its dialling code is saved as no phone`() = runTest(mainRule.dispatcher) {
        val mathId = env.fixtures.subject("Математика")
        val vm = vm()
        observing(vm.state)

        vm.update { it.copy(name = "Аня") }
        vm.toggleSubject(mathId, on = true)
        vm.setPriceText(mathId, "30")
        vm.save {}
        advanceUntilIdle()

        assertEquals("", env.studentDao.items.value.single().phone)
    }

    /**
     * What back has to consult. The phone prefix the form opens with is a default, not typed input,
     * so it must not on its own make leaving ask a question.
     */
    @Test
    fun `an untouched form is clean, and clearing an edit makes it clean again`() = runTest(mainRule.dispatcher) {
        val mathId = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val vm = vm()
        observing(vm.state)

        assertFalse("the prefilled phone prefix is not the tutor's work", vm.state.value.isDirty)

        vm.update { it.copy(name = "Аня") }
        assertTrue(vm.state.value.isDirty)

        vm.update { it.copy(name = "") }
        assertFalse("deleting what was typed leaves nothing to save", vm.state.value.isDirty)

        vm.toggleSubject(mathId, on = true)
        assertTrue("a ticked subject is unsaved work even before it is priced", vm.state.value.isDirty)
    }

    // --- A student and their prices are one act ---------------------------------------------------

    /**
     * The form refuses to submit a student with no priced subject, because nothing could be booked for
     * one. Its own save used to be able to produce exactly that: two writes in `viewModelScope`, then
     * navigation, so going back in between cancelled the second. One transaction is what makes the
     * invariant the form checks the invariant the database ends up with.
     */
    @Test
    fun `a student and their priced subjects are written in one transaction`() = runTest(mainRule.dispatcher) {
        val mathId = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val vm = vm()
        observing(vm.state)
        vm.update { it.copy(name = "Аня") }
        vm.toggleSubject(mathId, on = true)
        vm.setPriceText(mathId, "30")

        val before = env.transactor.count
        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertTrue(done)
        assertEquals("one flat transaction, not one per table", 1, env.transactor.count - before)
        assertEquals(1, env.studentDao.items.value.size)
        val studentId = env.studentDao.items.value.single().id
        assertEquals(listOf(mathId), env.studentDao.links.value.filter { it.studentId == studentId }.map { it.subjectId })
        assertEquals(Money.ofMajor(30).minor, env.priceDao.items.value.single().amountMinor)
    }

    @Test
    fun `a creation that fails to commit writes no half-student and is reported`() = runTest(mainRule.dispatcher) {
        val mathId = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val vm = vm()
        observing(vm.state)
        vm.update { it.copy(name = "Аня") }
        vm.toggleSubject(mathId, on = true)
        vm.setPriceText(mathId, "30")

        env.transactor.failAt = env.transactor.count + 1
        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertFalse("a failed write must not navigate away as though it had saved", done)
        assertEquals(StudentFormViewModel.Event.SAVE_FAILED, vm.events.stream.first())
    }

    /**
     * The write outlives the screen: the form hands it to the application scope and keeps only the
     * navigation, so leaving the moment after tapping «Сохранить» abandons the *navigation* and not
     * the student.
     */
    @Test
    fun `the write survives the screen going away`() = runTest(mainRule.dispatcher) {
        val mathId = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val vm = vm()
        observing(vm.state)
        vm.update { it.copy(name = "Аня") }
        vm.toggleSubject(mathId, on = true)
        vm.setPriceText(mathId, "30")

        vm.save { }
        // Whatever the screen does next, the student and their price are already the scope's business.
        advanceUntilIdle()

        assertEquals(1, env.studentDao.items.value.size)
        assertEquals(1, env.priceDao.items.value.size)
    }

    @Test
    fun `save does nothing when the form is invalid`() = runTest(mainRule.dispatcher) {
        env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val vm = vm()
        observing(vm.state)

        vm.update { it.copy(name = "") } // no name, no subjects

        var done = false
        vm.save { done = true }

        assertFalse(done)
        assertTrue(env.studentDao.items.value.isEmpty())
        assertNull(env.priceDao.items.value.firstOrNull())
    }
}
