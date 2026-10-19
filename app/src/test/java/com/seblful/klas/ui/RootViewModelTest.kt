package com.seblful.klas.ui

import com.seblful.klas.data.repository.AppSettings
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.ThemeChoice
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

/**
 * What the app decides before it draws anything: theme, currency, and whether onboarding is owed.
 *
 * The whole of it is one `stateIn`, which is why it had no test — but the *initial value* is a real
 * decision with a visible consequence. `null` means "not read yet", and it is what lets `AppRoot`
 * show a spinner instead of choosing between onboarding and the tabs on a guess.
 *
 * It also carries the upkeep pass the tutor's arrival is worth ([RootViewModel.catchUp]), which is how
 * the app settles the lessons that finished while it was away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RootViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val env = TestEnv()

    private fun vm() = RootViewModel(env.settingsRepo, env.deviceLocales, env.maintenance, mainRule.appScope)

    /**
     * Seeding `AppSettings()` as the initial value instead would read as a fresh install for the
     * moment before the row loads — so a tutor who finished setup months ago would be shown first-run
     * onboarding on every cold start, for one frame, and the app would decide the question before it
     * had the answer.
     */
    @Test
    fun `settings read as not-yet-loaded rather than as defaults`() {
        val vm = vm()

        assertNull("null is what makes AppRoot wait rather than guess", vm.settings.value)
    }

    @Test
    fun `the stored settings arrive once the app is watching`() = runTest(mainRule.dispatcher) {
        env.settingsRepo.update { it.copy(onboardingComplete = true, themeMode = ThemeChoice.DARK, currency = Currency.RUB) }

        val vm = vm()
        observing(vm.settings)
        advanceUntilIdle()

        val settings = vm.settings.value
        assertTrue("onboarding is done, so the tabs open", settings?.onboardingComplete == true)
        assertEquals(ThemeChoice.DARK, settings?.themeMode)
        assertEquals(Currency.RUB, settings?.currency)
    }

    /**
     * Which of the three screens the app opens on. `AppRoot` used to decide it in a `when` over the
     * nullable row, so "not read yet" and "read, and onboarding is owed" were told apart by the order of
     * two arms in a composable.
     */
    @Test
    fun `the route waits while settings are unread, then onboards or opens the tabs`() {
        val vm = vm()

        assertEquals(RootRoute.LOADING, vm.routeFor(null))
        assertEquals(RootRoute.ONBOARDING, vm.routeFor(AppSettings(onboardingComplete = false)))
        assertEquals(RootRoute.MAIN, vm.routeFor(AppSettings(onboardingComplete = true)))
    }

    /**
     * «Follow the system» is a real answer, and so is a value nothing wrote — a settings row that has not
     * arrived must follow the system too, because the alternative is a flash of the wrong theme.
     */
    @Test
    fun `an unset or unrecognised theme follows the system`() {
        assertEquals(ThemeChoice.LIGHT, ThemeChoice.parse("LIGHT"))
        assertEquals(ThemeChoice.DARK, ThemeChoice.parse("DARK"))
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.parse("SYSTEM"))
        assertEquals("nothing loaded yet", ThemeChoice.SYSTEM, ThemeChoice.parse(null))
        assertEquals("a value this build does not know", ThemeChoice.SYSTEM, ThemeChoice.parse("SEPIA"))
    }

    /** A fresh install has no settings row at all, and must still resolve — to the defaults. */
    @Test
    fun `a first run resolves to the defaults rather than staying unloaded`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.settings)
        advanceUntilIdle()

        val settings = vm.settings.value
        assertTrue("the row is missing, not the answer", settings != null)
        assertEquals("onboarding is owed on a fresh install", false, settings?.onboardingComplete)
    }

    /**
     * What the tutor's arrival settles: a lesson whose hour has passed is completed, and completing one is
     * what bills it — which is why this cannot be WorkManager's to do ("as current as WorkManager felt like
     * being").
     *
     * It runs *here*, as the UI comes up, and not from `KlasApp.onCreate`: a worker starting the process
     * ran it there too, settled the backlog, and left the unwatched pass that is supposed to announce the
     * income with nothing to report.
     */
    @Test
    fun `catching up settles the income that was waiting`() = runTest(mainRule.dispatcher) {
        val subjectId = env.fixtures.subject("Математика")
        val studentId = env.fixtures.pricedStudent(subjectId = subjectId, rate = Money.ofMajor(30))
        val lesson = env.fixtures.lesson(
            subjectId = subjectId,
            students = listOf(studentId),
            start = env.clock.now().minus(7200.seconds), // ended an hour ago
        ).single()
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }

        vm().catchUp()
        advanceUntilIdle()

        assertEquals(LessonStatus.COMPLETED, env.classRepo.getById(lesson)?.status)
    }
}
