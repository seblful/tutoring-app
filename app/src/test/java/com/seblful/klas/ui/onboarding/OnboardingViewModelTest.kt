package com.seblful.klas.ui.onboarding

import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()

    private fun vm() = OnboardingViewModel(env.subjectRepo, env.settingsRepo, mainRule.dispatcher)

    @Test
    fun `addSubject trims the name and ignores a blank one`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.subjects)

        vm.addSubject("   ")
        advanceUntilIdle()
        assertTrue(env.subjectDao.items.value.isEmpty())

        vm.addSubject("  Математика  ")
        advanceUntilIdle()
        assertEquals(listOf("Математика"), env.subjectDao.items.value.map { it.name })
    }

    /**
     * An import is a **full replace**, so restoring after the tutor has already added something in step 1
     * would take it away without mentioning it. On a genuinely fresh install there is nothing to replace
     * and the confirmation would be a question with one answer. The gate was in the file picker's result
     * callback, where no test could reach it.
     */
    @Test
    fun `restoring asks first only once there is something a full replace would destroy`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            observing(vm.subjects)
            advanceUntilIdle()

            assertFalse("a fresh install has nothing to lose", vm.restoreNeedsConfirming())

            vm.addSubject("Алгебра")
            advanceUntilIdle()

            assertTrue("the subject added in step 1 would go with the replace", vm.restoreNeedsConfirming())
        }

    @Test
    fun `complete flips the onboarding flag`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        assertFalse(env.settingsRepo.get().onboardingComplete)

        vm.complete()
        advanceUntilIdle()

        assertTrue(env.settingsRepo.get().onboardingComplete)
    }

    /** Skipping is the same flip: setup is offered, so first run can end with nothing added. */
    @Test
    fun `skipping ends first run with no subject and no student`() = runTest(mainRule.dispatcher) {
        val vm = vm()

        vm.complete()
        advanceUntilIdle()

        assertTrue(env.settingsRepo.get().onboardingComplete)
        assertTrue(env.subjectDao.items.value.isEmpty())
        assertTrue(env.studentDao.items.value.isEmpty())
    }
}
