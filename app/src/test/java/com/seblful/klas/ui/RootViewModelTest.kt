package com.seblful.klas.ui

import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What the app decides before it draws anything: theme, currency, and whether onboarding is owed.
 *
 * The whole of it is one `stateIn`, which is why it had no test — but the *initial value* is a real
 * decision with a visible consequence. `null` means "not read yet", and it is what lets `AppRoot`
 * show a spinner instead of choosing between onboarding and the tabs on a guess.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()

    /**
     * Seeding `AppSettings()` as the initial value instead would read as a fresh install for the
     * moment before the row loads — so a tutor who finished setup months ago would be shown first-run
     * onboarding on every cold start, for one frame, and the app would decide the question before it
     * had the answer.
     */
    @Test
    fun `settings read as not-yet-loaded rather than as defaults`() {
        val vm = RootViewModel(env.settingsRepo)

        assertNull("null is what makes AppRoot wait rather than guess", vm.settings.value)
    }

    @Test
    fun `the stored settings arrive once the app is watching`() = runTest(mainRule.dispatcher) {
        env.settingsRepo.update { it.copy(onboardingComplete = true, themeMode = "DARK", currency = "RUB") }

        val vm = RootViewModel(env.settingsRepo)
        observing(vm.settings)
        advanceUntilIdle()

        val settings = vm.settings.value
        assertTrue("onboarding is done, so the tabs open", settings?.onboardingComplete == true)
        assertEquals("DARK", settings?.themeMode)
        assertEquals("RUB", settings?.currency)
    }

    /** A fresh install has no settings row at all, and must still resolve — to the defaults. */
    @Test
    fun `a first run resolves to the defaults rather than staying unloaded`() = runTest(mainRule.dispatcher) {
        val vm = RootViewModel(env.settingsRepo)
        observing(vm.settings)
        advanceUntilIdle()

        val settings = vm.settings.value
        assertTrue("the row is missing, not the answer", settings != null)
        assertEquals("onboarding is owed on a fresh install", false, settings?.onboardingComplete)
    }
}
