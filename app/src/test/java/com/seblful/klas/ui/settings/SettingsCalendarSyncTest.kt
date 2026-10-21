package com.seblful.klas.ui.settings

import com.seblful.klas.calendar.Guest
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsCalendarSyncTest : SettingsTestFixture() {

    @Test
    fun `turning email invites off withdraws them from the classes already synced`() =
        runTest(mainRule.dispatcher) {
            val sid = env.fixtures.student(name = "Аня", email = "anya@example.com")
            val subj = env.fixtures.subject(name = "Математика")
            env.fixtures.storedClass(
                studentId = sid,
                subjectId = subj,
                startMillis = env.clock.now().toEpochMilliseconds() + 86_400_000,
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
            val sid = env.fixtures.student(name = "Аня", email = "anya@example.com")
            val subj = env.fixtures.subject(name = "Математика")
            env.fixtures.storedClass(
                studentId = sid,
                subjectId = subj,
                startMillis = env.clock.now().toEpochMilliseconds() + 86_400_000,
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
            val sid = env.fixtures.student(name = "Аня", email = "anya@example.com")
            val subj = env.fixtures.subject(name = "Математика")
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
            env.fixtures.lesson(subj, listOf(sid), start = Instant.parse("2026-06-02T09:00:00Z"))
            env.calendar.calls.clear()
            val refreshesBefore = env.reminders.refreshCount

            val vm = vm()
            vm.update { it.copy(reminderLeadMinutes = 24 * 60) }
            advanceUntilIdle()

            assertEquals("the event is re-mirrored under the new lead", 1, env.calendar.upserts.size)
            assertEquals("and the alarm plan is re-derived", refreshesBefore + 1, env.reminders.refreshCount)
        }

    @Test
    fun `enabling calendar sync backfills events for existing classes`() = runTest(mainRule.dispatcher) {
        val sid = env.fixtures.student(name = "Аня")
        val subj = env.fixtures.subject(name = "Математика")
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
        val sid = env.fixtures.student(name = "Аня")
        val subj = env.fixtures.subject(name = "Математика")
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
        observing(vm.state)
        advanceUntilIdle()
        assertEquals(CalendarDelivery.ON, vm.state.current.calendar)

        env.calendarPermission.granted = false
        vm.refreshPermissions()
        advanceUntilIdle()

        assertEquals(CalendarDelivery.BLOCKED, vm.state.current.calendar)
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
            val sid = env.fixtures.student(name = "Аня")
            val subj = env.fixtures.subject(name = "Математика")
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
        val sid = env.fixtures.student(name = "Аня")
        val subj = env.fixtures.subject(name = "Математика")
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
}
