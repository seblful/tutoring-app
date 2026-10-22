package com.seblful.klas.data.repository

import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.data.settings.SettingsDao
import com.seblful.klas.data.settings.SettingsEntity
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.format.AppLocale
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.format.toDevicePreferences
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    @get:Rule
    val locale = AppLocaleRule.russian()

    @get:Rule
    val database = TestDatabase()

    private val dao = database.db.settingsDao()
    private val repo = SettingsRepository(dao, RussianMinskPhone.toDevicePreferences())

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

    @Test
    fun `a phone with no row is captioned in its own currency`() = runTest {
        val american = SettingsRepository(dao, listOf(Locale.forLanguageTag("en-US")).toDevicePreferences())
        val german = SettingsRepository(dao, listOf(Locale.forLanguageTag("de-DE")).toDevicePreferences())
        val british = SettingsRepository(dao, listOf(Locale.forLanguageTag("en-GB")).toDevicePreferences())
        val japanese = SettingsRepository(dao, listOf(Locale.forLanguageTag("ja-JP")).toDevicePreferences())
        val unknown = SettingsRepository(dao, listOf(Locale.forLanguageTag("en-IS")).toDevicePreferences())

        assertEquals(Currency.USD, american.get().currency)
        assertEquals(Currency.EUR, german.get().currency)
        assertEquals(Currency.GBP, british.get().currency)
        assertEquals(Currency.JPY, japanese.get().currency)
        assertEquals("nothing the app offers, so the fallback", Currency.USD, unknown.get().currency)
    }

    @Test
    fun `the first write persists the currency the phone named`() = runTest {
        val repo = SettingsRepository(dao, listOf(Locale.forLanguageTag("de-DE")).toDevicePreferences())

        repo.update { it.copy(onboardingComplete = true) }

        assertEquals(Currency.EUR, dao.get()?.currency)
    }

    @Test
    fun `language state updates without changing the foreground locale`() = runTest {
        val display = DisplayLocale(repo, RussianMinskPhone.toDevicePreferences())
        AppLocale.current = Locale.ROOT
        repo.update { it.copy(language = AppLanguage.ENGLISH) }
        assertEquals(Locale.forLanguageTag("en"), display.current)

        repo.update { it.copy(language = AppLanguage.RUSSIAN) }
        assertEquals(Locale.forLanguageTag("ru"), display.current)

        repo.update { it.copy(language = AppLanguage.SYSTEM) }
        assertEquals(Locale.forLanguageTag("ru"), display.current)

        AppLocale.current = Locale.ROOT
        repo.observe().first()
        assertEquals(AppLanguage.SYSTEM, repo.language.value)
        assertEquals("persistence does not change presentation globals", Locale.ROOT, AppLocale.current)
    }

    @Test
    fun `what stays on screen reads the stored language, which a fresh process has not yet read`() = runTest {
        repo.update { it.copy(language = AppLanguage.ENGLISH) }
        val freshProcess = SettingsRepository(dao, RussianMinskPhone.toDevicePreferences())
        val display = DisplayLocale(freshProcess, RussianMinskPhone.toDevicePreferences())

        assertEquals("nothing has read the row yet", Locale.forLanguageTag("ru"), display.current)
        assertEquals(Locale.forLanguageTag("en"), display.observe().first())

        freshProcess.update { it.copy(language = AppLanguage.RUSSIAN) }
        assertEquals(Locale.forLanguageTag("ru"), display.observe().first())
    }

    @Test
    fun `system resolves against the phone the app is on`() = runTest {
        val american = SettingsRepository(dao, listOf(Locale.forLanguageTag("en-US")).toDevicePreferences())

        american.update { it.copy(language = AppLanguage.SYSTEM) }

        val display = DisplayLocale(american, listOf(Locale.US).toDevicePreferences())
        assertEquals(Locale.forLanguageTag("en"), display.current)
    }

    @Test
    fun `updating one setting leaves the others untouched`() = runTest {
        repo.update { it.copy(currency = Currency.RUB) }
        repo.update { it.copy(reminderLeadMinutes = 30) }

        val settings = repo.get()
        assertEquals(Currency.RUB, settings.currency)
        assertEquals(30, settings.reminderLeadMinutes)
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

    @Test
    fun `two concurrent updates do not lose one another`() = runTest {
        val dao = SuspendingSettingsDao()
        val repo = SettingsRepository(dao, RussianMinskPhone.toDevicePreferences())

        val first = launch { repo.update { it.copy(currency = Currency.RUB) } }
        val second = launch { repo.update { it.copy(reminderLeadMinutes = 30) } }
        first.join()
        second.join()

        val settings = repo.get()
        assertEquals(Currency.RUB, settings.currency)
        assertEquals(30, settings.reminderLeadMinutes)
    }

    @Test
    fun `an update is not lost by one that started while it was reading`() = runTest {
        val dao = SuspendingSettingsDao()
        val repo = SettingsRepository(dao, RussianMinskPhone.toDevicePreferences())

        val enabling = launch { repo.update { it.copy(calendarSyncEnabled = true) } }
        val theming = launch { repo.update { it.copy(themeMode = ThemeChoice.DARK) } }
        enabling.join()
        theming.join()

        assertTrue(repo.get().calendarSyncEnabled)
        assertEquals(ThemeChoice.DARK, repo.get().themeMode)
    }
}
