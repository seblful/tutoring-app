package com.seblful.klas.ui.common

import androidx.lifecycle.SavedStateHandle
import com.seblful.klas.format.MoneyField
import com.seblful.klas.testutil.TestMain
import com.seblful.klas.testutil.UiTest
import com.seblful.klas.testutil.UiTestEnv
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.groups.GroupFormViewModel
import com.seblful.klas.ui.schedule.booking.BookingIntent
import com.seblful.klas.ui.schedule.booking.ClassEditViewModel
import com.seblful.klas.ui.students.StudentFormViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DraftRestorationTest : UiTest() {

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
    private val env = UiTestEnv()
    private val main = TestMain()

    private fun student(handle: SavedStateHandle) = main.own(
        StudentFormViewModel(
            handle, env.subjectRepo, env.enrollmentRepo, env.studentRepo, env.settingsRepo,
            main.appScope, main.dispatcher,
        ),
    )

    private fun group(handle: SavedStateHandle) = main.own(
        GroupFormViewModel(
            handle, env.groupRepo, env.settingsRepo, env.studentRepo, env.subjectRepo,
            env.rateHistoryRepo, env.enrollmentRepo, main.appScope, main.dispatcher,
        ),
    )

    private fun booking(handle: SavedStateHandle) = main.own(
        ClassEditViewModel(
            handle, env.classRepo, env.studentRepo, env.groupRepo, env.subjectRepo, env.enrollmentRepo,
            env.settingsRepo, env.notificationPermission, main.appScope, main.dispatcher, env.clock,
        ),
    )

    private fun restored(handle: SavedStateHandle): SavedStateHandle =
        SavedStateHandle.createHandle(handle.savedStateProvider().saveState(), null)

    @Test
    fun `a new student's raw fields and selected rates survive a new view model`() = runTest(main.dispatcher) {
        val subject = env.fixtures.subject("Maths")
        val handle = SavedStateHandle()
        val before = student(handle)
        observing(before.state)
        advanceUntilIdle()
        before.update { it.copy(name = "Unfinished", email = "tutor@example.com", note = "Keep this") }
        before.setPriceText(subject, "12.")
        advanceUntilIdle()
        val expected = before.state.value.form
        val saved = restored(handle)
        main.clearViewModels()
        val after = student(saved)
        observing(after.state)
        advanceUntilIdle()
        assertEquals(expected, after.state.value.form)
        assertTrue(after.state.value.isDirty)
    }

    @Test
    fun `reloading an edited student does not overwrite the restored draft`() = runTest(main.dispatcher) {
        val id = env.fixtures.student("Original")
        val handle = SavedStateHandle()
        val before = student(handle)
        observing(before.state)
        before.edit(id)
        advanceUntilIdle()
        before.update { it.copy(name = "Unsaved correction") }
        advanceUntilIdle()
        val saved = restored(handle)
        main.clearViewModels()
        val after = student(saved)
        observing(after.state)
        after.edit(id)
        advanceUntilIdle()
        assertEquals("Unsaved correction", after.state.value.form.name)
        assertTrue(after.state.value.isDirty)
    }

    @Test
    fun `restoring before an edited student loads still reads their stored fields`() = runTest(main.dispatcher) {
        val id = env.fixtures.student("Original")
        val handle = SavedStateHandle()
        val before = student(handle)
        before.edit(id)
        val saved = restored(handle)
        main.clearViewModels()
        val after = student(saved)
        observing(after.state)
        after.edit(id)
        advanceUntilIdle()
        assertEquals("Original", after.state.value.form.name)
    }

    @Test
    fun `an unfinished group survives recreation keeping its typed rate`() = runTest(main.dispatcher) {
        env.fixtures.student("Member")
        val handle = SavedStateHandle()
        val before = group(handle)
        observing(before.state)
        advanceUntilIdle()
        before.update { it.copy(name = "Group draft", price = MoneyField("37."), note = "Keep") }
        advanceUntilIdle()
        val expected = before.state.value.valueOrNull()!!.form
        val saved = restored(handle)
        main.clearViewModels()
        val after = group(saved)
        observing(after.state)
        advanceUntilIdle()
        assertEquals(expected, after.state.value.valueOrNull()!!.form)
        assertTrue(after.state.value.valueOrNull()!!.isDirty)
    }

    @Test
    fun `an edited group reloads its record without replacing restored changes`() = runTest(main.dispatcher) {
        val id = env.fixtures.group(name = "Original")
        val handle = SavedStateHandle(mapOf("id" to id))
        val before = group(handle)
        observing(before.state)
        advanceUntilIdle()
        before.update { it.copy(name = "Corrected", price = MoneyField("47.")) }
        advanceUntilIdle()
        val expected = before.state.value.valueOrNull()!!.form
        val saved = restored(handle)
        main.clearViewModels()
        val after = group(saved)
        observing(after.state)
        advanceUntilIdle()
        assertEquals(expected, after.state.value.valueOrNull()!!.form)
        assertTrue(after.state.value.valueOrNull()!!.isDirty)
    }

    @Test
    fun `a booking draft keeps the chosen date rate and dirty state`() = runTest(main.dispatcher) {
        val handle = SavedStateHandle()
        val before = booking(handle)
        observing(before.state)
        advanceUntilIdle()
        before.change(BookingIntent.Edit { it.copy(date = LocalDate(2026, 11, 9), price = MoneyField("18.")) })
        advanceUntilIdle()
        val expected = before.state.value
        val saved = restored(handle)
        main.clearViewModels()
        val after = booking(saved)
        observing(after.state)
        advanceUntilIdle()
        assertEquals(expected, after.state.value)
    }

    @Test
    fun `an edited booking restores corrections after reloading its session`() = runTest(main.dispatcher) {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val id = env.fixtures.lesson(subject, listOf(student), env.clock.now()).single()
        val handle = SavedStateHandle(mapOf("id" to id))
        val before = booking(handle)
        observing(before.state)
        advanceUntilIdle()
        before.change(BookingIntent.Edit { it.copy(price = MoneyField("99.")) })
        advanceUntilIdle()
        val expected = before.state.value
        val saved = restored(handle)
        main.clearViewModels()
        val after = booking(saved)
        observing(after.state)
        advanceUntilIdle()
        assertEquals(expected, after.state.value)
    }
}
