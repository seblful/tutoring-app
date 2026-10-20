package com.seblful.klas.data.settings

import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.data.settings.SettingsChange
import com.seblful.klas.data.settings.SettingsChange.CalendarWork
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.ThemeChoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `moving the invite switch while synced owes the lessons ahead a resync`() {
        val synced = base.copy(calendarSyncEnabled = true)
        val c = change(before = synced, after = synced.copy(emailInvitesEnabled = true))
        assertEquals(CalendarWork.RESYNC_UPCOMING, c.calendarWork)
    }

    @Test
    fun `moving the invite switch while not synced owes the calendar nothing`() {
        val c = change(after = base.copy(emailInvitesEnabled = true))
        assertEquals(CalendarWork.NONE, c.calendarWork)
    }

    @Test
    fun `moving the reminder lead owes a resync and a re-plan`() {
        val synced = base.copy(calendarSyncEnabled = true)
        val c = change(before = synced, after = synced.copy(reminderLeadMinutes = 24 * 60))
        assertEquals(CalendarWork.RESYNC_UPCOMING, c.calendarWork, "the lead is baked into every event ahead")
        assertTrue(c.replansReminders, "and into every armed alarm")
    }

    @Test
    fun `the lead re-plans the alarms even while the calendar is off`() {
        val c = change(after = base.copy(reminderLeadMinutes = 30))
        assertEquals(CalendarWork.NONE, c.calendarWork)
        assertTrue(c.replansReminders)
    }

    @Test
    fun `changing the language while synced owes a resync`() {
        val synced = base.copy(calendarSyncEnabled = true)
        val c = change(before = synced, after = synced.copy(language = AppLanguage.ENGLISH))
        assertEquals(CalendarWork.RESYNC_UPCOMING, c.calendarWork, "an event's location follows app language")
        assertFalse(c.replansReminders, "the alarms carry no words of their own")
    }

    @Test
    fun `changing the language while not synced owes the calendar nothing`() {
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

    @Test
    fun `changing the currency owes the calendar nothing - even while synced`() {
        val synced = base.copy(calendarSyncEnabled = true)
        val c = change(before = synced, after = synced.copy(currency = Currency.USD))
        assertEquals(CalendarWork.NONE, c.calendarWork)
    }
}
