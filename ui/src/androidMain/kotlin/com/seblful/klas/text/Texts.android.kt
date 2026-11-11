package com.seblful.klas.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

internal actual fun formatInteger(value: Long, tag: String): String =
    String.format(Locale.forLanguageTag(tag), "%d", value)

@Composable
@ReadOnlyComposable
internal actual fun platformTexts(): Texts = Texts.of(LocalConfiguration.current.locales[0].toLanguageTag())
