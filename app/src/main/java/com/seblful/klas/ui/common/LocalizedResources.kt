package com.seblful.klas.ui.common

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.LayoutDirection
import com.seblful.klas.format.inLanguage
import java.util.Locale

/** Draws [content] in [locale] — **the app's language, provided to a whole composition.** */
@Composable
fun ProvideLocalizedResources(locale: Locale, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val localized = remember(base, locale) { base.inLanguage(locale) }
    val direction = if (localized.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalResources provides localized.resources,
        LocalLayoutDirection provides direction,
        @Suppress("DEPRECATION")
        LocalConfiguration provides localized.resources.configuration,
        content = content,
    )
}
