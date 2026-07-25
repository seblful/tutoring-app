package com.seblful.repetit.data.repository

import com.seblful.repetit.testutil.FakeSettingsDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings row. Previously untested — its eleven setters were each too small to look worth it,
 * which left the two things that actually matter unasserted: that a missing row reads as defaults,
 * and that changing one setting doesn't disturb the others.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    private val dao = FakeSettingsDao()
    private val repo = SettingsRepository(dao)

    @Test
    fun `a missing row reads as the defaults`() = runTest {
        val settings = repo.get()

        assertEquals(AppSettings(), settings)
        assertTrue("notifications default on", settings.notificationsEnabled)
        assertFalse("calendar sync default off", settings.calendarSyncEnabled)
        assertFalse("email invites default off — mailing students is a deliberate opt-in", settings.emailInvitesEnabled)
        assertFalse("auto-complete default off — never bill unconfirmed", settings.autoCompleteEnabled)
        assertTrue("the groups tab is on until turned off", settings.showGroupsTab)
        assertEquals(0L, settings.defaultGroupPriceMinor) // 0 means "not set"
        assertEquals("BYN", settings.currency)
    }

    @Test
    fun `updating one setting leaves the others untouched`() = runTest {
        repo.update { it.copy(currency = "RUB") }
        repo.update { it.copy(reminderLeadMinutes = 30) }

        val settings = repo.get()
        assertEquals("RUB", settings.currency)
        assertEquals(30, settings.reminderLeadMinutes)
        // Everything else still reads as it did.
        assertEquals(AppSettings().notificationsEnabled, settings.notificationsEnabled)
        assertEquals(AppSettings().defaultLessonMinutes, settings.defaultLessonMinutes)
        assertEquals(AppSettings().themeMode, settings.themeMode)
        assertEquals(AppSettings().showGroupsTab, settings.showGroupsTab)
    }

    @Test
    fun `an update round-trips through the entity without losing a field`() = runTest {
        val wanted = AppSettings(
            notificationsEnabled = false,
            calendarSyncEnabled = true,
            emailInvitesEnabled = true,
            onboardingComplete = true,
            reminderLeadMinutes = 15,
            autoCompleteEnabled = true,
            themeMode = "DARK",
            defaultLessonMinutes = 45,
            defaultGroupPriceMinor = 2_500,
            currency = "RUB",
            showGroupsTab = false,
        )

        repo.update { wanted }

        // Every field survives AppSettings -> SettingsEntity -> AppSettings. A field forgotten in
        // either mapper shows up here rather than as a setting that silently resets.
        assertEquals(wanted, repo.get())
    }

    @Test
    fun `observe emits the stored settings`() = runTest {
        repo.update { it.copy(themeMode = "DARK", showGroupsTab = false) }

        val observed = repo.observe().first()

        assertEquals("DARK", observed.themeMode)
        assertFalse(observed.showGroupsTab)
    }

    @Test
    fun `observe emits defaults while no row exists`() = runTest {
        assertEquals(AppSettings(), repo.observe().first())
    }
}
