package com.seblful.klas.ui.more

import com.seblful.klas.calendar.Guest
import com.seblful.klas.data.local.ClassEntity
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()

    private fun vm() = SettingsViewModel(env.settingsRepo, env.classRepo, env.calendarPermission, env.notificationPermission)

    @Test
    fun `setters persist each setting`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.update { it.copy(reminderLeadMinutes = 30) }
        vm.update { it.copy(autoCompleteEnabled = true) }
        vm.update { it.copy(themeMode = "DARK") }
        vm.update { it.copy(defaultLessonMinutes = 45) }
        vm.update { it.copy(currency = "RUB") }
        vm.update { it.copy(defaultGroupPriceMinor = 2000) }
        vm.update { it.copy(notificationsEnabled = false) }
        vm.update { it.copy(showGroupsTab = false) }
        advanceUntilIdle()

        val s = env.settingsRepo.get()
        assertEquals(30, s.reminderLeadMinutes)
        assertTrue(s.autoCompleteEnabled)
        assertEquals("DARK", s.themeMode)
        assertEquals(45, s.defaultLessonMinutes)
        assertEquals("RUB", s.currency)
        assertEquals(2000L, s.defaultGroupPriceMinor)
        assertFalse(s.notificationsEnabled)
        assertFalse(s.showGroupsTab)
    }

    @Test
    fun `no default group price is set until the tutor sets one`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        // 0 is "not set", so a new group's rate is left blank rather than pre-filled with a guess.
        assertEquals(0L, vm.state.value.defaultGroupPriceMinor)

        // And clearing it again is expressible — the settings row writes 0 for an empty field.
        vm.update { it.copy(defaultGroupPriceMinor = 2000) }
        advanceUntilIdle()
        vm.update { it.copy(defaultGroupPriceMinor = 0) }
        advanceUntilIdle()
        assertEquals(0L, env.settingsRepo.get().defaultGroupPriceMinor)
    }

    @Test
    fun `the groups tab is on by default and switching it back on sticks`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertTrue("a fresh install shows the tab", vm.state.value.showGroupsTab)

        vm.update { it.copy(showGroupsTab = false) }
        advanceUntilIdle()
        assertFalse(vm.state.value.showGroupsTab)

        vm.update { it.copy(showGroupsTab = true) }
        advanceUntilIdle()
        assertTrue(vm.state.value.showGroupsTab)
        assertTrue(env.settingsRepo.get().showGroupsTab)
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
            vm.setEmailInvitesEnabled(false)
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
            vm.setEmailInvitesEnabled(true)
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
            vm.setEmailInvitesEnabled(false)
            advanceUntilIdle()

            // The preference is remembered for when sync is switched on, but there are no events to
            // invite anyone to — so the resync stays a no-op and needs no calendar permission.
            assertFalse(env.settingsRepo.get().emailInvitesEnabled)
            assertTrue(env.calendar.calls.isEmpty())
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
