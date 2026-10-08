package com.seblful.klas.data.local

import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [EnumConverters]: every constant goes in as its name and comes back as itself, and text that is not a
 * name comes back as the answer its column can afford — a fallback where the column carries no money, a
 * named failure where it does. Which names are stored is `StoredEnumNamesTest`'s; this is how they travel.
 *
 * The converters run inside the `Flow`s every screen collects, so what they do with an unreadable column
 * decides whether that screen draws at all.
 */
class EnumConvertersTest {

    private val converters = EnumConverters()

    /** What a hand-edit, an older build or a careless migration could leave in a text column. */
    private val unreadable = listOf("HYBRID", "MISSED", "completed", "in_person", "", " SYSTEM")

    // --- Round trips -----------------------------------------------------------------------------

    @Test
    fun `every status round-trips through its name`() {
        LessonStatus.entries.forEach { status ->
            assertEquals(status.name, converters.fromStatus(status))
            assertEquals(status, converters.toStatus(converters.fromStatus(status)))
        }
    }

    @Test
    fun `every attendance round-trips through its name`() {
        Attended.entries.forEach { attended ->
            assertEquals(attended.name, converters.fromAttended(attended))
            assertEquals(attended, converters.toAttended(converters.fromAttended(attended)))
        }
    }

    @Test
    fun `every mode round-trips through its name`() {
        ClassMode.entries.forEach { mode ->
            assertEquals(mode.name, converters.fromMode(mode))
            assertEquals(mode, converters.toMode(converters.fromMode(mode)))
        }
    }

    @Test
    fun `every currency round-trips through its name`() {
        Currency.entries.forEach { currency ->
            assertEquals(currency.name, converters.fromCurrency(currency))
            assertEquals(currency, converters.toCurrency(converters.fromCurrency(currency)))
        }
    }

    @Test
    fun `every language round-trips through its name`() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language.name, converters.fromLanguage(language))
            assertEquals(language, converters.toLanguage(converters.fromLanguage(language)))
        }
    }

    @Test
    fun `every theme round-trips through its name`() {
        ThemeChoice.entries.forEach { theme ->
            assertEquals(theme.name, converters.fromTheme(theme))
            assertEquals(theme, converters.toTheme(converters.fromTheme(theme)))
        }
    }

    // --- Text that is not a name: no money, so a fallback ----------------------------------------

    /**
     * A mode is a label and a calendar location, so an unreadable one costs a wrong word rather than a
     * screen: the row is still read, which is what matters — the rest of the lesson is intact.
     */
    @Test
    fun `an unreadable mode falls back to in person rather than failing the read`() {
        unreadable.forEach { assertEquals("\"$it\"", ClassMode.IN_PERSON, converters.toMode(it)) }
    }

    @Test
    fun `an unreadable currency falls back to BYN`() {
        unreadable.forEach { assertEquals("\"$it\"", Currency.BYN, converters.toCurrency(it)) }
    }

    @Test
    fun `an unreadable language falls back to following the phone`() {
        unreadable.forEach { assertEquals("\"$it\"", AppLanguage.SYSTEM, converters.toLanguage(it)) }
    }

    @Test
    fun `an unreadable theme falls back to following the phone`() {
        unreadable.forEach { assertEquals("\"$it\"", ThemeChoice.SYSTEM, converters.toTheme(it)) }
    }

    // --- Text that is not a name: money, so a named failure --------------------------------------

    /**
     * A status is what makes a lesson count as income, so there is no fallback that doesn't move money:
     * `SCHEDULED` un-bills a lesson that earned, `COMPLETED` invents one that didn't. It fails instead,
     * saying what it read — and a backup naming such a status is refused as it is parsed, so this should
     * be unreachable from anything but a hand-edited database.
     */
    @Test
    fun `an unreadable status fails the read, naming what it found`() {
        unreadable.forEach { stored ->
            val failure = runCatching { converters.toStatus(stored) }.exceptionOrNull()

            assertTrue("\"$stored\" fails rather than guessing: $failure", failure is IllegalStateException)
            assertTrue("and says what it read: ${failure?.message}", failure!!.message!!.contains("\"$stored\""))
        }
    }

    /** And so does an attendance, for the same reason: it is the other half of what bills. */
    @Test
    fun `an unreadable attendance fails the read, naming what it found`() {
        unreadable.forEach { stored ->
            val failure = runCatching { converters.toAttended(stored) }.exceptionOrNull()

            assertTrue("\"$stored\" fails rather than guessing: $failure", failure is IllegalStateException)
            assertTrue("and says what it read: ${failure?.message}", failure!!.message!!.contains("\"$stored\""))
        }
    }
}
