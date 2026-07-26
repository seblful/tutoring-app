package com.seblful.klas.ui.students

import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

    private fun vm() = StudentFormViewModel(env.studentRepo, env.subjectRepo, env.enrollmentRepo)

    @Test
    fun `form starts invalid and becomes valid with a name and a priced subject`() = runTest(mainRule.dispatcher) {
        val mathId = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }

        assertFalse(vm.state.value.isValid)

        vm.update { it.copy(name = "Аня") }
        assertFalse("a name alone isn't enough — needs a priced subject", vm.state.value.isValid)

        vm.toggleSubject(mathId, on = true)
        assertFalse("a checked subject with no price is invalid", vm.state.value.isValid)

        vm.setPriceText(mathId, "30")
        assertTrue(vm.state.value.isValid)
    }

    @Test
    fun `a blank or unparseable price keeps the form invalid`() = runTest(mainRule.dispatcher) {
        val id = env.subjectDao.upsert(SubjectEntity(name = "Физика"))
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }

        vm.update { it.copy(name = "Иван") }
        vm.toggleSubject(id, on = true)
        vm.setPriceText(id, "abc")
        assertFalse(vm.state.value.isValid)

        vm.setPriceText(id, "")
        assertFalse(vm.state.value.isValid)
    }

    @Test
    fun `setPriceText is ignored for a subject that isn't selected`() = runTest(mainRule.dispatcher) {
        val id = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }

        vm.setPriceText(id, "40") // never toggled on
        assertTrue(vm.state.value.form.subjectPrices.isEmpty())
    }

    @Test
    fun `toggling a subject off drops its price text`() = runTest(mainRule.dispatcher) {
        val id = env.subjectDao.upsert(SubjectEntity(name = "История"))
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }

        vm.toggleSubject(id, on = true)
        vm.setPriceText(id, "25")
        vm.toggleSubject(id, on = false)
        assertTrue(vm.state.value.form.subjectPrices.isEmpty())
    }

    @Test
    fun `save persists a trimmed student with linked, priced subjects and signals done`() = runTest(mainRule.dispatcher) {
        val mathId = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }

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
     * What back has to consult. The phone prefix the form opens with is a default, not typed input,
     * so it must not on its own make leaving ask a question.
     */
    @Test
    fun `an untouched form is clean, and clearing an edit makes it clean again`() = runTest(mainRule.dispatcher) {
        val mathId = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }

        assertFalse("the prefilled phone prefix is not the tutor's work", vm.state.value.isDirty)

        vm.update { it.copy(name = "Аня") }
        assertTrue(vm.state.value.isDirty)

        vm.update { it.copy(name = "") }
        assertFalse("deleting what was typed leaves nothing to save", vm.state.value.isDirty)

        vm.toggleSubject(mathId, on = true)
        assertTrue("a ticked subject is unsaved work even before it is priced", vm.state.value.isDirty)
    }

    @Test
    fun `save does nothing when the form is invalid`() = runTest(mainRule.dispatcher) {
        env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }

        vm.update { it.copy(name = "") } // no name, no subjects

        var done = false
        vm.save { done = true }

        assertFalse(done)
        assertTrue(env.studentDao.items.value.isEmpty())
        assertNull(env.priceDao.items.value.firstOrNull())
    }
}
