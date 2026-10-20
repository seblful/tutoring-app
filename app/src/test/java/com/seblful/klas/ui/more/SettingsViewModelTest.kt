package com.seblful.klas.ui.more

import com.seblful.klas.calendar.Guest
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.settings.SettingsWorkflow
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.state.Loadable
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val env = TestEnv()

    private fun vm() = SettingsViewModel(
        env.settingsRepo, SettingsWorkflow(env.settingsRepo, env.calendarMirror, env.reminders, env.maintenance),
        env.calendarPermission, env.notificationPermission, env.exactAlarmPermission, mainRule.appScope,
    )

    @Test
    fun `the settings are Loading until the row is read, and then say what is stored`() =
        runTest(mainRule.dispatcher) {
            env.settingsRepo.update { it.copy(notificationsEnabled = false, currency = Currency.RUB) }

            val vm = vm()
            assertEquals(Loadable.Loading, vm.state.value)

            observing(vm.state)
            advanceUntilIdle()

            assertFalse("the tutor's own answer, not the default", vm.state.current.notificationsEnabled)
            assertEquals(Currency.RUB, vm.state.current.currency)
        }

    /**
     * Turning reminders on has three possible consequences, and telling the last two apart is the point:
     * "ask the platform" and "the platform has nothing to ask" look identical from the setting's side and
     * behave completely differently. This was three nested `if`s inside a composable's callback.
     */
    @Test
    fun `turning a digest on asks for the notification permission reminders would`() =
        runTest(mainRule.dispatcher) {
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
        runTest(mainRule.dispatcher) {
            val vm = vm()
            observing(vm.reminderDelivery)
            advanceUntilIdle()

            assertEquals(
                "switching off asks nothing",
                NotificationsIntent.Nothing,
                vm.setNotificationsEnabled(on = false),
            )
            assertEquals(
                "already granted, so nothing to ask",
                NotificationsIntent.Nothing,
                vm.setNotificationsEnabled(on = true),
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
    fun `asking for the permission and storing the setting are one act`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)
        observing(vm.reminderDelivery)
        env.notificationPermission.granted = false
        advanceUntilIdle()

        val intent = vm.setNotificationsEnabled(on = true)
        advanceUntilIdle()

        assertEquals(NotificationsIntent.Request, intent)
        assertTrue("and the setting it asked about was stored", vm.state.current.notificationsEnabled)
        assertEquals(
            "so the row reports the state the app is actually in",
            ReminderDelivery.BLOCKED,
            vm.reminderDelivery.value,
        )

        assertEquals(NotificationsIntent.Nothing, vm.setNotificationsEnabled(on = false))
        advanceUntilIdle()
        assertFalse(vm.state.current.notificationsEnabled)
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

        assertNull(vm.state.current.defaultGroupPrice)

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
    fun `turning email invites off withdraws them from the classes already synced`() =
        runTest(mainRule.dispatcher) {
            val sid = env.studentDao.upsert(StudentEntity(name = "Аня", email = "anya@example.com"))
            val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
            env.fixtures.storedClass(
                studentId = sid,
                subjectId = subj,
                startMillis = 1,
                durationMinutes = 60,
                status = LessonStatus.SCHEDULED,
            )
            env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
            env.settingsRepo.update { it.copy(emailInvitesEnabled = true) }

            val vm = vm()
            vm.update { it.copy(emailInvitesEnabled = false) }
            advanceUntilIdle()

            assertFalse(env.settingsRepo.get().emailInvitesEnabled)
            assertEquals(Guest("anya@example.com", invited = false), env.calendar.lastGuest)
        }

    @Test
    fun `turning email invites back on re-invites the classes already synced`() =
        runTest(mainRule.dispatcher) {
            val sid = env.studentDao.upsert(StudentEntity(name = "Аня", email = "anya@example.com"))
            val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
            env.fixtures.storedClass(
                studentId = sid,
                subjectId = subj,
                startMillis = 1,
                durationMinutes = 60,
                status = LessonStatus.SCHEDULED,
            )
            env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
            env.settingsRepo.update { it.copy(emailInvitesEnabled = false) }

            val vm = vm()
            vm.update { it.copy(emailInvitesEnabled = true) }
            advanceUntilIdle()

            assertTrue(env.settingsRepo.get().emailInvitesEnabled)
            assertEquals(Guest("anya@example.com", invited = true), env.calendar.lastGuest)
        }

    @Test
    fun `toggling email invites while calendar sync is off writes nothing to the calendar`() =
        runTest(mainRule.dispatcher) {
            val sid = env.studentDao.upsert(StudentEntity(name = "Аня", email = "anya@example.com"))
            val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
            env.fixtures.storedClass(
                studentId = sid,
                subjectId = subj,
                startMillis = 1,
                durationMinutes = 60,
                status = LessonStatus.SCHEDULED,
            )

            val vm = vm()
            vm.update { it.copy(emailInvitesEnabled = false) }
            advanceUntilIdle()

            assertFalse(env.settingsRepo.get().emailInvitesEnabled)
            assertTrue(env.calendar.calls.isEmpty())
        }

    @Test
    fun `changing the reminder lead re-mirrors the calendar and re-plans the reminders`() =
        runTest(mainRule.dispatcher) {
            val subj = env.fixtures.subject("Математика")
            val sid = env.fixtures.student("Аня", email = "anya@example.com")
            env.fixtures.withCalendarSync()
            env.fixtures.lesson(subj, listOf(sid), start = Instant.parse("2026-03-02T09:00:00Z"))
            env.calendar.calls.clear()
            val refreshesBefore = env.reminders.refreshCount

            val vm = vm()
            vm.update { it.copy(reminderLeadMinutes = 24 * 60) }
            advanceUntilIdle()

            assertEquals("the event is re-mirrored under the new lead", 1, env.calendar.upserts.size)
            assertEquals("and the alarm plan is re-derived", refreshesBefore + 1, env.reminders.refreshCount)
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

    @Test
    fun `enabling calendar sync backfills events for existing classes`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.fixtures.storedClass(
            studentId = sid,
            subjectId = subj,
            startMillis = 1,
            durationMinutes = 60,
            status = LessonStatus.SCHEDULED,
        )

        val vm = vm()
        assertEquals(CalendarSyncToggle.APPLIED, vm.setCalendarSyncEnabled(true))
        advanceUntilIdle()

        assertTrue(env.settingsRepo.get().calendarSyncEnabled)
        assertEquals(1, env.calendar.upserts.size) // the scheduled class got mirrored
    }

    @Test
    fun `enabling calendar sync without the permission changes nothing`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.fixtures.storedClass(
            studentId = sid,
            subjectId = subj,
            startMillis = 1,
            durationMinutes = 60,
            status = LessonStatus.SCHEDULED,
        )
        env.calendarPermission.granted = false

        val vm = vm()
        assertEquals(CalendarSyncToggle.PERMISSION_NEEDED, vm.setCalendarSyncEnabled(true))
        advanceUntilIdle()

        assertFalse(env.settingsRepo.get().calendarSyncEnabled)
        assertTrue(env.calendar.upserts.isEmpty())
    }

    @Test
    fun `enabling succeeds once the permission is granted`() = runTest(mainRule.dispatcher) {
        env.calendarPermission.granted = false
        val vm = vm()
        assertEquals(CalendarSyncToggle.PERMISSION_NEEDED, vm.setCalendarSyncEnabled(true))

        env.calendarPermission.granted = true
        assertEquals(CalendarSyncToggle.APPLIED, vm.setCalendarSyncEnabled(true))
        advanceUntilIdle()

        assertTrue(env.settingsRepo.get().calendarSyncEnabled)
    }

    @Test
    fun `the sync row reports a revoked permission only while sync is on`() {
        assertEquals(CalendarDelivery.OFF, CalendarDelivery.of(enabled = false, permitted = false))
        assertEquals(CalendarDelivery.OFF, CalendarDelivery.of(enabled = false, permitted = true))
        assertEquals(CalendarDelivery.BLOCKED, CalendarDelivery.of(enabled = true, permitted = false))
        assertEquals(CalendarDelivery.ON, CalendarDelivery.of(enabled = true, permitted = true))
    }

    @Test
    fun `revoking the calendar permission while away turns the sync row blocked`() = runTest(mainRule.dispatcher) {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val vm = vm()
        observing(vm.calendarDelivery)
        advanceUntilIdle()
        assertEquals(CalendarDelivery.ON, vm.calendarDelivery.value)

        env.calendarPermission.granted = false
        vm.refreshPermissions()
        advanceUntilIdle()

        assertEquals(CalendarDelivery.BLOCKED, vm.calendarDelivery.value)
    }

    @Test
    fun `a calendar request answered with every grant turns sync on`() = runTest(mainRule.dispatcher) {
        env.calendarPermission.granted = false
        val vm = vm()
        assertEquals(CalendarSyncToggle.PERMISSION_NEEDED, vm.setCalendarSyncEnabled(true))

        env.calendarPermission.granted = true
        val answer = vm.calendarPermissionAnswered()
        advanceUntilIdle()

        assertEquals(CalendarSyncToggle.APPLIED, answer)
        assertTrue(env.settingsRepo.get().calendarSyncEnabled)
    }

    @Test
    fun `a calendar request that leaves the permission not held leaves sync off`() =
        runTest(mainRule.dispatcher) {
            env.calendarPermission.granted = false
            val vm = vm()

            assertEquals(CalendarSyncToggle.PERMISSION_NEEDED, vm.calendarPermissionAnswered())
            advanceUntilIdle()

            assertFalse(env.settingsRepo.get().calendarSyncEnabled)
        }

    @Test
    fun `disabling calendar sync never needs the permission — clearing up must always work`() =
        runTest(mainRule.dispatcher) {
            val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
            val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
            env.fixtures.storedClass(
                studentId = sid,
                subjectId = subj,
                startMillis = 1,
                durationMinutes = 60,
                status = LessonStatus.SCHEDULED,
                calendarEventId = "evt-9",
            )
            env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
            env.calendarPermission.granted = false

            val vm = vm()
            assertEquals(CalendarSyncToggle.APPLIED, vm.setCalendarSyncEnabled(false))
            advanceUntilIdle()

            assertFalse(env.settingsRepo.get().calendarSyncEnabled)
            assertEquals(listOf("evt-9"), env.calendar.deletes)
        }

    @Test
    fun `disabling calendar sync clears the events we created`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.fixtures.storedClass(
            studentId = sid,
            subjectId = subj,
            startMillis = 1,
            durationMinutes = 60,
            status = LessonStatus.SCHEDULED,
            calendarEventId = "evt-1",
        )
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }

        val vm = vm()
        vm.setCalendarSyncEnabled(false)
        advanceUntilIdle()

        assertFalse(env.settingsRepo.get().calendarSyncEnabled)
        assertEquals(listOf("evt-1"), env.calendar.deletes)
        assertEquals(null, env.classes().single().calendarEventId)
    }

    private suspend fun finishedLessons(count: Int): List<Long> {
        val subjectId = env.fixtures.subject("Математика")
        val studentId = env.fixtures.pricedStudent(subjectId = subjectId, rate = Money.ofMajor(30))
        val ended = env.clock.now().minus((30L * 24 * 3600).seconds)
        return (1..count).map { n ->
            env.fixtures.lesson(
                subjectId = subjectId,
                students = listOf(studentId),
                start = ended.plus((n * 2L * 3600).seconds),
            ).single()
        }
    }

    private suspend fun statuses(ids: List<Long>) = ids.map { env.classRepo.getById(it)?.status }

    @Test
    fun `a backlog of any size is asked about before anything is stored`() =
        runTest(mainRule.dispatcher) {
            val ids = finishedLessons(3)

            val vm = vm()
            vm.setAutoCompleteEnabled(true)
            advanceUntilIdle()

            assertEquals(3, vm.pendingSweep.value)
            assertFalse("the switch stays off while the question is up", env.settingsRepo.get().autoCompleteEnabled)
            assertTrue(statuses(ids).all { it == LessonStatus.SCHEDULED })
        }

    @Test
    fun `confirming the question turns it on and settles the backlog`() = runTest(mainRule.dispatcher) {
        val ids = finishedLessons(3)
        val vm = vm()
        vm.setAutoCompleteEnabled(true)
        advanceUntilIdle()

        vm.confirmPendingSweep()
        advanceUntilIdle()

        assertNull(vm.pendingSweep.value)
        assertTrue(env.settingsRepo.get().autoCompleteEnabled)
        assertEquals(List(3) { LessonStatus.COMPLETED }, statuses(ids))
    }

    @Test
    fun `and says how many it settled`() = runTest(mainRule.dispatcher) {
        finishedLessons(3)
        val vm = vm()
        val reported = mutableListOf<Int>()
        backgroundScope.launch { vm.swept.stream.collect { reported += it } }

        vm.setAutoCompleteEnabled(true)
        advanceUntilIdle()
        vm.confirmPendingSweep()
        advanceUntilIdle()

        assertEquals(listOf(3), reported)
    }

    @Test
    fun `nothing waiting means nothing to ask`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        val reported = mutableListOf<Int>()
        backgroundScope.launch { vm.swept.stream.collect { reported += it } }

        vm.setAutoCompleteEnabled(true)
        advanceUntilIdle()

        assertNull("no question", vm.pendingSweep.value)
        assertTrue("the setting simply goes on", env.settingsRepo.get().autoCompleteEnabled)
        assertTrue("and nothing is reported", reported.isEmpty())
    }

    @Test
    fun `declining leaves the switch off and the lessons alone`() = runTest(mainRule.dispatcher) {
        val ids = finishedLessons(3)
        val vm = vm()
        vm.setAutoCompleteEnabled(true)
        advanceUntilIdle()

        vm.dismissPendingSweep()
        advanceUntilIdle()

        assertNull(vm.pendingSweep.value)
        assertFalse(env.settingsRepo.get().autoCompleteEnabled)
        assertTrue(statuses(ids).all { it == LessonStatus.SCHEDULED })
    }

    @Test
    fun `declining and trying again asks again`() = runTest(mainRule.dispatcher) {
        finishedLessons(3)
        val vm = vm()
        vm.setAutoCompleteEnabled(true)
        advanceUntilIdle()
        vm.dismissPendingSweep()

        vm.setAutoCompleteEnabled(true)
        advanceUntilIdle()

        assertEquals(3, vm.pendingSweep.value)
    }

    @Test
    fun `turning auto-completion off settles nothing`() = runTest(mainRule.dispatcher) {
        env.settingsRepo.update { it.copy(autoCompleteEnabled = true) }
        val ids = finishedLessons(3)

        val vm = vm()
        vm.setAutoCompleteEnabled(false)
        advanceUntilIdle()

        assertFalse(env.settingsRepo.get().autoCompleteEnabled)
        assertTrue(statuses(ids).all { it == LessonStatus.SCHEDULED })
    }

    @Test
    fun `the platform's answer is reported apart from the tutor's intent`() = runTest(mainRule.dispatcher) {
        env.notificationPermission.granted = false
        val vm = vm()
        observing(vm.state, vm.reminderDelivery)
        advanceUntilIdle()

        assertTrue("the tutor asked for reminders", vm.state.current.notificationsEnabled)
        assertEquals(
            "but the platform will not show them",
            ReminderDelivery.BLOCKED,
            vm.reminderDelivery.value,
        )
    }

    @Test
    fun `a reminder that can only be inexact is reported as late, not as working`() =
        runTest(mainRule.dispatcher) {
            env.exactAlarmPermission.granted = false
            val vm = vm()
            observing(vm.reminderDelivery)
            advanceUntilIdle()

            assertEquals(ReminderDelivery.LATE, vm.reminderDelivery.value)
        }

    @Test
    fun `nothing postable outranks nothing punctual`() = runTest(mainRule.dispatcher) {
        env.notificationPermission.granted = false
        env.exactAlarmPermission.granted = false
        val vm = vm()
        observing(vm.reminderDelivery)
        advanceUntilIdle()

        assertEquals(ReminderDelivery.BLOCKED, vm.reminderDelivery.value)
    }

    @Test
    fun `granting the permission is picked up on the next check`() = runTest(mainRule.dispatcher) {
        env.notificationPermission.granted = false
        val vm = vm()
        observing(vm.reminderDelivery)
        advanceUntilIdle()
        assertEquals(ReminderDelivery.BLOCKED, vm.reminderDelivery.value)

        env.notificationPermission.granted = true
        vm.refreshPermissions()
        advanceUntilIdle()

        assertEquals(ReminderDelivery.ON_TIME, vm.reminderDelivery.value)
    }

    @Test
    fun `a fresh install can ask for the permission without touching the switch`() =
        runTest(mainRule.dispatcher) {
            env.notificationPermission.granted = false
            val vm = vm()
            observing(vm.state, vm.reminderDelivery)
            advanceUntilIdle()

            assertTrue("reminders are on by default", vm.state.current.notificationsEnabled)
            assertEquals(ReminderDelivery.BLOCKED, vm.reminderDelivery.value)

            assertEquals(
                "and the row can ask for what the switch never could",
                NotificationsIntent.Request,
                vm.unblockNotifications(),
            )
        }

    @Test
    fun `the blocked row says so when there is nothing left to ask`() = runTest(mainRule.dispatcher) {
        env.notificationPermission.granted = false
        env.notificationPermission.canAsk = false
        val vm = vm()
        observing(vm.reminderDelivery)
        advanceUntilIdle()

        assertEquals(NotificationsIntent.BlockedBySystem, vm.unblockNotifications())
    }

    @Test
    fun `unblocking does not write the setting`() = runTest(mainRule.dispatcher) {
        env.notificationPermission.granted = false
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()
        val before = env.settingsRepo.get()

        vm.unblockNotifications()
        advanceUntilIdle()

        assertEquals("no write went out", before, env.settingsRepo.get())
    }

    @Test
    fun `a granted permission has nothing to unblock`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.reminderDelivery)
        advanceUntilIdle()

        assertEquals(NotificationsIntent.Nothing, vm.unblockNotifications())
    }

    @Test
    fun `revoking the permission is picked up too`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.reminderDelivery)
        advanceUntilIdle()
        assertEquals(ReminderDelivery.ON_TIME, vm.reminderDelivery.value)

        env.notificationPermission.granted = false
        vm.refreshPermissions()
        advanceUntilIdle()

        assertEquals(ReminderDelivery.BLOCKED, vm.reminderDelivery.value)
    }

    @Test
    fun `a reminder switched off reports nothing about the platform`() = runTest(mainRule.dispatcher) {
        env.notificationPermission.granted = false
        env.exactAlarmPermission.granted = false
        val vm = vm()
        observing(vm.reminderDelivery)

        vm.update { it.copy(notificationsEnabled = false) }
        advanceUntilIdle()

        assertEquals(ReminderDelivery.OFF, vm.reminderDelivery.value)
    }

    @Test
    fun `the setting can always be turned off`() = runTest(mainRule.dispatcher) {
        env.notificationPermission.granted = false
        val vm = vm()
        observing(vm.state)

        vm.update { it.copy(notificationsEnabled = false) }
        advanceUntilIdle()

        assertFalse(env.settingsRepo.get().notificationsEnabled)
    }
}
