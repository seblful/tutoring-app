package com.seblful.klas.ui.settings

import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.settings.SettingsWorkflow
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsDeliveryTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val env = TestEnv()

    private fun vm() = SettingsViewModel(
        env.settingsRepo, SettingsWorkflow(env.settingsRepo, env.calendarMirror, env.reminders, env.maintenance),
        env.calendarPermission, env.notificationPermission, env.exactAlarmPermission, mainRule.appScope,
    )

    @Test
    fun `every notification feature reports blocked even with lesson reminders off`() = runTest(mainRule.dispatcher) {
        val off = AppSettings(notificationsEnabled = false)
        env.settingsRepo.update { off }
        env.notificationPermission.granted = false
        env.notificationPermission.canAsk = false
        env.exactAlarmPermission.granted = false
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()
        assertEquals(NotificationDelivery.OFF, vm.state.current.notifications)

        val features = listOf(
            off.copy(dailyAgendaEnabled = true),
            off.copy(unfinishedDigestEnabled = true),
            off.copy(debtDigestEnabled = true),
            off.copy(autoCompleteEnabled = true),
        )
        for (feature in features) {
            env.settingsRepo.update { feature }
            advanceUntilIdle()
            assertEquals(NotificationDelivery.BLOCKED, vm.state.current.notifications)
            assertEquals(ReminderDelivery.OFF, vm.state.current.reminders)
            assertEquals(NotificationsIntent.BlockedBySystem, vm.unblockNotifications())
            assertEquals("repair does not change preferences", feature, env.settingsRepo.get())
        }

        env.notificationPermission.granted = true
        vm.refreshPermissions()
        advanceUntilIdle()
        assertEquals(
            "exact-alarm access does not block reports", NotificationDelivery.ENABLED, vm.state.current.notifications,
        )
    }

    @Test
    fun `the index and a category both refresh permission status`() = runTest(mainRule.dispatcher) {
        env.settingsRepo.update { it.copy(notificationsEnabled = false, dailyAgendaEnabled = true) }
        val index = vm()
        val category = vm()
        observing(index.state)
        observing(category.state)
        advanceUntilIdle()

        env.notificationPermission.granted = false
        index.refreshPermissions()
        category.refreshPermissions()
        advanceUntilIdle()
        assertEquals(NotificationDelivery.BLOCKED, index.state.current.notifications)
        assertEquals(NotificationDelivery.BLOCKED, category.state.current.notifications)

        env.notificationPermission.granted = true
        index.refreshPermissions()
        category.refreshPermissions()
        advanceUntilIdle()
        assertEquals(NotificationDelivery.ENABLED, index.state.current.notifications)
        assertEquals(NotificationDelivery.ENABLED, category.state.current.notifications)
    }

    @Test
    fun `shared timing is editable for calendar-only reminders`() {
        val off = AppSettings(notificationsEnabled = false)
        assertFalse(off.canEditReminderLead)
        assertTrue(off.copy(calendarSyncEnabled = true).canEditReminderLead)
        assertTrue(off.copy(notificationsEnabled = true).canEditReminderLead)
    }
}
