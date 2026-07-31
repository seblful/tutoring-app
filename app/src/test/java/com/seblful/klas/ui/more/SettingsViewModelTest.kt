package com.seblful.klas.ui.more

import com.seblful.klas.calendar.Guest
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.Loadable
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()

    private fun vm() = SettingsViewModel(
        env.settingsRepo, env.calendarMirror, env.reminders, env.maintenance,
        env.calendarPermission, env.notificationPermission, env.exactAlarmPermission,
    )

    /**
     * **A settings row is never drawn from a default**, because `AppSettings()`'s defaults are a different
     * tutor's settings: reminders on, sync off, BYN, hour-long lessons. The state used to start there, so
     * a tutor who had switched reminders off opened the screen on a switch that was on and then watched it
     * flip — which reads as the app doing something rather than as the app catching up.
     *
     * Both halves are asserted, as `LoadableTest` argues: `Loading` alone could be a state nothing leaves.
     */
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
                NotificationsIntent.Request(listOf("POST_NOTIFICATIONS")),
                vm.setNotificationsEnabled(on = true),
            )

            // Notifications off for the whole app: there is no permission to request, so asking would
            // show a dialog that can never be granted. Say so instead.
            env.notificationPermission.required = emptyList()
            assertEquals(NotificationsIntent.BlockedBySystem, vm.setNotificationsEnabled(on = true))
        }

    /**
     * **The setting is stored by the same call that asks.**
     *
     * This is the assertion that could not be written before: storing and asking were two calls the screen
     * made in sequence over one argument, so a test could exercise either and never the pair. Both halves
     * failing silently is what made it worth binding — asking without storing requests a permission for a
     * setting nobody saved, and storing without asking leaves reminders reading «включено» with no
     * permission and no dialog, which is the very state `reminderDelivery` exists to report.
     */
    @Test
    fun `asking for the permission and storing the setting are one act`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)
        observing(vm.reminderDelivery)
        env.notificationPermission.granted = false
        advanceUntilIdle()

        val intent = vm.setNotificationsEnabled(on = true)
        advanceUntilIdle()

        assertEquals(NotificationsIntent.Request(listOf("POST_NOTIFICATIONS")), intent)
        assertTrue("and the setting it asked about was stored", vm.state.current.notificationsEnabled)
        assertEquals(
            "so the row reports the state the app is actually in",
            ReminderDelivery.BLOCKED,
            vm.reminderDelivery.value,
        )

        // And off again: no request, and the setting follows.
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
        assertNull(vm.state.current.defaultGroupPrice)

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
            env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED")
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
            env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED")
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
            env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED")

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
        env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED")

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
        env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED")
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
            env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED", calendarEventId = "evt-9")
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
        env.fixtures.storedClass(studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60, status = "SCHEDULED", calendarEventId = "evt-1")
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }

        val vm = vm()
        vm.setCalendarSyncEnabled(false)
        advanceUntilIdle()

        assertFalse(env.settingsRepo.get().calendarSyncEnabled)
        assertEquals(listOf("evt-1"), env.calendar.deletes)
        // The event id is cleared off the class.
        assertEquals(null, env.classDao.items.value.single().calendarEventId)
    }

    // --- Turning auto-completion on ----------------------------------------------------------------
    //
    // The switch used to be wired straight to the generic `update`, so it stored a flag and owed
    // nothing: the backlog sat there until an app restart, the half-hourly tick, or *any* lesson write
    // happened to run the upkeep pass. It read as a switch that did nothing, and then marking one old
    // lesson completed by hand completed every other old lesson too — that tap was simply what finally
    // ran the pass, and it credited the lot at once.
    //
    // It now runs the pass itself, having asked. The **background** pass is not represented here and asks
    // nobody: nothing can be asked from a worker, and by then the tutor has agreed to the arrangement.

    /** Lessons that finished before the environment's clock, so a pass at `now` is due to settle them. */
    private suspend fun finishedLessons(count: Int): List<Long> {
        val subjectId = env.fixtures.subject("Математика")
        val studentId = env.fixtures.pricedStudent(subjectId = subjectId, rate = Money.ofMajor(30))
        val ended = env.clock.instant().minusSeconds(30L * 24 * 3600)
        return (1..count).map { n ->
            env.fixtures.lesson(
                subjectId = subjectId,
                students = listOf(studentId),
                start = ended.plusSeconds(n * 2L * 3600),
            ).single()
        }
    }

    private suspend fun statuses(ids: List<Long>) = ids.map { env.classRepo.getById(it)?.status }

    /**
     * The switch asks before it bills anything, and **nothing is stored** until the tutor answers — so a
     * question left hanging cannot half-apply.
     *
     * Three lessons, deliberately: there was a size threshold here, and a backlog this small went through
     * without a word. The tutor cannot see the backlog before flipping the switch, so a threshold decides
     * on their behalf that some amount of their money may be recognized unasked.
     */
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

    /** And says what it did, rather than leaving the tutor to trust the number it asked with. */
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

    /** Zero is the one backlog worth not asking about: there is nothing to say and nothing to bill. */
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

    /**
     * **Declining leaves the setting off**, which is the only honest answer available: the sweep has no
     * lower bound, so with the setting on the next upkeep pass would settle that backlog anyway — a "no"
     * that merely postponed it would be a lie.
     */
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

    /** Asked again on the next attempt — a "no" refuses this sweep, it does not remember a preference. */
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

    /** One-directional: switching a preference off is not a reason to un-bill anything, or to bill it. */
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
        // Both, because the point is the two disagreeing — and the tutor's own answer is now a read that
        // has to have happened before it can be asserted about.
        observing(vm.state, vm.reminderDelivery)
        advanceUntilIdle()

        assertTrue("the tutor asked for reminders", vm.state.current.notificationsEnabled)
        assertEquals(
            "but the platform will not show them",
            ReminderDelivery.BLOCKED,
            vm.reminderDelivery.value,
        )
    }

    /**
     * The other half of the platform's answer, and the half that was missing: the reminder *will* be
     * shown, and not necessarily when it says. A fresh Android 14+ install is in exactly this state,
     * because `SCHEDULE_EXACT_ALARM` is not granted by default there — so the app's one time-critical
     * promise was quietly approximate on every new install, with the row underneath reporting it as fine.
     */
    @Test
    fun `a reminder that can only be inexact is reported as late, not as working`() =
        runTest(mainRule.dispatcher) {
            env.exactAlarmPermission.granted = false
            val vm = vm()
            observing(vm.reminderDelivery)
            advanceUntilIdle()

            assertEquals(ReminderDelivery.LATE, vm.reminderDelivery.value)
        }

    /**
     * Precedence, which is the whole reason this is a named answer rather than two booleans: a reminder
     * nothing can post has nothing to be late about, so the blocked state wins and the tutor is given one
     * problem to fix instead of two.
     */
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

        // What returning from the system dialog — or from system settings — amounts to.
        env.notificationPermission.granted = true
        vm.refreshPermissions()
        advanceUntilIdle()

        assertEquals(ReminderDelivery.ON_TIME, vm.reminderDelivery.value)
    }

    /** And revoked the same way: a permission can be taken away while the app is in the background. */
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

    /**
     * Switching reminders off says nothing about the platform. Both grants are missing here and the answer
     * is still [ReminderDelivery.OFF] — the row must not lecture a tutor about a permission for a feature
     * they have just turned off.
     */
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
