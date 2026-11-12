package com.seblful.klas.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.R
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnvRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppLanguagePreferencesTest {
    @get:Rule(order = 0)
    val env = TestEnvRule()
    @get:Rule(order = 1)
    val main = MainDispatcherRule()

    private class Platform(override var language: AppLanguage? = AppLanguage.SYSTEM) : AppLanguagePlatform {
        override var migrated = false
        override fun apply(language: AppLanguage) { if (this.language != null) this.language = language }
    }

    private fun preferences(platform: Platform) =
        AppLanguagePreferences(env.settingsRepo, env.settingsWorkflow, platform, main.appScope)

    @Test
    fun `the first launch preserves an existing in-app language choice`() = runTest(main.dispatcher) {
        env.settingsRepo.update { it.copy(language = AppLanguage.ARABIC) }
        val platform = Platform()
        preferences(platform).refresh()
        assertEquals(AppLanguage.ARABIC, platform.language)
        assertEquals(AppLanguage.ARABIC, env.settingsRepo.get().language)
        assertTrue(platform.migrated)
    }

    @Test
    fun `an existing system choice takes priority during migration`() = runTest(main.dispatcher) {
        env.settingsRepo.update { it.copy(language = AppLanguage.RUSSIAN) }
        val platform = Platform(AppLanguage.FRENCH)
        preferences(platform).refresh()
        assertEquals(AppLanguage.FRENCH, env.settingsRepo.get().language)
    }

    @Test
    fun `returning from system settings imports its language and follow-system choice`() = runTest(main.dispatcher) {
        val platform = Platform().apply { migrated = true }
        val preferences = preferences(platform)
        preferences.refresh()
        platform.language = AppLanguage.SPANISH
        preferences.refresh()
        assertEquals(AppLanguage.SPANISH, env.settingsRepo.get().language)
        platform.language = AppLanguage.SYSTEM
        preferences.refresh()
        assertEquals(AppLanguage.SYSTEM, env.settingsRepo.get().language)
    }

    @Test
    fun `a language chosen in system settings while synced rewrites the upcoming events in it`() =
        runTest(main.dispatcher) {
            val subject = env.fixtures.subject("Математика")
            val student = env.fixtures.student("Аня")
            env.fixtures.withCalendarSync()
            env.fixtures.lesson(subject, listOf(student), start = Instant.parse("2026-06-02T09:00:00Z"))
            val platform = Platform().apply { migrated = true }
            val preferences = preferences(platform)
            preferences.refresh()
            env.calendar.calls.clear()

            platform.language = AppLanguage.SPANISH
            preferences.refresh()

            assertEquals("the event is re-mirrored in the new language", 1, env.calendar.upserts.size)
        }

    @Test
    fun `an in-app change is published without a resume overwriting the pending choice`() = runTest(main.dispatcher) {
        val platform = Platform().apply { migrated = true }
        val preferences = preferences(platform)
        preferences.start()
        advanceUntilIdle()
        env.settingsRepo.update { it.copy(language = AppLanguage.GERMAN) }
        preferences.refresh()
        advanceUntilIdle()
        assertEquals(AppLanguage.GERMAN, platform.language)
        assertEquals(AppLanguage.GERMAN, env.settingsRepo.get().language)
    }

    @Test
    fun `older Android versions retain the custom language setting`() = runTest(main.dispatcher) {
        env.settingsRepo.update { it.copy(language = AppLanguage.RUSSIAN) }
        val platform = Platform(null)
        preferences(platform).refresh()
        assertEquals(AppLanguage.RUSSIAN, env.settingsRepo.get().language)
    }

    @Test
    @Config(sdk = [32])
    fun `the Android adapter does not call unsupported locale APIs`() {
        val platform = AndroidAppLanguagePlatform(ApplicationProvider.getApplicationContext())
        platform.apply(AppLanguage.ARABIC)
        assertNull(platform.language)
    }

    @Test
    fun `Android language settings offer every translated language`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tags = mutableSetOf<String>()
        context.resources.getXml(R.xml.locale_config).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    tags += parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                }
            }
        }
        assertEquals(AppLanguage.translated.mapNotNull { it.tag }.toSet(), tags)
    }
}
