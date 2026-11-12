package com.seblful.klas.ui.settings

import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.diagnostics.DeviceInfo
import com.seblful.klas.settings.SettingsWorkflow
import com.seblful.klas.testutil.TestMain
import com.seblful.klas.testutil.UiTest
import com.seblful.klas.testutil.UiTestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDeliveryTest : UiTest() {

    @BeforeTest
    fun startMain() = main.start()

    @AfterTest
    fun finishMainThenCloseDatabase() {
        try {
            main.finish()
        } finally {
            env.close()
        }
    }

    private val main = TestMain()

    private val env = UiTestEnv()

    private fun vm() = main.own(
        SettingsViewModel(
            env.settingsRepo,
            SettingsWorkflow(env.settingsRepo, env.calendarMirror, env.reminders, env.maintenance, env.notifier),
            env.calendarPermission, env.notificationPermission, env.exactAlarmPermission, main.appScope,
            env.subjectRepo, NoCrashLogs, DeviceInfo("1.0", "Android SDK 34", "Pixel"),
        ),
    )

    @Test
    fun `every notification feature reports blocked even with lesson reminders off`() = runTest(main.dispatcher) {
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
            assertEquals(feature, env.settingsRepo.get(), "repair does not change preferences")
        }

        env.notificationPermission.granted = true
        vm.refreshPermissions()
        advanceUntilIdle()
        assertEquals(
            NotificationDelivery.ENABLED, vm.state.current.notifications, "exact-alarm access does not block reports",
        )
    }

    @Test
    fun `the index and a category both refresh permission status`() = runTest(main.dispatcher) {
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
