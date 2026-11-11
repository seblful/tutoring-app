package com.seblful.klas.format

import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.settings.DevicePreferences
import com.seblful.klas.text.Texts
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale

/** Resolves shared language state at the point Android renders background content. */
@SingleIn(AppScope::class)
class DisplayLocale @Inject constructor(
    private val settings: SettingsRepository,
    private val devicePreferences: DevicePreferences,
) {
    /** The language last read or written; the phone's until something reads the settings row. */
    val current: Locale get() = settings.language.value.locale()

    /** [current]'s words, for what is written in the background: notifications, calendar events. */
    val texts: Texts get() = Texts.of(current.toLanguageTag())

    /** The tutor's language as stored, now and on each change: for what stays on screen, such as a widget. */
    fun observe(): Flow<Locale> = settings.observe().map { it.language.locale() }.distinctUntilChanged()

    private fun AppLanguage.locale(): Locale = Locale.forLanguageTag(tagFor(devicePreferences.languageTags))
}
