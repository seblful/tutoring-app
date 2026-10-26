package com.seblful.klas.data.settings

import com.seblful.klas.domain.time.MINUTES_PER_HOUR
import kotlinx.datetime.DayOfWeek

/**
 * What a setting is until the tutor moves it, for [SettingsEntity], [AppSettings] and the screens that
 * read a value before settings have loaded. The `@ColumnInfo(defaultValue)` strings stay literal — Room
 * checks them against the migrations — and `SettingsDefaultsTest` holds them to these.
 */
object SettingsDefaults {

    const val LESSON_MINUTES = MINUTES_PER_HOUR

    const val AGENDA_MINUTE_OF_DAY = 9 * MINUTES_PER_HOUR
    const val DEBT_MINUTE_OF_DAY = 9 * MINUTES_PER_HOUR
    const val UNFINISHED_MINUTE_OF_DAY = 21 * MINUTES_PER_HOUR

    /** The day a tutor's week is planned on. */
    val DEBT_DAY: DayOfWeek = DayOfWeek.MONDAY
}
