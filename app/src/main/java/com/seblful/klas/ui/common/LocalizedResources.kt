package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.seblful.klas.format.inLanguage
import java.util.Locale

/** raws [content] in [locale] — **the app's language, provided to a whole composition.** */
@Composable
fun ProvideLocalizedResources(locale: Locale, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val localized = remember(base, locale) { base.inLanguage(locale) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalResources provides localized.resources,
        @Suppress("DEPRECATION")
        LocalConfiguration provides localized.resources.configuration,
        content = content,
    )
}
