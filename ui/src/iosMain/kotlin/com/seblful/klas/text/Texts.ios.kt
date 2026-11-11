package com.seblful.klas.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.preferredLanguages

internal actual fun formatInteger(value: Long, tag: String): String {
    val formatter = NSNumberFormatter()
    formatter.locale = NSLocale(localeIdentifier = tag)
    formatter.numberStyle = NSNumberFormatterDecimalStyle
    formatter.usesGroupingSeparator = false
    formatter.maximumFractionDigits = 0u
    return formatter.stringFromNumber(NSNumber(longLong = value)) ?: value.toString()
}

@Composable
@ReadOnlyComposable
internal actual fun platformTexts(): Texts =
    Texts.of(NSLocale.preferredLanguages.firstOrNull() as? String ?: DEFAULT_TEXT_TAG)
