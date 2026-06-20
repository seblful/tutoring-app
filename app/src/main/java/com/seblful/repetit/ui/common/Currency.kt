package com.seblful.repetit.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.seblful.repetit.domain.model.Money

/**
 * The currency code (e.g. "BYN", "RUB") money is displayed in, chosen in Settings and provided at
 * the app root. UI formats money through [Money.text] so a currency change re-renders everywhere.
 */
val LocalCurrency = compositionLocalOf { "BYN" }

/** Formats this amount in the currency currently provided by [LocalCurrency]. */
@Composable
@ReadOnlyComposable
fun Money.text(): String = format(LocalCurrency.current)
