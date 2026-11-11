package com.seblful.klas.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.Collator
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class CurrencyPickerTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    @Test
    fun `the dropdown shows currency names symbols and codes and selects a new currency`() {
        var selected by mutableStateOf(Currency.BYN)
        compose.setContent {
            KlasTheme { CurrencyPicker(selected, onSelect = { selected = it }) }
        }
        compose.onNode(
            hasText("Belarusian", substring = true) and
                hasText("— ${Currency.BYN.symbol} (BYN)", substring = true),
        ).assertExists()
        compose.onNodeWithText("Currency").performClick()
        compose.onNode(hasSetTextAction() and hasText("Search")).performTextInput("Japanese")
        compose.onNode(
            hasText("Japanese", substring = true) and hasText("— ¥ (JPY)", substring = true),
        ).performScrollTo().performClick()
        assertEquals(Currency.JPY, selected)
        compose.onNode(hasText("— ¥ (JPY)", substring = true)).assertExists()
    }

    @Test
    @Config(qualifiers = "ru")
    fun `the Russian picker shows the localized name and the same Belarusian ruble sign`() {
        compose.setContent {
            KlasTheme { CurrencyPicker(Currency.BYN, onSelect = {}) }
        }
        compose.onNode(
            hasText("Белорусский рубль", substring = true) and
                hasText("— ${Currency.BYN.symbol} (BYN)", substring = true),
        ).assertExists()
    }

    @Test
    fun `all currencies are ordered by localized names in each app language`() {
        for (locale in listOf(Locale.ENGLISH, Locale.forLanguageTag("ru"))) {
            val choices = currencyChoices(locale.toLanguageTag())
            val collator = Collator.getInstance(locale)
            val expected = Currency.entries.map { java.util.Currency.getInstance(it.name).getDisplayName(locale) }
                .sortedWith(collator)
            val names = choices.map { java.util.Currency.getInstance(it.name).getDisplayName(locale) }
            assertEquals(expected, names)
            assertEquals(Currency.entries.toSet(), choices.toSet())
        }
    }
}
