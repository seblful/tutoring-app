package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.model.AppLanguage

/** Native names stay readable when the tutor needs to leave an unfamiliar app language. */
private val autonyms = mapOf(
    AppLanguage.RUSSIAN to "Русский",
    AppLanguage.ENGLISH to "English",
    AppLanguage.UKRAINIAN to "Українська",
    AppLanguage.POLISH to "Polski",
    AppLanguage.GERMAN to "Deutsch",
    AppLanguage.FRENCH to "Français",
    AppLanguage.SPANISH to "Español",
    AppLanguage.PORTUGUESE_BRAZIL to "Português (Brasil)",
    AppLanguage.TURKISH to "Türkçe",
    AppLanguage.ITALIAN to "Italiano",
    AppLanguage.BELARUSIAN to "Беларуская",
    AppLanguage.PORTUGUESE_PORTUGAL to "Português (Portugal)",
    AppLanguage.DUTCH to "Nederlands",
    AppLanguage.GREEK to "Ελληνικά",
    AppLanguage.FINNISH to "Suomi",
    AppLanguage.DANISH to "Dansk",
    AppLanguage.NORWEGIAN_BOKMAL to "Norsk bokmål",
    AppLanguage.SWEDISH to "Svenska",
    AppLanguage.ARABIC to "العربية",
    AppLanguage.KAZAKH to "Қазақша",
    AppLanguage.HINDI to "हिन्दी",
    AppLanguage.CHINESE_SIMPLIFIED to "简体中文",
    AppLanguage.CHINESE_TRADITIONAL to "繁體中文",
    AppLanguage.JAPANESE to "日本語",
    AppLanguage.KOREAN to "한국어",
)

internal fun languageAutonym(language: AppLanguage): String = autonyms.getValue(language)

/** System first, then a stable autonym order in both pickers. */
val LanguageChoices = listOf(AppLanguage.SYSTEM) + AppLanguage.translated.sortedBy(::languageAutonym)

@Composable
@ReadOnlyComposable
fun languageLabel(language: AppLanguage): String = if (language == AppLanguage.SYSTEM) {
    stringResource(R.string.language_system)
} else {
    languageAutonym(language)
}
