package com.seblful.repetit.ui.onboarding

import com.seblful.repetit.testutil.MainDispatcherRule
import com.seblful.repetit.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

    private fun vm() = OnboardingViewModel(env.subjectRepo, env.settingsRepo)

    @Test
    fun `addSubject trims the name and ignores a blank one`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.subjects.collect {} }

        vm.addSubject("   ")
        advanceUntilIdle()
        assertTrue(env.subjectDao.items.value.isEmpty())

        vm.addSubject("  Математика  ")
        advanceUntilIdle()
        assertEquals(listOf("Математика"), env.subjectDao.items.value.map { it.name })
    }

    @Test
    fun `complete flips the onboarding flag`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        assertFalse(env.settingsRepo.get().onboardingComplete)

        vm.complete()
        advanceUntilIdle()

        assertTrue(env.settingsRepo.get().onboardingComplete)
    }
}
