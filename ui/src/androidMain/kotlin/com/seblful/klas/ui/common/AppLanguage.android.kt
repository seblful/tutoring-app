package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.seblful.klas.format.inLanguage
import java.util.Locale

/** The context, resources and configuration in [tag], for the Android views and APIs that read them. */
@Composable
internal actual fun ProvidePlatformLanguage(tag: String, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val configuration = LocalConfiguration.current
    val localized = remember(base, tag, configuration) { base.inLanguage(Locale.forLanguageTag(tag)) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalResources provides localized.resources,
        @Suppress("DEPRECATION")
        LocalConfiguration provides localized.resources.configuration,
        content = content,
    )
}
