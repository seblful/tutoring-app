package com.seblful.klas.ui.onboarding

import com.seblful.klas.data.settings.SettingsDao
import com.seblful.klas.data.settings.SettingsEntity
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.format.AppLocale
import com.seblful.klas.settings.DevicePreferences
import com.seblful.klas.settings.SettingsWorkflow
import com.seblful.klas.testutil.TestMain
import com.seblful.klas.testutil.UiTest
import com.seblful.klas.testutil.UiTestEnv
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.valueOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
class OnboardingViewModelTest : UiTest() {

    @BeforeTest
    fun startMain() {
        AppLocale.tag = "ru"
        main.start()
    }

    @AfterTest
    fun finishMainThenCloseDatabase() {
        try {
            main.finish()
        } finally {
            env.close()
            AppLocale.tag = appLocale
        }
    }

    private val main = TestMain()

    /** Storing a language publishes the app's display locale, which is process-wide — put it back. */
    private val appLocale = AppLocale.tag

    private val env = UiTestEnv()

    private val firstLesson = FirstLesson()

    private fun newVm() =
        OnboardingViewModel(
            env.subjectRepo, env.settingsRepo, env.settingsWorkflow, main.appScope, firstLesson, env.clock,
        )

    private fun vm() = main.own(newVm())

    @Test
    fun `the subject list is Loading until read - and Ready even when a fresh install has none`() =
        runTest(main.dispatcher) {
            val vm = vm()

            assertEquals(Loadable.Loading, vm.subjects.value)

            observing(vm.subjects)
            advanceUntilIdle()

            assertEquals(Loadable.Ready(emptyList<Subject>()), vm.subjects.value)
        }

    @Test
    fun `the language and currency start where the phone puts them`() = runTest(main.dispatcher) {
        val minsk = main.own(newVm())
        observing(minsk.locale)
        advanceUntilIdle()

        val chosen = (minsk.locale.value as Loadable.Ready).value
        assertEquals(AppLanguage.SYSTEM, chosen.language, "follow the phone, which is what SYSTEM is for")
        assertEquals(Currency.BYN, chosen.currency, "this fixture's phone is in Belarus")
    }

    @Test
    fun `an English phone in Minsk is offered English and rubles`() = runTest(main.dispatcher) {
        UiTestEnv(preferences = DevicePreferences(listOf("en-BY"), listOf("BYN"))).use { env ->
            val vm = main.own(
                OnboardingViewModel(
                    env.subjectRepo, env.settingsRepo, env.settingsWorkflow, main.appScope, firstLesson, env.clock,
                ),
            )
            observing(vm.locale)
            advanceUntilIdle()

            val chosen = (vm.locale.value as Loadable.Ready).value
            assertEquals(AppLanguage.SYSTEM, chosen.language)
            assertEquals(Currency.BYN, chosen.currency, "the region is what says which currency, never the language")
        }
    }

    @Test
    fun `picking a language or currency stores it at once - with nothing to confirm`() =
        runTest(main.dispatcher) {
            val vm = vm()
            observing(vm.locale)

            vm.setLanguage(AppLanguage.ENGLISH)
            advanceUntilIdle()
            assertEquals(AppLanguage.ENGLISH, env.settingsRepo.get().language)

            vm.setCurrency(Currency.EUR)
            advanceUntilIdle()
            assertEquals(Currency.EUR, env.settingsRepo.get().currency)
            assertEquals(AppLanguage.ENGLISH, env.settingsRepo.get().language, "the other answer is untouched")
        }

    @Test
    fun `the usual lesson length is offered at an hour and stored as picked`() = runTest(main.dispatcher) {
        val vm = vm()
        observing(vm.locale)
        advanceUntilIdle()
        assertEquals(60, vm.locale.value.valueOrNull()?.defaultLessonMinutes)

        vm.setLessonMinutes(90)
        advanceUntilIdle()

        assertEquals(90, env.settingsRepo.get().defaultLessonMinutes)
    }

    @Test
    fun `skipping the setup still persists the currency the phone named`() = runTest(main.dispatcher) {
        UiTestEnv(preferences = DevicePreferences(listOf("de-DE"), listOf("EUR"))).use { env ->
            val vm = main.own(
                OnboardingViewModel(
                    env.subjectRepo, env.settingsRepo, env.settingsWorkflow, main.appScope, firstLesson, env.clock,
                ),
            )

            vm.complete()
            advanceUntilIdle()

            assertEquals(Currency.EUR, env.settingsDao.get()?.currency)
            assertTrue(env.settingsRepo.get().onboardingComplete)
        }
    }

    @Test
    fun `addSubject trims the name and ignores a blank one`() = runTest(main.dispatcher) {
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
    fun `adding a subject that is already there is refused and said`() = runTest(main.dispatcher) {
        val vm = vm()
        val events = mutableListOf<OnboardingViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }

        vm.addSubject("Математика")
        advanceUntilIdle()
        vm.addSubject("  математика ")
        advanceUntilIdle()

        assertEquals(listOf("Математика"), env.stored().subjects.map { it.name })
        assertEquals(listOf<OnboardingViewModel.Event>(OnboardingViewModel.Event.NAME_TAKEN), events)
    }

    @Test
    fun `a write that fails is reported rather than thrown`() = runTest(main.dispatcher) {
        val broken = object : SettingsDao by env.settingsDao {
            override suspend fun upsert(settings: SettingsEntity): Unit = throw IllegalStateException("disk full")
        }
        val brokenRepo = SettingsRepository(broken, DevicePreferences(listOf("ru-BY"), listOf("BYN")))
        val vm = main.own(
            OnboardingViewModel(
                env.subjectRepo,
                brokenRepo,
                SettingsWorkflow(brokenRepo, env.calendarMirror, env.reminders, env.maintenance, env.notifier),
                main.appScope,
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
        runTest(main.dispatcher) {
            val vm = vm()
            observing(vm.subjects)
            advanceUntilIdle()

            assertFalse(vm.restoreNeedsConfirming(), "a fresh install has nothing to lose")

            vm.addSubject("Алгебра")
            advanceUntilIdle()

            assertTrue(vm.restoreNeedsConfirming(), "the subject added in step 1 would go with the replace")
        }

    @Test
    fun `complete flips the onboarding flag`() = runTest(main.dispatcher) {
        val vm = vm()
        assertFalse(env.settingsRepo.get().onboardingComplete)

        vm.complete()
        advanceUntilIdle()

        assertTrue(env.settingsRepo.get().onboardingComplete)
    }

    @Test
    fun `skipping ends first run with no subject and no student`() = runTest(main.dispatcher) {
        val vm = vm()

        vm.complete()
        advanceUntilIdle()

        assertTrue(env.settingsRepo.get().onboardingComplete)
        assertTrue(env.stored().subjects.isEmpty())
        assertTrue(env.stored().students.isEmpty())
    }

    @Test
    fun `finishing with the first lesson hands its student to the app - once`() = runTest(main.dispatcher) {
        val vm = vm()

        vm.complete(bookFor = 7)
        advanceUntilIdle()

        assertTrue(env.settingsRepo.get().onboardingComplete)
        assertEquals(7L, firstLesson.take())
        assertNull(firstLesson.take())
    }
}
