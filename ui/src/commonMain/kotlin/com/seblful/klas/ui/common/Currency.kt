package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.format.collatorFor
import com.seblful.klas.format.currencyDisplayName
import com.seblful.klas.format.money
import com.seblful.klas.format.titlecaseFirst
import com.seblful.klas.text.Strings
import com.seblful.klas.text.currentTexts
import com.seblful.klas.text.stringResource

/**
 * The [Currency] money is displayed in, chosen in Settings and provided at the app root. UI formats
 * money through [Money.text] so a currency change re-renders everywhere.
 */
val LocalCurrency = compositionLocalOf { Currency.LEGACY }

/**
 * Formats this amount in the currency currently provided by [LocalCurrency], with the app language's
 * decimal separator.
 */
@Composable
@ReadOnlyComposable
fun Money.text(): String = money(LocalCurrency.current)

/** Currency names follow the app's language; ISO codes distinguish dollars and other shared symbols. */
@Composable
@ReadOnlyComposable
fun currencyChoiceLabel(currency: Currency): String {
    val tag = currentTexts().tag
    return stringResource(
        Strings.currency_choice_label, currencyName(currency, tag), currency.symbol, currency.name,
    )
}

/** The code where the platform has no name for it. */
private fun currencyName(currency: Currency, tag: String): String =
    titlecaseFirst(currencyDisplayName(currency.name, tag) ?: currency.name, tag)

/** Alphabetical in the current app language, rather than privileging one region. */
fun currencyChoices(tag: String): List<Currency> {
    val collator = collatorFor(tag)
    return Currency.entries.sortedWith { first, second ->
        collator.compare(currencyName(first, tag), currencyName(second, tag))
    }
}

@Composable
fun CurrencyPicker(selected: Currency, onSelect: (Currency) -> Unit, description: String? = null) {
    val tag = currentTexts().tag
    val choices = remember(tag) { currencyChoices(tag) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.inline)) {
        ChoiceField(
            label = stringResource(Strings.settings_currency),
            selected = selected,
            options = choices,
            optionLabel = { currencyChoiceLabel(it) },
            onSelect = onSelect,
            supportingText = description,
        )
    }
}
