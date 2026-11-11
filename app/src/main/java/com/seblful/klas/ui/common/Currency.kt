package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.format.money
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import java.text.Collator
import java.util.Locale

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
    val locale = LocalResources.current.configuration.locales[0]
    return stringResource(
        Strings.currency_choice_label, currencyName(currency, locale), currency.symbol, currency.name,
    )
}

/** The code where the platform has no name: older Androids predate VES, MRU, STN, SLE and ZWG. */
private fun currencyName(currency: Currency, locale: Locale): String =
    runCatching { java.util.Currency.getInstance(currency.name).getDisplayName(locale) }.getOrDefault(currency.name)
        .replaceFirstChar { it.titlecase(locale) }

/** Alphabetical in the current app language, rather than privileging one region. */
internal fun currencyChoices(locale: Locale): List<Currency> {
    val collator = Collator.getInstance(locale)
    return Currency.entries.sortedWith { first, second ->
        collator.compare(currencyName(first, locale), currencyName(second, locale))
    }
}

@Composable
fun CurrencyPicker(selected: Currency, onSelect: (Currency) -> Unit, description: String? = null) {
    val locale = LocalResources.current.configuration.locales[0]
    val choices = remember(locale) { currencyChoices(locale) }
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
