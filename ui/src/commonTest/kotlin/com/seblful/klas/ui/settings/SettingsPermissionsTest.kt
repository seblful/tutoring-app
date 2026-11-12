package com.seblful.klas.ui.settings

import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPermissionsTest : SettingsTestFixture() {

    @Test
    fun `turning a digest on asks for the notification permission reminders would`() =
        runTest(main.dispatcher) {
            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()
            env.notificationPermission.granted = false

            assertEquals(
                NotificationsIntent.Request,
                vm.setDigestEnabled(on = true) { it.copy(debtDigestEnabled = true) },
            )
            assertEquals(
                NotificationsIntent.Nothing,
                vm.setDigestEnabled(on = false) { it.copy(debtDigestEnabled = false) },
            )
            advanceUntilIdle()
            assertEquals(false, env.settingsRepo.get().debtDigestEnabled)
        }

    @Test
    fun `turning reminders on asks for the permission only when there is one to ask for`() =
        runTest(main.dispatcher) {
            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()

            assertEquals(
                NotificationsIntent.Nothing,
                vm.setNotificationsEnabled(on = false),
                "switching off asks nothing",
            )
            assertEquals(
                NotificationsIntent.Nothing,
                vm.setNotificationsEnabled(on = true),
                "already granted, so nothing to ask",
            )

            env.notificationPermission.granted = false
            vm.refreshPermissions()
            advanceUntilIdle()
            assertEquals(
                NotificationsIntent.Request,
                vm.setNotificationsEnabled(on = true),
            )

            env.notificationPermission.canAsk = false
            assertEquals(NotificationsIntent.BlockedBySystem, vm.setNotificationsEnabled(on = true))
        }

    @Test
    fun `asking for the permission and storing the setting are one act`() = runTest(main.dispatcher) {
        val vm = vm()
        observing(vm.state)
        env.notificationPermission.granted = false
        advanceUntilIdle()

        val intent = vm.setNotificationsEnabled(on = true)
        advanceUntilIdle()

        assertEquals(NotificationsIntent.Request, intent)
        assertTrue(vm.state.current.settings.notificationsEnabled, "and the setting it asked about was stored")
        assertEquals(
            ReminderDelivery.BLOCKED,
            vm.state.current.reminders,
            "so the row reports the state the app is actually in",
        )

        assertEquals(NotificationsIntent.Nothing, vm.setNotificationsEnabled(on = false))
        advanceUntilIdle()
        assertFalse(vm.state.current.settings.notificationsEnabled)
    }

    @Test
    fun `the platform's answer is reported apart from the tutor's intent`() = runTest(main.dispatcher) {
        env.notificationPermission.granted = false
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertTrue(vm.state.current.settings.notificationsEnabled, "the tutor asked for reminders")
        assertEquals(
            ReminderDelivery.BLOCKED,
            vm.state.current.reminders,
            "but the platform will not show them",
        )
    }

    @Test
    fun `a reminder that can only be inexact is reported as late - not as working`() =
        runTest(main.dispatcher) {
            env.exactAlarmPermission.granted = false
            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()

            assertEquals(ReminderDelivery.LATE, vm.state.current.reminders)
        }

    @Test
    fun `nothing postable outranks nothing punctual`() = runTest(main.dispatcher) {
        env.notificationPermission.granted = false
        env.exactAlarmPermission.granted = false
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(ReminderDelivery.BLOCKED, vm.state.current.reminders)
    }

    @Test
    fun `granting the permission is picked up on the next check`() = runTest(main.dispatcher) {
        env.notificationPermission.granted = false
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()
        assertEquals(ReminderDelivery.BLOCKED, vm.state.current.reminders)

        env.notificationPermission.granted = true
        vm.refreshPermissions()
        advanceUntilIdle()

        assertEquals(ReminderDelivery.ON_TIME, vm.state.current.reminders)
    }

    @Test
    fun `a fresh install can ask for the permission without touching the switch`() =
        runTest(main.dispatcher) {
            env.notificationPermission.granted = false
            val vm = vm()
            observing(vm.state)
            advanceUntilIdle()

            assertTrue(vm.state.current.settings.notificationsEnabled, "reminders are on by default")
            assertEquals(ReminderDelivery.BLOCKED, vm.state.current.reminders)

            assertEquals(
                NotificationsIntent.Request,
                vm.unblockNotifications(),
                "and the row can ask for what the switch never could",
            )
        }

    @Test
    fun `the blocked row says so when there is nothing left to ask`() = runTest(main.dispatcher) {
        env.notificationPermission.granted = false
        env.notificationPermission.canAsk = false
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(NotificationsIntent.BlockedBySystem, vm.unblockNotifications())
    }

    @Test
    fun `unblocking does not write the setting`() = runTest(main.dispatcher) {
        env.notificationPermission.granted = false
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()
        val before = env.settingsRepo.get()

        vm.unblockNotifications()
        advanceUntilIdle()

        assertEquals(before, env.settingsRepo.get(), "no write went out")
    }

    @Test
    fun `a granted permission has nothing to unblock`() = runTest(main.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(NotificationsIntent.Nothing, vm.unblockNotifications())
    }

    @Test
    fun `revoking the permission is picked up too`() = runTest(main.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()
        assertEquals(ReminderDelivery.ON_TIME, vm.state.current.reminders)

        env.notificationPermission.granted = false
        vm.refreshPermissions()
        advanceUntilIdle()

        assertEquals(ReminderDelivery.BLOCKED, vm.state.current.reminders)
    }

    @Test
    fun `a reminder switched off reports nothing about the platform`() = runTest(main.dispatcher) {
        env.notificationPermission.granted = false
        env.exactAlarmPermission.granted = false
        val vm = vm()
        observing(vm.state)

        vm.update { it.copy(notificationsEnabled = false) }
        advanceUntilIdle()

        assertEquals(ReminderDelivery.OFF, vm.state.current.reminders)
    }

    @Test
    fun `the setting can always be turned off`() = runTest(main.dispatcher) {
        env.notificationPermission.granted = false
        val vm = vm()
        observing(vm.state)

        vm.update { it.copy(notificationsEnabled = false) }
        advanceUntilIdle()

        assertFalse(env.settingsRepo.get().notificationsEnabled)
    }
}
