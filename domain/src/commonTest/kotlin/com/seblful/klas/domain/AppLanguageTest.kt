package com.seblful.klas.domain

import com.seblful.klas.domain.model.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppLanguageTest {

    private fun locales(vararg tags: String) = tags.toList()

    @Test
    fun `system follows the phone when the phone asks for a language the app has`() {
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.of(locales("ru-BY")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(locales("en-US")))
    }

    @Test
    fun `a region does not change the language`() {
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.of(locales("ru-RU")))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.of(locales("ru-BY")))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.of(locales("ru")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(locales("en-GB")))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.of(locales("RU-by")), "a tag's case is not its language")
    }

    @Test
    fun `a phone speaking neither language falls back to English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(locales("fr-FR")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(locales("pl-PL", "de-DE")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(emptyList()), "nothing to ask at all")
        assertEquals(AppLanguage.ENGLISH, AppLanguage.FALLBACK)
    }

    @Test
    fun `the phone's order decides which of several is used`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.of(locales("en-US", "ru-RU")))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.of(locales("ru-RU", "en-US")))
        assertEquals(
            AppLanguage.RUSSIAN,
            AppLanguage.of(locales("be-BY", "ru-BY", "en-US")),
            "a language the app lacks is skipped rather than answered",
        )
    }

    @Test
    fun `a chosen language answers with itself - whatever the phone says`() {
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
        assertEquals(AppLanguage.SYSTEM, AppLanguage.parse(null), "nothing loaded yet")
        assertEquals(AppLanguage.SYSTEM, AppLanguage.parse("BELARUSIAN"), "a value this build does not know")
        assertEquals(AppLanguage.SYSTEM, AppLanguage.parse("ru"), "a tag is not a name")
    }
}
