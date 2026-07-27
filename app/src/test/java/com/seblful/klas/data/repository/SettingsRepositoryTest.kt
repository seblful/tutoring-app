package com.seblful.klas.data.repository

import com.seblful.klas.data.local.SettingsDao
import com.seblful.klas.data.local.SettingsEntity
import com.seblful.klas.testutil.FakeSettingsDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
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
        )

        repo.update { wanted }

        // Every field survives AppSettings -> SettingsEntity -> AppSettings. A field forgotten in
        // either mapper shows up here rather than as a setting that silently resets.
        assertEquals(wanted, repo.get())
    }

    @Test
    fun `observe emits the stored settings`() = runTest {
        repo.update { it.copy(themeMode = "DARK", currency = "RUB") }

        val observed = repo.observe().first()

        assertEquals("DARK", observed.themeMode)
        assertEquals("RUB", observed.currency)
    }

    @Test
    fun `observe emits defaults while no row exists`() = runTest {
        assertEquals(AppSettings(), repo.observe().first())
    }

    // --- Two updates at once ----------------------------------------------------------------------

    /**
     * A settings DAO whose read suspends, as a real one does — Room's suspend queries hand off to its
     * own executor. The plain fake returns from `get()` without ever yielding, so it cannot express
     * the interleaving this is about.
     */
    private class SuspendingSettingsDao : SettingsDao {
        val row = MutableStateFlow<SettingsEntity?>(null)

        override fun observe(): Flow<SettingsEntity?> = row

        override suspend fun get(): SettingsEntity? {
            yield()
            return row.value
        }

        override suspend fun upsert(settings: SettingsEntity) {
            row.value = settings
        }
    }

    /**
     * Two settings changed at once must both survive. The row is written **whole**, so an unguarded
     * read-modify-write loses one: both coroutines read the same settings, both write their own copy,
     * and the second copy carries the first one's change reverted inside it.
     *
     * This is what the settings screen does — one `launch` per switch — so it took no more than
     * flipping two switches quickly, and the setting that reverted did so with nothing on screen to
     * say it had.
     */
    @Test
    fun `two concurrent updates do not lose one another`() = runTest {
        val dao = SuspendingSettingsDao()
        val repo = SettingsRepository(dao)

        // Interleaved at the read: both are in flight before either writes.
        val first = launch { repo.update { it.copy(currency = "RUB") } }
        val second = launch { repo.update { it.copy(reminderLeadMinutes = 30) } }
        first.join()
        second.join()

        val settings = repo.get()
        assertEquals("RUB", settings.currency)
        assertEquals(30, settings.reminderLeadMinutes)
    }

    /** The same, for the toggle that holds its update open across other work. */
    @Test
    fun `an update is not lost by one that started while it was reading`() = runTest {
        val dao = SuspendingSettingsDao()
        val repo = SettingsRepository(dao)

        val enabling = launch { repo.update { it.copy(calendarSyncEnabled = true) } }
        val theming = launch { repo.update { it.copy(themeMode = "DARK") } }
        enabling.join()
        theming.join()

        assertTrue(repo.get().calendarSyncEnabled)
        assertEquals("DARK", repo.get().themeMode)
    }
}
