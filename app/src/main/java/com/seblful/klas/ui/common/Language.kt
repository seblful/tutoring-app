package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.model.AppLanguage

/**
 * The human name of [language] — words only; the legal set is [AppLanguage]'s.
 *
 * Each translation is named **in itself** in both string files («Русский» stays «Русский» in the English
 * set, "English" stays "English" in the Russian one), which is what makes the control usable to somebody
 * who has just landed in the wrong language: a row reading «Английский» is no help to a reader who cannot
 * read the word next to it. Only «follow the system» is translated, having no language of its own to be
 * named in.
 *
 * The counterpart of [currencyLabel], and beside it for the same reason: both are a fixed set the app
 * stores and two screens word — Settings and first-run setup.
 */
@Composable
@ReadOnlyComposable
fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.RUSSIAN -> R.string.language_russian
        AppLanguage.ENGLISH -> R.string.language_english
    },
)
