package com.seblful.klas.ui.students

import androidx.lifecycle.viewModelScope
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestMain
import com.seblful.klas.testutil.UiTest
import com.seblful.klas.testutil.UiTestEnv
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StudentSaveLifecycleTest : UiTest() {

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
    private val main = TestMain(StandardTestDispatcher())

    private val env = UiTestEnv()

    private suspend fun TestScope.form(): StudentFormViewModel {
        val subject = env.fixtures.subject("Maths")
        val vm = main.own(
            StudentFormViewModel(
                androidx.lifecycle.SavedStateHandle(),
                env.subjectRepo, env.enrollmentRepo, env.studentRepo, env.settingsRepo, main.appScope,
                main.dispatcher,
            ),
        )
        observing(vm.state)
        runCurrent()
        vm.update { it.copy(name = "Ann") }
        vm.toggleSubject(subject, on = true)
        vm.setPriceText(subject, "30")
        runCurrent()
        assertTrue(vm.state.value.canSave)
        return vm
    }

    @Test
    fun `a second tap during a suspended save starts no second transaction`() = runTest(main.dispatcher) {
        val vm = form()
        val before = env.transactor.count
        val gate = env.transactor.pauseNextTransaction()
        var saved = 0
        try {
            vm.save { saved++ }
            runCurrent()
            gate.awaitEntry()
            vm.save { saved++ }
            runCurrent()

            assertEquals(before + 1, env.transactor.count)
            assertEquals(0, saved)
            assertTrue(env.stored().students.isEmpty())
        } finally {
            gate.release()
        }
        advanceUntilIdle()

        assertEquals(1, saved)
        assertEquals(1, env.stored().students.size)
        assertEquals(1, env.stored().prices.size)
    }

    @Test
    fun `clearing the view model during a save keeps the write but drops navigation`() = runTest(main.dispatcher) {
        val vm = form()
        val gate = env.transactor.pauseNextTransaction()
        var navigated = false
        try {
            vm.save { navigated = true }
            runCurrent()
            gate.awaitEntry()
            assertTrue(env.stored().students.isEmpty())

            main.clearViewModels()
            runCurrent()
            assertFalse(vm.viewModelScope.isActive)
            assertTrue(main.appScope.isActive)
        } finally {
            gate.release()
        }
        advanceUntilIdle()

        assertEquals("Ann", env.stored().students.single().name)
        assertEquals(Money.ofMajor(30).minor, env.stored().prices.single().amountMinor)
        assertFalse(navigated)
    }
}
