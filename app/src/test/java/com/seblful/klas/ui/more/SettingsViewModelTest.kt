package com.seblful.klas.ui.more

import com.seblful.klas.calendar.Guest
import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()

    private fun vm() = SettingsViewModel(
        env.settingsRepo, env.calendarMirror, env.reminders, env.calendarPermission, env.notificationPermission,
    )

    /**
     * Turning reminders on has three possible consequences, and telling the last two apart is the point:
     * "ask the platform" and "the platform has nothing to ask" look identical from the setting's side and
     * behave completely differently. This was three nested `if`s inside a composable's callback.
     */
    @Test
    fun `turning reminders on asks for the permission only when there is one to ask for`() =
        runTest(mainRule.dispatcher) {
            val vm = vm()
            observing(vm.notificationsPermitted)
            advanceUntilIdle()

            assertEquals("switching off asks nothing", NotificationsIntent.Nothing, vm.notificationsIntent(on = false))
            assertEquals(
                "already granted, so nothing to ask",
                NotificationsIntent.Nothing,
                vm.notificationsIntent(on = true),
            )

            env.notificationPermission.granted = false
            vm.refreshPermissions()
            advanceUntilIdle()
            assertEquals(
                NotificationsIntent.Request(arrayOf("POST_NOTIFICATIONS")),
                vm.notificationsIntent(on = true),
            )

            // Notifications off for the whole app: there is no permission to request, so asking would
            // show a dialog that can never be granted. Say so instead.
            env.notificationPermission.required = emptyArray()
            assertEquals(NotificationsIntent.BlockedBySystem, vm.notificationsIntent(on = true))
        }

    @Test
    fun `setters persist each setting`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.update { it.copy(reminderLeadMinutes = 30) }
        vm.update { it.copy(autoCompleteEnabled = true) }
        vm.update { it.copy(themeMode = "DARK") }
        vm.update { it.copy(defaultLessonMinutes = 45) }
        vm.update { it.copy(currency = Currency.RUB) }
        vm.update { it.copy(defaultGroupPrice = Money(2000)) }
        vm.update { it.copy(notificationsEnabled = false) }
        advanceUntilIdle()

        val s = env.settingsRepo.get()
        assertEquals(30, s.reminderLeadMinutes)
        assertTrue(s.autoCompleteEnabled)
        assertEquals("DARK", s.themeMode)
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

        // Null is "not set", so a new group's rate is left blank rather than pre-filled with a guess.
        assertNull(vm.state.value.defaultGroupPrice)

        // And clearing it again is expressible.
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
            env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED"))
            env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
            env.settingsRepo.update { it.copy(emailInvitesEnabled = true) }

            val vm = vm()
            vm.update { it.copy(emailInvitesEnabled = false) }
            advanceUntilIdle()

            assertFalse(env.settingsRepo.get().emailInvitesEnabled)
            // Re-mirrored, carrying the address marked uninvited — that is what withdraws the
            // invitation the student already has, rather than only sparing future lessons.
            assertEquals(Guest("anya@example.com", invited = false), env.calendar.lastGuest)
        }

    @Test
    fun `turning email invites back on re-invites the classes already synced`() =
        runTest(mainRule.dispatcher) {
            val sid = env.studentDao.upsert(StudentEntity(name = "Аня", email = "anya@example.com"))
            val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
            env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED"))
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
            env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED"))

            val vm = vm()
            vm.update { it.copy(emailInvitesEnabled = false) }
            advanceUntilIdle()

            // The preference is remembered for when sync is switched on, but there are no events to
            // invite anyone to — so the resync stays a no-op and needs no calendar permission.
            assertFalse(env.settingsRepo.get().emailInvitesEnabled)
            assertTrue(env.calendar.calls.isEmpty())
        }

    /**
     * The setting the generic path used to drop: the lead is baked into every mirrored event's
     * email reminder and into every armed alarm, so changing it owes both a refresh. Until this
     * went through [SettingsChange] the mirror kept the old lead indefinitely — the tail existed
     * only for the two settings that happened to have named methods.
     */
    @Test
    fun `changing the reminder lead re-mirrors the calendar and re-plans the reminders`() =
        runTest(mainRule.dispatcher) {
            val subj = env.fixtures.subject("Математика")
            val sid = env.fixtures.student("Аня", email = "anya@example.com")
            env.fixtures.withCalendarSync()
            env.fixtures.lesson(subj, listOf(sid), start = java.time.Instant.parse("2026-03-02T09:00:00Z"))
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
        vm.update { it.copy(themeMode = "DARK") }
        advanceUntilIdle()

        assertTrue("no calendar work for a theme change", env.calendar.calls.isEmpty())
        assertEquals(refreshesBefore, env.reminders.refreshCount)
    }

    @Test
    fun `enabling calendar sync backfills events for existing classes`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED"))

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
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED"))
        env.calendarPermission.granted = false

        val vm = vm()
        assertEquals(CalendarSyncToggle.PERMISSION_NEEDED, vm.setCalendarSyncEnabled(true))
        advanceUntilIdle()

        // The flag the whole mirror keys off stays off, and nothing was written.
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
    fun `disabling calendar sync never needs the permission — clearing up must always work`() =
        runTest(mainRule.dispatcher) {
            val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
            val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
            env.classDao.upsert(
                ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED", calendarEventId = "evt-9"),
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
        // A class already carrying a mirrored event id.
        env.classDao.upsert(ClassEntity(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED", calendarEventId = "evt-1"))
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }

        val vm = vm()
        vm.setCalendarSyncEnabled(false)
        advanceUntilIdle()

        assertFalse(env.settingsRepo.get().calendarSyncEnabled)
        assertEquals(listOf("evt-1"), env.calendar.deletes)
        // The event id is cleared off the class.
        assertEquals(null, env.classDao.items.value.single().calendarEventId)
    }

    // --- Intent and platform truth are two facts ---------------------------------------------------

    /**
     * The state a fresh Android 13+ install is in: the setting on by default, the permission never
     * granted, `AndroidNotifier` no-opping in silence. The switch read on while no reminder could ever
     * appear, and nothing anywhere said so.
     *
     * Deliberately *not* modelled as the calendar rule — see `NotificationPermission`. Saying yes is the
     * tutor's to say; whether the platform will honour it is a separate fact the screen shows next to it.
     */
    @Test
    fun `the platform's answer is reported apart from the tutor's intent`() = runTest(mainRule.dispatcher) {
        env.notificationPermission.granted = false
        val vm = vm()
        observing(vm.notificationsPermitted)
        advanceUntilIdle()

        assertTrue("the tutor asked for reminders", vm.state.value.notificationsEnabled)
        assertFalse("but the platform will not show them", vm.notificationsPermitted.value)
    }

    @Test
    fun `granting the permission is picked up on the next check`() = runTest(mainRule.dispatcher) {
        env.notificationPermission.granted = false
        val vm = vm()
        observing(vm.notificationsPermitted)
        advanceUntilIdle()
        assertFalse(vm.notificationsPermitted.value)

        // What returning from the system dialog — or from system settings — amounts to.
        env.notificationPermission.granted = true
        vm.refreshPermissions()
        advanceUntilIdle()

        assertTrue(vm.notificationsPermitted.value)
    }

    /** And revoked the same way: a permission can be taken away while the app is in the background. */
    @Test
    fun `revoking the permission is picked up too`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.notificationsPermitted)
        advanceUntilIdle()
        assertTrue(vm.notificationsPermitted.value)

        env.notificationPermission.granted = false
        vm.refreshPermissions()
        advanceUntilIdle()

        assertFalse(vm.notificationsPermitted.value)
    }

    /** Turning reminders off is never blocked — nothing to ask for, nothing to warn about. */
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
