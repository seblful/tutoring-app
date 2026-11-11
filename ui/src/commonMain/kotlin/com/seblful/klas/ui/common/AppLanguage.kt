package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.seblful.klas.format.localeData
import com.seblful.klas.text.LocalTexts
import com.seblful.klas.text.Texts

/**
 * Draws [content] in the language [tag] — **the app's language, provided to a whole composition**: its
 * words, its reading direction and whatever the platform's own views read the language from.
 */
@Composable
fun ProvideAppLanguage(tag: String, content: @Composable () -> Unit) {
    val texts = remember(tag) { Texts.of(tag) }
    val direction = if (localeData(tag).rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr
    ProvidePlatformLanguage(tag) {
        CompositionLocalProvider(
            LocalTexts provides texts,
            LocalLayoutDirection provides direction,
            content = content,
        )
    }
}

/** The platform's half of [ProvideAppLanguage]: Android's resources and configuration; nothing on iOS. */
@Composable
internal expect fun ProvidePlatformLanguage(tag: String, content: @Composable () -> Unit)
