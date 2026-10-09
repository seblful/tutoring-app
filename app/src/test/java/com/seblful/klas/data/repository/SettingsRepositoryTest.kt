package com.seblful.klas.data.repository

import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.data.local.SettingsDao
import com.seblful.klas.data.local.SettingsEntity
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.format.AppLocale
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.testutil.RussianMinskPhone
import com.seblful.klas.testutil.TestDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.Locale

/**
 * The settings row. Previously untested — its eleven setters were each too small to look worth it,
 * which left the two things that actually matter unasserted: that a missing row reads as defaults,
 * and that changing one setting doesn't disturb the others.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    /**
     * Reading the row **writes** `AppLocale.current`, which is process-wide — so this class is one of the
     * ones that has to put it back, or which language the next test class formats a date in would depend on
     * the order JUnit happened to run them in.
     */
    @get:Rule
    val locale = AppLocaleRule.russian()

    @get:Rule
    val database = TestDatabase()

    private val dao = database.db.settingsDao()
    private val repo = SettingsRepository(dao, RussianMinskPhone)

    @Test
    fun `a missing row reads as the defaults`() = runTest {
        val settings = repo.get()

        assertEquals(AppSettings(), settings)
        assertTrue("notifications default on", settings.notificationsEnabled)
        assertFalse("calendar sync default off", settings.calendarSyncEnabled)
        assertFalse("email invites default off — mailing students is a deliberate opt-in", settings.emailInvitesEnabled)
        assertFalse("auto-complete default off — never bill unconfirmed", settings.autoCompleteEnabled)
        assertNull(settings.defaultGroupPrice) // not set until the tutor sets one
        assertEquals("this phone is in Belarus", Currency.BYN, settings.currency)
        assertEquals("follow the phone until the tutor says otherwise", AppLanguage.SYSTEM, settings.language)
    }

    /**
     * **A row that does not exist is a tutor who has chosen nothing**, so the currency they see is the one
     * their phone names rather than the first currency the app ever had.
     *
     * This is where "default as phone" is actually implemented, and it is here rather than in first-run setup
     * because it has to hold on every path: a tutor who skips the setup entirely, or flips any switch before
     * reaching it, still gets their own currency — the first write persists whatever this substituted. There
     * is nothing to substitute for the language, `AppLanguage.SYSTEM` being a live rule rather than a value.
     */
    @Test
    fun `a phone with no row is captioned in its own currency`() = runTest {
        // One empty table for all three: reading a missing row writes nothing.
        val american = SettingsRepository(dao, listOf(Locale.forLanguageTag("en-US")))
        val german = SettingsRepository(dao, listOf(Locale.forLanguageTag("de-DE")))
        val unknown = SettingsRepository(dao, listOf(Locale.forLanguageTag("en-GB")))

        assertEquals(Currency.USD, american.get().currency)
        assertEquals(Currency.EUR, german.get().currency)
        assertEquals("nothing the app offers, so the fallback", Currency.USD, unknown.get().currency)
    }

    /** And the very first write is what makes that stick, rather than it being re-derived every launch. */
    @Test
    fun `the first write persists the currency the phone named`() = runTest {
        val repo = SettingsRepository(dao, listOf(Locale.forLanguageTag("de-DE")))

        // Anything at all — here the flag that ends first run, which is what «Пропустить» writes.
        repo.update { it.copy(onboardingComplete = true) }

        assertEquals(Currency.EUR, dao.get()?.currency)
    }

    /**
     * **Reading the row publishes the app's display locale**, which is what keeps the words and the dates in
     * the same language: every consumer — the UI, the workers, the notifier — arrives through here, so
     * `AppLocale.current` is already the new one before the new `AppSettings` reaches any of them.
     *
     * Asserted on both read paths, because a worker uses [SettingsRepository.get] and never observes.
     */
    @Test
    fun `reading the row publishes the locale everything formats in`() = runTest {
        repo.update { it.copy(language = AppLanguage.ENGLISH) }
        assertEquals(Locale.forLanguageTag("en"), AppLocale.current)

        repo.update { it.copy(language = AppLanguage.RUSSIAN) }
        assertEquals(Locale.forLanguageTag("ru"), AppLocale.current)

        // «Follow the phone» resolves against the device, not to nothing: this fixture's phone is Russian.
        repo.update { it.copy(language = AppLanguage.SYSTEM) }
        assertEquals(Locale.forLanguageTag("ru"), AppLocale.current)

        AppLocale.current = Locale.ROOT
        repo.observe().first()
        assertEquals("observing publishes it too", Locale.forLanguageTag("ru"), AppLocale.current)
    }

    /** SYSTEM is a rule and not a value, so it answers with *this* phone rather than with a constant. */
    @Test
    fun `system resolves against the phone the app is on`() = runTest {
        val american = SettingsRepository(dao, listOf(Locale.forLanguageTag("en-US")))

        american.update { it.copy(language = AppLanguage.SYSTEM) }

        assertEquals(Locale.forLanguageTag("en"), AppLocale.current)
    }

    @Test
    fun `updating one setting leaves the others untouched`() = runTest {
        repo.update { it.copy(currency = Currency.RUB) }
        repo.update { it.copy(reminderLeadMinutes = 30) }

        val settings = repo.get()
        assertEquals(Currency.RUB, settings.currency)
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
            themeMode = ThemeChoice.DARK,
            dynamicColorEnabled = true,
            defaultLessonMinutes = 45,
            defaultGroupPrice = Money(2_500),
            currency = Currency.RUB,
            language = AppLanguage.ENGLISH,
            lastExportAt = Instant.parse("2026-06-20T18:30:00Z"),
        )

        repo.update { wanted }

        // Every field survives AppSettings -> SettingsEntity -> AppSettings. A field forgotten in
        // either mapper shows up here rather than as a setting that silently resets.
        //
        // Every field has to be given a **non-default** value for that to be true, which is the part
        // that is easy to lose: `lastExportAt` was added to the class and not to this list, so for a
        // while the assertion compared null to null and would have passed on a mapper that dropped it
        // outright. If a new setting is added below, it belongs here too.
        assertEquals(wanted, repo.get())
    }

    @Test
    fun `observe emits the stored settings`() = runTest {
        repo.update { it.copy(themeMode = ThemeChoice.DARK, currency = Currency.RUB) }

        val observed = repo.observe().first()

        assertEquals(ThemeChoice.DARK, observed.themeMode)
        assertEquals(Currency.RUB, observed.currency)
    }

    @Test
    fun `observe emits defaults while no row exists`() = runTest {
        assertEquals(AppSettings(), repo.observe().first())
    }

    // --- Two updates at once ----------------------------------------------------------------------

    /**
     * A settings DAO whose read suspends, as Room's does on a device — its suspend queries hand off to
     * its own executor. The test database answers inline on the asking thread (see [TestDatabase]), so
     * its `get()` never yields and cannot express the interleaving this is about.
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
        val repo = SettingsRepository(dao, RussianMinskPhone)

        // Interleaved at the read: both are in flight before either writes.
        val first = launch { repo.update { it.copy(currency = Currency.RUB) } }
        val second = launch { repo.update { it.copy(reminderLeadMinutes = 30) } }
        first.join()
        second.join()

        val settings = repo.get()
        assertEquals(Currency.RUB, settings.currency)
        assertEquals(30, settings.reminderLeadMinutes)
    }

    /** The same, for the toggle that holds its update open across other work. */
    @Test
    fun `an update is not lost by one that started while it was reading`() = runTest {
        val dao = SuspendingSettingsDao()
        val repo = SettingsRepository(dao, RussianMinskPhone)

        val enabling = launch { repo.update { it.copy(calendarSyncEnabled = true) } }
        val theming = launch { repo.update { it.copy(themeMode = ThemeChoice.DARK) } }
        enabling.join()
        theming.join()

        assertTrue(repo.get().calendarSyncEnabled)
        assertEquals(ThemeChoice.DARK, repo.get().themeMode)
    }
}
