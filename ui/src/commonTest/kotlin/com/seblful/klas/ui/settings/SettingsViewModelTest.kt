package com.seblful.klas.ui.settings

import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.Loadable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest : SettingsTestFixture() {

    @Test
    fun `the settings are Loading until the row is read - and then say what is stored`() =
        runTest(main.dispatcher) {
            env.settingsRepo.update { it.copy(notificationsEnabled = false, currency = Currency.RUB) }

            val vm = vm()
            assertEquals(Loadable.Loading, vm.state.value)

            observing(vm.state)
            advanceUntilIdle()

            assertFalse(vm.state.current.settings.notificationsEnabled, "the tutor's own answer, not the default")
            assertEquals(Currency.RUB, vm.state.current.settings.currency)
        }

    @Test
    fun `setters persist each setting`() = runTest(main.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.update { it.copy(reminderLeadMinutes = 30) }
        vm.update { it.copy(autoCompleteEnabled = true) }
        vm.update { it.copy(themeMode = ThemeChoice.DARK) }
        vm.update { it.copy(defaultLessonMinutes = 45) }
        vm.update { it.copy(currency = Currency.RUB) }
        vm.update { it.copy(defaultGroupPrice = Money(2000)) }
        vm.update { it.copy(notificationsEnabled = false) }
        advanceUntilIdle()

        val s = env.settingsRepo.get()
        assertEquals(30, s.reminderLeadMinutes)
        assertTrue(s.autoCompleteEnabled)
        assertEquals(ThemeChoice.DARK, s.themeMode)
        assertEquals(45, s.defaultLessonMinutes)
        assertEquals(Currency.RUB, s.currency)
        assertEquals(Money(2000), s.defaultGroupPrice)
        assertFalse(s.notificationsEnabled)
    }

    @Test
    fun `no default group price is set until the tutor sets one`() = runTest(main.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertNull(vm.state.current.settings.defaultGroupPrice)

        vm.update { it.copy(defaultGroupPrice = Money(2000)) }
        advanceUntilIdle()
        vm.update { it.copy(defaultGroupPrice = null) }
        advanceUntilIdle()
        assertNull(env.settingsRepo.get().defaultGroupPrice)
    }

    @Test
    fun `email invites are off by default - so a fresh install mails nobody unasked`() =
        runTest(main.dispatcher) {
            assertFalse(env.settingsRepo.get().emailInvitesEnabled)
        }

    @Test
    fun `a setting that owes the world nothing discharges nothing`() = runTest(main.dispatcher) {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val refreshesBefore = env.reminders.refreshCount

        val vm = vm()
        vm.update { it.copy(themeMode = ThemeChoice.DARK) }
        advanceUntilIdle()

        assertTrue(env.calendar.calls.isEmpty(), "no calendar work for a theme change")
        assertEquals(refreshesBefore, env.reminders.refreshCount)
    }
}
