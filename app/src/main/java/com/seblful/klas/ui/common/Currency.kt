package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.format.money

/**
 * The [Currency] money is displayed in, chosen in Settings and provided at the app root. UI formats
 * money through [Money.text] so a currency change re-renders everywhere.
 */
val LocalCurrency = compositionLocalOf { Currency.BYN }

/**
 * Formats this amount in the currency currently provided by [LocalCurrency], with the app language's
 * decimal separator.
 */
@Composable
@ReadOnlyComposable
fun Money.text(): String = money(LocalCurrency.current)

/** The human name of [currency] — words only; the legal set is [Currency]'s. */
@Composable
@ReadOnlyComposable
fun currencyLabel(currency: Currency): String = stringResource(
    when (currency) {
        Currency.BYN -> R.string.currency_byn
        Currency.RUB -> R.string.currency_rub
        Currency.USD -> R.string.currency_usd
        Currency.EUR -> R.string.currency_eur
    },
)
