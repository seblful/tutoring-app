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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest : SettingsTestFixture() {

    @Test
    fun `the settings are Loading until the row is read, and then say what is stored`() =
        runTest(mainRule.dispatcher) {
            env.settingsRepo.update { it.copy(notificationsEnabled = false, currency = Currency.RUB) }

            val vm = vm()
            assertEquals(Loadable.Loading, vm.state.value)

            observing(vm.state)
            advanceUntilIdle()

            assertFalse("the tutor's own answer, not the default", vm.state.current.settings.notificationsEnabled)
            assertEquals(Currency.RUB, vm.state.current.settings.currency)
        }

    @Test
    fun `setters persist each setting`() = runTest(mainRule.dispatcher) {
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
    fun `no default group price is set until the tutor sets one`() = runTest(mainRule.dispatcher) {
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
    fun `email invites are off by default, so a fresh install mails nobody unasked`() =
        runTest(mainRule.dispatcher) {
            assertFalse(env.settingsRepo.get().emailInvitesEnabled)
        }

    @Test
    fun `a setting that owes the world nothing discharges nothing`() = runTest(mainRule.dispatcher) {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val refreshesBefore = env.reminders.refreshCount

        val vm = vm()
        vm.update { it.copy(themeMode = ThemeChoice.DARK) }
        advanceUntilIdle()

        assertTrue("no calendar work for a theme change", env.calendar.calls.isEmpty())
        assertEquals(refreshesBefore, env.reminders.refreshCount)
    }
}
