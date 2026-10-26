package com.seblful.klas.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsDefaultsTest {

    @Test
    fun `the stored row and the settings model start from the same values`() {
        val row = SettingsEntity()
        val settings = AppSettings()

        assertEquals(row.defaultLessonMinutes, settings.defaultLessonMinutes)
        assertEquals(row.agendaMinuteOfDay, settings.agendaMinuteOfDay)
        assertEquals(row.debtMinuteOfDay, settings.debtMinuteOfDay)
        assertEquals(row.unfinishedMinuteOfDay, settings.unfinishedMinuteOfDay)
        assertEquals(row.debtDayOfWeek, settings.debtDayOfWeek)
    }

    @Test
    fun `the defaults keep the values the migrations wrote into existing databases`() {
        // `Migrations` and `SettingsEntity`'s `@ColumnInfo(defaultValue)` spell these as SQL literals.
        assertEquals(540, SettingsDefaults.AGENDA_MINUTE_OF_DAY)
        assertEquals(540, SettingsDefaults.DEBT_MINUTE_OF_DAY)
        assertEquals(1260, SettingsDefaults.UNFINISHED_MINUTE_OF_DAY)
        assertEquals(1, SettingsDefaults.DEBT_DAY.ordinal + 1)
    }
}
