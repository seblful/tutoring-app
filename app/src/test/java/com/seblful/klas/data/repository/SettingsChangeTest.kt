package com.seblful.klas.data.repository

import com.seblful.klas.data.repository.SettingsChange.CalendarWork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which settings owe the world something when they move — the classification `update` hands back.
 * Pure, so each obligation is one assertion rather than a fake calendar to inspect; the discharge
 * itself is covered in `SettingsViewModelTest`.
 */
class SettingsChangeTest {

    private val base = AppSettings()

    private fun change(before: AppSettings = base, after: AppSettings) = SettingsChange(before, after)

    @Test
    fun `switching sync on owes a backfill`() {
        val c = change(after = base.copy(calendarSyncEnabled = true))
        assertEquals(CalendarWork.RESYNC, c.calendarWork)
    }

    @Test
    fun `switching sync off owes a withdrawal`() {
        val c = change(
            before = base.copy(calendarSyncEnabled = true),
            after = base.copy(calendarSyncEnabled = false),
        )
        assertEquals(CalendarWork.WITHDRAW, c.calendarWork)
    }

    @Test
    fun `moving the invite switch while synced owes a resync`() {
        val synced = base.copy(calendarSyncEnabled = true)
        val c = change(before = synced, after = synced.copy(emailInvitesEnabled = true))
        assertEquals(CalendarWork.RESYNC, c.calendarWork)
    }

    @Test
    fun `moving the invite switch while not synced owes the calendar nothing`() {
        // The preference is remembered; there are no events to re-earn until sync is on.
        val c = change(after = base.copy(emailInvitesEnabled = true))
        assertEquals(CalendarWork.NONE, c.calendarWork)
    }

    @Test
    fun `moving the reminder lead owes a resync and a re-plan`() {
        val synced = base.copy(calendarSyncEnabled = true)
        val c = change(before = synced, after = synced.copy(reminderLeadMinutes = 24 * 60))
        assertEquals("the lead is baked into every mirrored event", CalendarWork.RESYNC, c.calendarWork)
        assertTrue("and into every armed alarm", c.replansReminders)
    }

    @Test
    fun `the lead re-plans the alarms even while the calendar is off`() {
        // The alarms are the app's own; the calendar is only a mirror of them.
        val c = change(after = base.copy(reminderLeadMinutes = 30))
        assertEquals(CalendarWork.NONE, c.calendarWork)
        assertTrue(c.replansReminders)
    }

    @Test
    fun `a change that moves nothing the world can see owes nothing`() {
        val c = change(
            after = base.copy(themeMode = "DARK", currency = com.seblful.klas.domain.model.Currency.RUB, defaultLessonMinutes = 45),
        )
        assertEquals(CalendarWork.NONE, c.calendarWork)
        assertFalse(c.replansReminders)
    }
}
