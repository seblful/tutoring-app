package com.seblful.klas.domain

import com.seblful.klas.domain.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What «follow the phone» means, and what a stored word reads back as.
 *
 * The interesting half is [AppLanguage.localeFor]: it is the *one* place the app resolves SYSTEM, and it has
 * to be, because the strings and the date formatters both take the answer. Two resolutions that disagreed
 * would be an app whose words and months were in different languages, which is the defect the whole
 * arrangement exists to make unrepresentable.
 */
class AppLanguageTest {

    private fun locales(vararg tags: String) = tags.toList()

    @Test
    fun `system follows the phone when the phone asks for a language the app has`() {
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.of(locales("ru-BY")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(locales("en-US")))
    }

    /**
     * Matched on the **language**, not the locale: a region is not a language, and there is no
     * `values-ru-rBY/`. A Minsk phone falling through to English for want of one would be the app's own
     * audience getting the fallback.
     */
    @Test
    fun `a region does not change the language`() {
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.of(locales("ru-RU")))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.of(locales("ru-BY")))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.of(locales("ru")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(locales("en-GB")))
        assertEquals("a tag's case is not its language", AppLanguage.RUSSIAN, AppLanguage.of(locales("RU-by")))
    }

    /**
     * The fallback has to be English because `values/` is: Android loads the default resource folder for an
     * unmatched locale, so any other answer here would put the strings and the dates in different languages
     * on the same phone.
     */
    @Test
    fun `a phone speaking neither language falls back to English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(locales("fr-FR")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(locales("pl-PL", "de-DE")))
        assertEquals("nothing to ask at all", AppLanguage.ENGLISH, AppLanguage.of(emptyList()))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.FALLBACK)
    }

    /** Most-wanted first: a phone listing several is answered by the first one the app can serve. */
    @Test
    fun `the phone's order decides which of several is used`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(locales("en-US", "ru-RU")))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.of(locales("ru-RU", "en-US")))
        assertEquals(
            "a language the app lacks is skipped rather than answered",
            AppLanguage.RUSSIAN,
            AppLanguage.of(locales("be-BY", "ru-BY", "en-US")),
        )
    }

    @Test
    fun `a chosen language answers with itself, whatever the phone says`() {
        assertEquals("ru", AppLanguage.RUSSIAN.tagFor(locales("en-US")))
        assertEquals("en", AppLanguage.ENGLISH.tagFor(locales("ru-BY")))
    }

    @Test
    fun `system resolves through the phone and never to nothing`() {
        assertEquals("ru", AppLanguage.SYSTEM.tagFor(locales("ru-BY")))
        assertEquals("en", AppLanguage.SYSTEM.tagFor(locales("fr-FR")))
        assertEquals("en", AppLanguage.SYSTEM.tagFor(emptyList()))
    }

    /** SYSTEM is a rule for picking a language, not a language — so it has no locale of its own. */
    @Test
    fun `only the translations name a locale`() {
        assertNull(AppLanguage.SYSTEM.tag)
        assertEquals(listOf(AppLanguage.RUSSIAN, AppLanguage.ENGLISH), AppLanguage.translated)
    }

    /**
     * Total, like every parse of a stored word: this runs inside a settings flow, where a throw reports
     * nothing. An unreadable value costs the tutor a preference for one launch and no money — see
     * `StoredEnumNamesTest`, which is the list that says renaming one is a migration.
     */
    @Test
    fun `an unset or unrecognised language follows the system`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.parse("SYSTEM"))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.parse("RUSSIAN"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.parse("ENGLISH"))
        assertEquals("nothing loaded yet", AppLanguage.SYSTEM, AppLanguage.parse(null))
        assertEquals("a value this build does not know", AppLanguage.SYSTEM, AppLanguage.parse("BELARUSIAN"))
        assertEquals("a tag is not a name", AppLanguage.SYSTEM, AppLanguage.parse("ru"))
    }
}
