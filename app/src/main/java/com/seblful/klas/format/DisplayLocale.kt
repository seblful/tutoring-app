package com.seblful.klas.format

import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.settings.DevicePreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.Locale

/** Resolves shared language state at the point Android renders background content. */
@SingleIn(AppScope::class)
class DisplayLocale @Inject constructor(
    private val settings: SettingsRepository,
    private val devicePreferences: DevicePreferences,
) {
    val current: Locale get() = Locale.forLanguageTag(settings.language.value.tagFor(devicePreferences.languageTags))
}
