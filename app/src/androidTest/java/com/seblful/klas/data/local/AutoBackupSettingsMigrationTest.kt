package com.seblful.klas.data.local

import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoBackupSettingsMigrationTest {

    @get:Rule
    val helper = migrationTestHelper(TEST_DB)

    @Test
    fun anUpgradedInstallHasNoWeeklyCopyUntilAFolderIsPicked() {
        helper.createDatabase(11).use { db ->
            db.execSQL(
                """
                INSERT INTO settings (
                    id, notificationsEnabled, calendarSyncEnabled, emailInvitesEnabled, onboardingComplete,
                    reminderLeadMinutes, dailyAgendaEnabled, agendaMinuteOfDay, debtDigestEnabled,
                    debtDayOfWeek, debtMinuteOfDay, unfinishedDigestEnabled, unfinishedMinuteOfDay,
                    autoCompleteEnabled, themeMode, dynamicColorEnabled, defaultLessonMinutes,
                    defaultGroupPriceMinor, currency, language, lastExportMillis, lockScreenDetails,
                    lockScreenWidgets, appLock, relockAfterMinutes, hideInRecents
                ) VALUES (
                    0, 1, 1, 0, 1, 60, 1, 480, 0, 1, 540, 0, 1260, 1, 'DARK', 0, 90, 3000, 'RUB', 'SYSTEM',
                    1750000000000, 1, 0, 1, 5, 0
                )
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(12, listOf(Migrations.MIGRATION_11_12))

        val row = migrated.row(
            "SELECT * FROM settings WHERE id = 0",
            missing = "the row went missing — a migration must not recreate the table",
        )
        assertNull("off until a folder is picked", row["autoBackupFolder"])
        assertEquals(0L, row["autoBackupFailed"])
        assertEquals("the last export is kept", 1750000000000L, row["lastExportMillis"])
        assertEquals(5L, row["relockAfterMinutes"])
    }

    private companion object {
        const val TEST_DB = "migration-auto-backup.db"
    }
}
