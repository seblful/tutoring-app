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

class EnumConvertersTest {

    private val converters = EnumConverters()

    private val unreadable = listOf("HYBRID", "MISSED", "completed", "in_person", "", " SYSTEM")

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

    @Test
    fun `an unreadable status fails the read, naming what it found`() {
        unreadable.forEach { stored ->
            val failure = runCatching { converters.toStatus(stored) }.exceptionOrNull()

            assertTrue("\"$stored\" fails rather than guessing: $failure", failure is IllegalStateException)
            assertTrue("and says what it read: ${failure?.message}", failure!!.message!!.contains("\"$stored\""))
        }
    }

    @Test
    fun `an unreadable attendance fails the read, naming what it found`() {
        unreadable.forEach { stored ->
            val failure = runCatching { converters.toAttended(stored) }.exceptionOrNull()

            assertTrue("\"$stored\" fails rather than guessing: $failure", failure is IllegalStateException)
            assertTrue("and says what it read: ${failure?.message}", failure!!.message!!.contains("\"$stored\""))
        }
    }
}
