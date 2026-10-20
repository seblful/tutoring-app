package com.seblful.klas.data.local

import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsLanguageMigrationTest {

    @get:Rule
    val helper = migrationTestHelper(TEST_DB)

    @Test
    fun aSettingsRowSurvivesTheUpgradeAndFollowsThePhone() {
        helper.createDatabase(5).use { db ->
            db.execSQL(
                """
                INSERT INTO settings (
                    id, notificationsEnabled, calendarSyncEnabled, emailInvitesEnabled, onboardingComplete,
                    reminderLeadMinutes, dailyAgendaEnabled, agendaMinuteOfDay, debtDigestEnabled,
                    debtDayOfWeek, debtMinuteOfDay, autoCompleteEnabled, themeMode, dynamicColorEnabled,
                    defaultLessonMinutes, defaultGroupPriceMinor, currency, showGroupsTab, lastExportMillis
                ) VALUES (
                    0, 1, 1, 0, 1, 60, 1, 480, 0, 1, 540, 1, 'DARK', 0, 90, 3000, 'RUB', 1, 1750000000000
                )
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(6, listOf(Migrations.MIGRATION_5_6))

        val row = migrated.row(
            "SELECT * FROM settings WHERE id = 0",
            missing = "the row went missing — a migration must not recreate the table",
        )
        assertEquals(
            "«follow the phone» is the honest backfill: the tutor never chose Russian, the app was " +
                "only ever written in it",
            "SYSTEM",
            row["language"],
        )
        assertEquals("RUB", row["currency"])
        assertEquals("DARK", row["themeMode"])
        assertEquals(60L, row["reminderLeadMinutes"])
        assertEquals(90L, row["defaultLessonMinutes"])
        assertEquals(3000L, row["defaultGroupPriceMinor"])
        assertEquals(1L, row["calendarSyncEnabled"])
        assertEquals(1L, row["autoCompleteEnabled"])
        assertEquals(480L, row["agendaMinuteOfDay"])
        assertEquals(1750000000000L, row["lastExportMillis"])
    }

    @Test
    fun anEmptySettingsTableStillValidates() {
        helper.createDatabase(5).close()

        val migrated = helper.runMigrationsAndValidate(6, listOf(Migrations.MIGRATION_5_6))

        assertEquals("nothing was invented on the way", 0L, migrated.long("SELECT count(*) FROM settings"))
    }

    private companion object {
        const val TEST_DB = "migration-klas.db"
    }
}
