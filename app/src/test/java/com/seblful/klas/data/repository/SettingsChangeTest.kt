package com.seblful.klas.data.repository

import com.seblful.klas.data.repository.SettingsChange.CalendarWork
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.ThemeChoice
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

    /**
     * A mirrored event's **location** is the app's own word for «Очно» or "Remote", written at mirror time.
     * So a language change leaves every event already in the tutor's calendar in the language it was written
     * in, indefinitely — the same defect the reminder lead had, and the reason this class exists.
     */
    @Test
    fun `changing the language while synced owes a resync`() {
        val synced = base.copy(calendarSyncEnabled = true)
        val c = change(before = synced, after = synced.copy(language = AppLanguage.ENGLISH))
        assertEquals("a mirrored event's location is written in the app's words", CalendarWork.RESYNC, c.calendarWork)
        assertFalse("the alarms carry no words of their own", c.replansReminders)
    }

    @Test
    fun `changing the language while not synced owes the calendar nothing`() {
        // Every other surface builds its words at the moment it shows them; only the mirror stores them.
        val c = change(after = base.copy(language = AppLanguage.ENGLISH))
        assertEquals(CalendarWork.NONE, c.calendarWork)
    }

    @Test
    fun `a change that moves nothing the world can see owes nothing`() {
        val c = change(
            after = base.copy(themeMode = ThemeChoice.DARK, currency = Currency.RUB, defaultLessonMinutes = 45),
        )
        assertEquals(CalendarWork.NONE, c.calendarWork)
        assertFalse(c.replansReminders)
    }

    /**
     * The currency is the near-miss worth pinning: it is printed into *notifications* and into a report, but
     * never stored in anything the app has to go back and rewrite — a calendar event's title and location
     * carry no money. So unlike the language beside it, it owes nothing.
     */
    @Test
    fun `changing the currency owes the calendar nothing, even while synced`() {
        val synced = base.copy(calendarSyncEnabled = true)
        val c = change(before = synced, after = synced.copy(currency = Currency.USD))
        assertEquals(CalendarWork.NONE, c.calendarWork)
    }
}
