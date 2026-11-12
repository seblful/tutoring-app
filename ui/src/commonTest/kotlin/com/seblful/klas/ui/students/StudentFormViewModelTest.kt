package com.seblful.klas.ui.students

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.testutil.TestMain
import com.seblful.klas.testutil.UiTest
import com.seblful.klas.testutil.UiTestEnv
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.FormGap
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.valueOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StudentFormViewModelTest : UiTest() {

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

    private fun vm() = main.own(
        StudentFormViewModel(
            androidx.lifecycle.SavedStateHandle(),
            env.subjectRepo,
            env.enrollmentRepo,
            env.studentRepo,
            env.settingsRepo,
            main.appScope,
            main.dispatcher,
        ),
    )

    @Test
    fun `opened on a student - the form shows them as stored and saves corrections and rates`() =
        runTest(main.dispatcher) {
            val maths = env.fixtures.subject("Maths")
            val physics = env.fixtures.subject("Physics")
            val anya = Student(name = "Аня", phone = "+375 29 1234567")
            val id = env.enrollmentRepo.createStudent(anya, mapOf(maths to Money.ofMajor(30)))
            val vm = vm()
            observing(vm.state)

            vm.edit(id)
            advanceUntilIdle()
            val opened = vm.state.value
            assertFalse(opened.isNew)
            assertEquals("Аня", opened.form.name)
            assertEquals("+375 29 1234567", opened.form.phone)
            assertEquals(setOf(maths), opened.selectedSubjectIds)
            assertFalse(opened.isDirty)

            vm.update { it.copy(name = "  Анна  ") }
            vm.setPriceText(maths, "35")
            vm.toggleSubject(physics, on = true)
            vm.setPriceText(physics, "20")
            advanceUntilIdle()
            var saved = 0L
            vm.create { saved = it }
            advanceUntilIdle()

            assertEquals(id, saved)
            assertEquals("Анна", env.studentRepo.getById(id)?.name)
            assertEquals(Money.ofMajor(35), env.enrollmentRepo.priceFor(id, maths)?.amount)
            assertEquals(Money.ofMajor(20), env.enrollmentRepo.priceFor(id, physics)?.amount)
        }

    @Test
    fun `a subject the student has lessons in stays ticked`() = runTest(main.dispatcher) {
        val maths = env.fixtures.subject("Maths")
        val id = env.enrollmentRepo.createStudent(Student(name = "Аня"), mapOf(maths to Money.ofMajor(30)))
        env.fixtures.storedClass(studentId = id, subjectId = maths, startMillis = 0)
        val vm = vm()
        observing(vm.state)
        vm.edit(id)
        advanceUntilIdle()

        vm.toggleSubject(maths, on = false)
        advanceUntilIdle()

        assertEquals(setOf(maths), vm.state.value.locked)
        assertEquals(setOf(maths), vm.state.value.selectedSubjectIds)
    }

    @Test
    fun `a student with no phone - or only an old dialling code - opens with an empty clean field`() =
        runTest(main.dispatcher) {
            val maths = env.fixtures.subject("Maths")
            val old = Student(name = "Аня", phone = "+375")
            val id = env.enrollmentRepo.createStudent(old, mapOf(maths to Money.ofMajor(30)))
            val vm = vm()
            observing(vm.state)

            vm.edit(id)
            advanceUntilIdle()
            assertEquals("", vm.state.value.form.phone)
            assertFalse(vm.state.value.isDirty)
        }

    @Test
    fun `a picked contact fills the number as the contact has it - and a missing name`() =
        runTest(main.dispatcher) {
            val vm = vm()
            observing(vm.state)

            vm.pickContact(" Мама Ани ", " +375 29 123-45-67 ")
            assertEquals("+375 29 123-45-67", vm.state.value.form.phone)
            assertEquals("Мама Ани", vm.state.value.form.name)
            assertTrue(vm.state.value.isDirty)

            vm.pickContact("Папа", "8 029 123-45-67")
            assertEquals("8 029 123-45-67", vm.state.value.form.phone, "no code is added or taken away")
            assertEquals("Мама Ани", vm.state.value.form.name, "a typed name stays")
        }

    @Test
    fun `a setup detour carries its subject and returns the created student`() = runTest(main.dispatcher) {
        val subject = env.fixtures.subject()
        val vm = vm()
        observing(vm.state)
        vm.suggestSubject(subject)
        advanceUntilIdle()
        assertFalse(vm.state.value.isDirty)
        assertEquals(setOf(subject), vm.state.value.selectedSubjectIds)
        vm.update { it.copy(name = "Created from group") }
        vm.setPriceText(subject, "30")
        advanceUntilIdle()
        var created = 0L
        vm.create { created = it }
        advanceUntilIdle()
        assertEquals("Created from group", env.studentRepo.getById(created)?.name)
        assertEquals(Money.ofMajor(30), env.enrollmentRepo.priceFor(created, subject)?.amount)
    }

    @Test
    fun `only the subject list waits to be read`() = runTest(main.dispatcher) {
        env.fixtures.subject("Математика")
        val vm = vm()

        assertEquals(Loadable.Loading, vm.state.value.allSubjects)
        assertEquals("", vm.state.value.form.name, "but the form itself is already there")

        observing(vm.state)
        advanceUntilIdle()

        assertEquals(
            listOf("Математика"),
            vm.state.value.allSubjects.valueOrNull()?.map { it.name },
        )
    }

    @Test
    fun `form starts invalid and becomes valid with a name and a priced subject`() = runTest(main.dispatcher) {
        val mathId = env.fixtures.subject(name = "Математика")
        val vm = vm()
        observing(vm.state)

        assertFalse(vm.state.value.canSave)
        assertEquals(listOf(FormGap.NAME, FormGap.PRICE), vm.state.value.missing, "the save bar names each gap")

        vm.update { it.copy(name = "Аня") }
        assertFalse(vm.state.value.canSave, "a name alone isn't enough — needs a priced subject")

        vm.toggleSubject(mathId, on = true)
        assertFalse(vm.state.value.canSave, "a checked subject with no price is invalid")
        assertEquals(listOf(FormGap.PRICE), vm.state.value.missing)

        vm.setPriceText(mathId, "30")
        assertTrue(vm.state.value.canSave)
        assertEquals(emptyList<FormGap>(), vm.state.value.missing)
    }

    @Test
    fun `a blank or unparseable price keeps the form invalid`() = runTest(main.dispatcher) {
        val id = env.fixtures.subject(name = "Физика")
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
    fun `setPriceText is ignored for a subject that isn't selected`() = runTest(main.dispatcher) {
        val id = env.fixtures.subject(name = "Химия")
        env.fixtures.subject(name = "Физика") // two, so neither is ticked for the tutor
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.setPriceText(id, "40") // never toggled on
        assertTrue(vm.state.value.form.subjectPrices.isEmpty())
    }

    @Test
    fun `toggling a subject off drops its price text`() = runTest(main.dispatcher) {
        val id = env.fixtures.subject(name = "История")
        val vm = vm()
        observing(vm.state)

        vm.toggleSubject(id, on = true)
        vm.setPriceText(id, "25")
        vm.toggleSubject(id, on = false)
        assertTrue(vm.state.value.form.subjectPrices.isEmpty())
    }

    @Test
    fun `save persists a trimmed student with linked - priced subjects and signals done`() =
        runTest(main.dispatcher) {
            val mathId = env.fixtures.subject(name = "Математика")
            val vm = vm()
            observing(vm.state)

            vm.update { it.copy(name = "  Аня  ", email = " a@b.co ", phone = "  +375 29  ", note = "  hi ") }
            vm.toggleSubject(mathId, on = true)
            vm.setPriceText(mathId, "30,50")

            var done = false
            vm.save { done = true }

            assertTrue(done)
            val student = env.stored().students.single()
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
    fun `a subject added from the form is made and ticked`() = runTest(main.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.addSubject("  Физика ")
        advanceUntilIdle()

        val physics = env.stored().subjects.single()
        assertEquals("Физика", physics.name)
        assertEquals(setOf(physics.id), vm.state.value.selectedSubjectIds)
        assertEquals(listOf("Физика"), vm.state.value.allSubjects.valueOrNull()?.map { it.name })
    }

    @Test
    fun `a subject added by a name that exists ticks the existing one`() = runTest(main.dispatcher) {
        val math = env.fixtures.subject("Математика")
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.addSubject("математика")
        advanceUntilIdle()

        assertEquals(1, env.stored().subjects.size)
        assertEquals(setOf(math), vm.state.value.selectedSubjectIds)
    }

    @Test
    fun `a blank subject name makes nothing`() = runTest(main.dispatcher) {
        val vm = vm()
        observing(vm.state)

        vm.addSubject("   ")
        advanceUntilIdle()

        assertTrue(env.stored().subjects.isEmpty())
    }

    @Test
    fun `reusing a selected subject keeps the price already typed`() = runTest(main.dispatcher) {
        val math = env.fixtures.subject("Math")
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()
        vm.setPriceText(math, "35")

        vm.addSubject(" math ")
        advanceUntilIdle()

        assertEquals(setOf(math), vm.state.value.selectedSubjectIds)
        assertEquals(Money.ofMajor(35), vm.state.value.form.subjectPrices.getValue(math).amount)
        assertEquals(1, env.stored().subjects.size)
    }

    @Test
    fun `a phone left on its dialling code is saved as no phone`() = runTest(main.dispatcher) {
        val mathId = env.fixtures.subject("Математика")
        val vm = vm()
        observing(vm.state)
        vm.update { it.copy(phone = "+7 ") }

        vm.update { it.copy(name = "Аня") }
        vm.toggleSubject(mathId, on = true)
        vm.setPriceText(mathId, "30")
        vm.save {}
        advanceUntilIdle()

        assertEquals("", env.stored().students.single().phone)
    }

    @Test
    fun `an untouched form is clean - and clearing an edit makes it clean again`() = runTest(main.dispatcher) {
        val mathId = env.fixtures.subject(name = "Математика")
        env.fixtures.subject(name = "Физика")
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()
        assertFalse(vm.state.value.isDirty, "an opened form is clean")

        vm.update { it.copy(name = "Аня") }
        assertTrue(vm.state.value.isDirty)

        vm.update { it.copy(name = "") }
        assertFalse(vm.state.value.isDirty, "deleting what was typed leaves nothing to save")

        vm.toggleSubject(mathId, on = true)
        assertTrue(vm.state.value.isDirty, "a ticked subject is unsaved work even before it is priced")
    }

    @Test
    fun `the only subject comes ticked - and the tick is not unsaved work`() = runTest(main.dispatcher) {
        val math = env.fixtures.subject("Математика")
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(setOf(math), vm.state.value.selectedSubjectIds)
        assertFalse(vm.state.value.isDirty)
    }

    @Test
    fun `with several subjects nothing is guessed - and a guess the list outgrows is taken back`() =
        runTest(main.dispatcher) {
            // Setup builds this form before its subjects exist and adds them one by one.
            val vm = vm()
            observing(vm.state)
            val math = env.fixtures.subject("Математика")
            advanceUntilIdle()
            assertEquals(setOf(math), vm.state.value.selectedSubjectIds)

            env.fixtures.subject("Физика")
            advanceUntilIdle()
            assertTrue(vm.state.value.selectedSubjectIds.isEmpty())
        }

    @Test
    fun `a subject the tutor chose is not second-guessed by the list`() = runTest(main.dispatcher) {
        env.fixtures.subject("Математика")
        val physics = env.fixtures.subject("Физика")
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.toggleSubject(physics, on = true)
        vm.toggleSubject(physics, on = false)
        env.subjectRepo.delete(env.subjectRepo.observeAll().first().single { it.id == physics })
        advanceUntilIdle()

        assertTrue(vm.state.value.selectedSubjectIds.isEmpty(), "down to one subject, but the tutor has already chosen")
    }

    @Test
    fun `the usual lesson length is read for the price hint`() = runTest(main.dispatcher) {
        env.settingsRepo.update { it.copy(defaultLessonMinutes = 90) }
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(90, vm.state.value.lessonMinutes)
    }

    @Test
    fun `a student and their priced subjects are written in one transaction`() = runTest(main.dispatcher) {
        val mathId = env.fixtures.subject(name = "Математика")
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
        assertEquals(1, env.transactor.count - before, "one flat transaction, not one per table")
        assertEquals(1, env.stored().students.size)
        val studentId = env.stored().students.single().id
        assertEquals(
            listOf(mathId),
            env.stored().studentSubjects.filter {
                it.studentId == studentId
            }.map { it.subjectId },
        )
        assertEquals(Money.ofMajor(30).minor, env.stored().prices.single().amountMinor)
    }

    @Test
    fun `a creation that fails to commit writes no half-student and is reported`() = runTest(main.dispatcher) {
        val mathId = env.fixtures.subject(name = "Математика")
        val vm = vm()
        observing(vm.state)
        vm.update { it.copy(name = "Аня") }
        vm.toggleSubject(mathId, on = true)
        vm.setPriceText(mathId, "30")

        env.transactor.failAt = env.transactor.count + 1
        var done = false
        vm.save { done = true }
        advanceUntilIdle()

        assertFalse(done, "a failed write must not navigate away as though it had saved")
        assertEquals(StudentFormViewModel.Event.SAVE_FAILED, vm.events.stream.first())
    }

    @Test
    fun `save does nothing when the form is invalid`() = runTest(main.dispatcher) {
        env.fixtures.subject(name = "Математика")
        val vm = vm()
        observing(vm.state)

        vm.update { it.copy(name = "") } // no name, no subjects

        var done = false
        vm.save { done = true }

        assertFalse(done)
        assertTrue(env.stored().students.isEmpty())
        assertNull(env.stored().prices.firstOrNull())
    }
}
