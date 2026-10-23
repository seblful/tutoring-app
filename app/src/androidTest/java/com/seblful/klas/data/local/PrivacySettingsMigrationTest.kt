package com.seblful.klas.data.local

import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacySettingsMigrationTest {

    @get:Rule
    val helper = migrationTestHelper(TEST_DB)

    @Test
    fun anUpgradedInstallStartsAsClosedAsANewOne() {
        helper.createDatabase(10).use { db ->
            db.execSQL(
                """
                INSERT INTO settings (
                    id, notificationsEnabled, calendarSyncEnabled, emailInvitesEnabled, onboardingComplete,
                    reminderLeadMinutes, dailyAgendaEnabled, agendaMinuteOfDay, debtDigestEnabled,
                    debtDayOfWeek, debtMinuteOfDay, unfinishedDigestEnabled, unfinishedMinuteOfDay,
                    autoCompleteEnabled, themeMode, dynamicColorEnabled, defaultLessonMinutes,
                    defaultGroupPriceMinor, currency, language, lastExportMillis
                ) VALUES (
                    0, 1, 1, 0, 1, 60, 1, 480, 0, 1, 540, 0, 1260, 1, 'DARK', 0, 90, 3000, 'RUB', 'SYSTEM', NULL
                )
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(11, listOf(Migrations.MIGRATION_10_11))

        val row = migrated.row(
            "SELECT * FROM settings WHERE id = 0",
            missing = "the row went missing — a migration must not recreate the table",
        )
        assertEquals("names stay off the lock screen", 0L, row["lockScreenDetails"])
        assertEquals(0L, row["lockScreenWidgets"])
        assertEquals("no lock the tutor never asked for", 0L, row["appLock"])
        assertEquals(1L, row["relockAfterMinutes"])
        assertEquals(0L, row["hideInRecents"])
        assertEquals("RUB", row["currency"])
        assertEquals(90L, row["defaultLessonMinutes"])
    }

    private companion object {
        const val TEST_DB = "migration-privacy.db"
    }
}
