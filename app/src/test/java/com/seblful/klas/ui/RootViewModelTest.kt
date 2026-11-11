package com.seblful.klas.ui

import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.format.AppLocale
import com.seblful.klas.format.toDevicePreferences
import com.seblful.klas.lock.AppLock
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.onboarding.FirstLesson
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
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RootViewModelTest {

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    @get:Rule(order = 0)
    val env = TestEnv()

    @get:Rule
    val locale = AppLocaleRule.russian()

    private fun vm() = mainRule.own(
        RootViewModel(
            env.settingsRepo, env.deviceLocales.toDevicePreferences(), env.maintenance, mainRule.appScope, AppLock(),
            FirstLesson(),
        ),
    )

    @Test
    fun `foreground locale follows settings before the UI reads them`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.settings)
        advanceUntilIdle()

        env.settingsRepo.update { it.copy(language = AppLanguage.ENGLISH) }
        advanceUntilIdle()

        assertEquals(AppLanguage.ENGLISH, vm.settings.value?.language)
        assertEquals("en", AppLocale.tag)

        env.settingsRepo.update { it.copy(language = AppLanguage.RUSSIAN) }
        advanceUntilIdle()
        assertEquals("ru", AppLocale.tag)
    }

    @Test
    fun `settings read as not-yet-loaded rather than as defaults`() {
        val vm = vm()

        assertNull("null is what makes AppRoot wait rather than guess", vm.settings.value)
    }

    @Test
    fun `the stored settings arrive once the app is watching`() = runTest(mainRule.dispatcher) {
        env.settingsRepo.update {
            it.copy(onboardingComplete = true, themeMode = ThemeChoice.DARK, currency = Currency.RUB)
        }

        val vm = vm()
        observing(vm.settings)
        advanceUntilIdle()

        val settings = vm.settings.value
        assertTrue("onboarding is done, so the tabs open", settings?.onboardingComplete == true)
        assertEquals(ThemeChoice.DARK, settings?.themeMode)
        assertEquals(Currency.RUB, settings?.currency)
    }

    @Test
    fun `the route waits while settings are unread, then onboards or opens the tabs`() {
        val vm = vm()

        assertEquals(RootRoute.LOADING, vm.routeFor(null))
        assertEquals(RootRoute.ONBOARDING, vm.routeFor(AppSettings(onboardingComplete = false)))
        assertEquals(RootRoute.MAIN, vm.routeFor(AppSettings(onboardingComplete = true)))
    }

    @Test
    fun `an unset or unrecognised theme follows the system`() {
        assertEquals(ThemeChoice.LIGHT, ThemeChoice.parse("LIGHT"))
        assertEquals(ThemeChoice.DARK, ThemeChoice.parse("DARK"))
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.parse("SYSTEM"))
        assertEquals("nothing loaded yet", ThemeChoice.SYSTEM, ThemeChoice.parse(null))
        assertEquals("a value this build does not know", ThemeChoice.SYSTEM, ThemeChoice.parse("SEPIA"))
    }

    @Test
    fun `a first run resolves to the defaults rather than staying unloaded`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.settings)
        advanceUntilIdle()

        val settings = vm.settings.value
        assertTrue("the row is missing, not the answer", settings != null)
        assertEquals("onboarding is owed on a fresh install", false, settings?.onboardingComplete)
    }

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
