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

/** Resolves shared language state at the point a platform renders background content. */
@SingleIn(AppScope::class)
class DisplayLocale @Inject constructor(
    private val settings: SettingsRepository,
    private val devicePreferences: DevicePreferences,
) {
    /** The language last read or written; the phone's until something reads the settings row. */
    val tag: String get() = settings.language.value.resolved()

    /** [tag]'s words, for what is written in the background: notifications, calendar events. */
    val texts: Texts get() = Texts.of(tag)

    /** The tutor's language as stored, now and on each change: for what stays on screen, such as a widget. */
    fun observe(): Flow<String> = settings.observe().map { it.language.resolved() }.distinctUntilChanged()

    private fun AppLanguage.resolved(): String = tagFor(devicePreferences.languageTags)
}
