package com.seblful.klas.ui.onboarding

import com.seblful.klas.data.settings.SettingsDao
import com.seblful.klas.data.settings.SettingsEntity
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.format.toDevicePreferences
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.valueOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    /** Storing a language publishes the app's display locale, which is process-wide — put it back. */
    @get:Rule
    val locale = AppLocaleRule.russian()

    @get:Rule(order = 0)
    val env = TestEnv()

    private val firstLesson = FirstLesson()

    private fun newVm() =
        OnboardingViewModel(env.subjectRepo, env.settingsRepo, mainRule.appScope, firstLesson, env.clock)

    private fun vm() = mainRule.own(newVm())

    @Test
    fun `the subject list is Loading until read, and Ready even when a fresh install has none`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()

            assertEquals(Loadable.Loading, vm.subjects.value)

            observing(vm.subjects)
            advanceUntilIdle()

            assertEquals(Loadable.Ready(emptyList<Subject>()), vm.subjects.value)
        }

    @Test
    fun `the language and currency start where the phone puts them`() = runTest(mainRule.dispatcher) {
        val minsk = mainRule.own(newVm())
        observing(minsk.locale)
        advanceUntilIdle()

        val chosen = (minsk.locale.value as Loadable.Ready).value
        assertEquals("follow the phone, which is what SYSTEM is for", AppLanguage.SYSTEM, chosen.language)
        assertEquals("this fixture's phone is in Belarus", Currency.BYN, chosen.currency)
    }

    @Test
    fun `an English phone in Minsk is offered English and rubles`() = runTest(mainRule.dispatcher) {
        TestEnv(deviceLocales = listOf(java.util.Locale.forLanguageTag("en-BY"))).use { env ->
            val vm = mainRule.own(
                OnboardingViewModel(env.subjectRepo, env.settingsRepo, mainRule.appScope, firstLesson, env.clock),
            )
            observing(vm.locale)
            advanceUntilIdle()

            val chosen = (vm.locale.value as Loadable.Ready).value
            assertEquals(AppLanguage.SYSTEM, chosen.language)
            assertEquals("the region is what says which currency, never the language", Currency.BYN, chosen.currency)
        }
    }

    @Test
    fun `picking a language or currency stores it at once, with nothing to confirm`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            observing(vm.locale)

            vm.setLanguage(AppLanguage.ENGLISH)
            advanceUntilIdle()
            assertEquals(AppLanguage.ENGLISH, env.settingsRepo.get().language)

            vm.setCurrency(Currency.EUR)
            advanceUntilIdle()
            assertEquals(Currency.EUR, env.settingsRepo.get().currency)
            assertEquals("the other answer is untouched", AppLanguage.ENGLISH, env.settingsRepo.get().language)
        }

    @Test
    fun `the usual lesson length is offered at an hour and stored as picked`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.locale)
        advanceUntilIdle()
        assertEquals(60, vm.locale.value.valueOrNull()?.defaultLessonMinutes)

        vm.setLessonMinutes(90)
        advanceUntilIdle()

        assertEquals(90, env.settingsRepo.get().defaultLessonMinutes)
    }

    @Test
    fun `skipping the setup still persists the currency the phone named`() = runTest(mainRule.dispatcher) {
        TestEnv(deviceLocales = listOf(java.util.Locale.forLanguageTag("de-DE"))).use { env ->
            val vm = mainRule.own(
                OnboardingViewModel(env.subjectRepo, env.settingsRepo, mainRule.appScope, firstLesson, env.clock),
            )

            vm.complete()
            advanceUntilIdle()

            assertEquals(Currency.EUR, env.settingsDao.get()?.currency)
            assertTrue(env.settingsRepo.get().onboardingComplete)
        }
    }

    @Test
    fun `addSubject trims the name and ignores a blank one`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.subjects)

        vm.addSubject("   ")
        advanceUntilIdle()
        assertTrue(env.stored().subjects.isEmpty())

        vm.addSubject("  Математика  ")
        advanceUntilIdle()
        assertEquals(listOf("Математика"), env.stored().subjects.map { it.name })
    }

    @Test
    fun `adding a subject that is already there is refused and said`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        val events = mutableListOf<OnboardingViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }

        vm.addSubject("Математика")
        advanceUntilIdle()
        vm.addSubject("  математика ")
        advanceUntilIdle()

        assertEquals(listOf("Математика"), env.stored().subjects.map { it.name })
        assertEquals(listOf(OnboardingViewModel.Event.NAME_TAKEN), events)
    }

    @Test
    fun `a write that fails is reported rather than thrown`() = runTest(mainRule.dispatcher) {
        val broken = object : SettingsDao by env.settingsDao {
            override suspend fun upsert(settings: SettingsEntity): Unit = throw IllegalStateException("disk full")
        }
        val vm = mainRule.own(
            OnboardingViewModel(
                env.subjectRepo, SettingsRepository(broken, env.deviceLocales.toDevicePreferences()), mainRule.appScope,
                firstLesson,
                env.clock,
            ),
        )
        val events = mutableListOf<OnboardingViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }

        vm.setLanguage(AppLanguage.ENGLISH)
        vm.complete()
        advanceUntilIdle()

        assertEquals(List(2) { OnboardingViewModel.Event.WRITE_FAILED }, events)
        assertFalse(env.settingsRepo.get().onboardingComplete)
    }

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

    @Test
    fun `skipping ends first run with no subject and no student`() = runTest(mainRule.dispatcher) {
        val vm = vm()

        vm.complete()
        advanceUntilIdle()

        assertTrue(env.settingsRepo.get().onboardingComplete)
        assertTrue(env.stored().subjects.isEmpty())
        assertTrue(env.stored().students.isEmpty())
    }

    @Test
    fun `finishing with the first lesson hands its student to the app, once`() = runTest(mainRule.dispatcher) {
        val vm = vm()

        vm.complete(bookFor = 7)
        advanceUntilIdle()

        assertTrue(env.settingsRepo.get().onboardingComplete)
        assertEquals(7L, firstLesson.take())
        assertNull(firstLesson.take())
    }
}
