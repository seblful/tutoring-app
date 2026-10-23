package com.seblful.klas.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Colour by **meaning**: a feature asks for "a debt" or "a destructive action", never for `error`. Each role
 * names a scheme role rather than a hue of its own, so every role inherits that role's tested contrast in
 * all three contrast levels, both themes and under dynamic colour (`ContrastSchemesTest`).
 *
 * Debt, overdue, destructive, validation and cancelled share the error hue, so none may be the only signal:
 * each comes with its word or icon. Advisory is the tertiary container, so a nudge ("an old backup") never
 * reads as amber, which means "on now", or green, which means "taught". Blocking is the error container:
 * something does not work until the tutor acts.
 */
@Immutable
data class KlasSemantics(
    val debt: Color,
    val overdue: Color,
    val destructive: Color,
    val validation: Color,
    val cancelled: Color,
    /** An icon or word that wants attention without being an error — the tertiary tone. */
    val attention: Color,
    val blocking: Color,
    val onBlocking: Color,
    val advisory: Color,
    val onAdvisory: Color,
) {
    companion object {
        fun of(scheme: ColorScheme) = KlasSemantics(
            debt = scheme.error,
            overdue = scheme.error,
            destructive = scheme.error,
            validation = scheme.error,
            cancelled = scheme.error,
            attention = scheme.tertiary,
            blocking = scheme.errorContainer,
            onBlocking = scheme.onErrorContainer,
            advisory = scheme.tertiaryContainer,
            onAdvisory = scheme.onTertiaryContainer,
        )
    }
}

/** The meaning-based roles of the scheme in force — see [KlasSemantics]. */
val klasSemantics: KlasSemantics
    @Composable @ReadOnlyComposable
    get() = KlasSemantics.of(MaterialTheme.colorScheme)
