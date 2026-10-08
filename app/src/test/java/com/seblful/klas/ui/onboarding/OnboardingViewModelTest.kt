package com.seblful.klas.ui.onboarding

import com.seblful.klas.data.local.SettingsDao
import com.seblful.klas.data.local.SettingsEntity
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.Loadable
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

    /** Storing a language publishes the app's display locale, which is process-wide — put it back. */
    @get:Rule
    val locale = AppLocaleRule.russian()

    private val env = TestEnv()

    private fun vm() = OnboardingViewModel(env.subjectRepo, env.settingsRepo, mainRule.appScope)

    /**
     * Empty is usually the truth on this screen — onboarding runs on a fresh install — but «Нет предметов»
     * still has to be said only about a list that was read: the step is reachable again once subjects have
     * been added, and a tutor who has just typed three of them must not see it over them, with «Далее»
     * disabled. So the list is [Loadable] like every other list in the app.
     */
    @Test
    fun `the subject list is Loading until read, and Ready even when a fresh install has none`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()

            assertEquals(Loadable.Loading, vm.subjects.value)

            observing(vm.subjects)
            advanceUntilIdle()

            assertEquals(Loadable.Ready(emptyList<Subject>()), vm.subjects.value)
        }

    /**
     * **Both of step 1's answers arrive already right for the phone**, which is what "default as phone" comes
     * to and the reason the step is a nod rather than a form: `AppLanguage.SYSTEM` is a live rule that follows
     * the device, and the currency is substituted from it by `SettingsRepository` for a row nobody has written
     * yet. Nothing here seeds anything — the point of the assertion is that there is no seeding step to
     * forget.
     */
    @Test
    fun `the language and currency start where the phone puts them`() = runTest(mainRule.dispatcher) {
        val minsk = OnboardingViewModel(env.subjectRepo, env.settingsRepo, mainRule.appScope)
        observing(minsk.locale)
        advanceUntilIdle()

        val chosen = (minsk.locale.value as Loadable.Ready).value
        assertEquals("follow the phone, which is what SYSTEM is for", AppLanguage.SYSTEM, chosen.language)
        assertEquals("this fixture's phone is in Belarus", Currency.BYN, chosen.currency)
    }

    /** The one case the guess gets half right, and the reason the step exists at all. */
    @Test
    fun `an English phone in Minsk is offered English and rubles`() = runTest(mainRule.dispatcher) {
        val env = TestEnv(deviceLocales = listOf(java.util.Locale.forLanguageTag("en-BY")))
        val vm = OnboardingViewModel(env.subjectRepo, env.settingsRepo, mainRule.appScope)
        observing(vm.locale)
        advanceUntilIdle()

        val chosen = (vm.locale.value as Loadable.Ready).value
        assertEquals(AppLanguage.SYSTEM, chosen.language)
        assertEquals("the region is what says which currency, never the language", Currency.BYN, chosen.currency)
    }

    /**
     * Both are stored as they are tapped, and the language one applies to the screen doing the asking —
     * `AppRoot` provides the composition's locale from this row, which is what lets the tutor *see* whether
     * the pre-set answer was right instead of having to believe it.
     */
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

    /**
     * **Skipping keeps the phone's currency**, because the row is written whole from what was read: the tutor
     * who never reaches step 1 still gets their own money labelled, which is the path a per-step seed would
     * have missed.
     */
    @Test
    fun `skipping the setup still persists the currency the phone named`() = runTest(mainRule.dispatcher) {
        val env = TestEnv(deviceLocales = listOf(java.util.Locale.forLanguageTag("de-DE")))
        val vm = OnboardingViewModel(env.subjectRepo, env.settingsRepo, mainRule.appScope)

        vm.complete()
        advanceUntilIdle()

        assertEquals("EUR", env.settingsDao.row.value?.currency)
        assertTrue(env.settingsRepo.get().onboardingComplete)
    }

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
     * Two «Математика» cannot be told apart anywhere a subject is offered, and a double tap on «Добавить»
     * in step 1 was enough to make them. Refused the way the subjects screen refuses it — whatever the case
     * and the spaces — and said, so the tap is not simply lost.
     */
    @Test
    fun `adding a subject that is already there is refused and said`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        val events = mutableListOf<OnboardingViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }

        vm.addSubject("Математика")
        advanceUntilIdle()
        vm.addSubject("  математика ")
        advanceUntilIdle()

        assertEquals(listOf("Математика"), env.subjectDao.items.value.map { it.name })
        assertEquals(listOf(OnboardingViewModel.Event.NAME_TAKEN), events)
    }

    /**
     * Setup's writes used to be bare `viewModelScope.launch`es, so a failed one was an exception with
     * nothing to catch it — a crash in the app's first minute. Now it is reported, and nothing is thrown.
     */
    @Test
    fun `a write that fails is reported rather than thrown`() = runTest(mainRule.dispatcher) {
        val broken = object : SettingsDao by env.settingsDao {
            override suspend fun upsert(settings: SettingsEntity): Unit = throw IllegalStateException("disk full")
        }
        val vm = OnboardingViewModel(env.subjectRepo, SettingsRepository(broken, env.deviceLocales), mainRule.appScope)
        val events = mutableListOf<OnboardingViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { events += it } }

        vm.setLanguage(AppLanguage.ENGLISH)
        vm.complete()
        advanceUntilIdle()

        assertEquals(List(2) { OnboardingViewModel.Event.WRITE_FAILED }, events)
        assertFalse(env.settingsRepo.get().onboardingComplete)
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
