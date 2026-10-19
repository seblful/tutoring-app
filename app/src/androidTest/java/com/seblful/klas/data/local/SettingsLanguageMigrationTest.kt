package com.seblful.klas.data.local

import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **The app's first real migration, run on a real database.**
 *
 * v5 shipped, in `0.3.0`, so [Migrations.MIGRATION_5_6] executes on phones holding a tutor's students,
 * lessons and payments. `MigrationChainTest` proves the step *exists* and is wired in, which it can do on the
 * JVM; whether it *works* needs SQLite, and there is exactly one way for this one to fail that review would
 * not catch: `settings.language`'s `DEFAULT` disagreeing with the entity's `defaultValue`, which Room
 * validates against the bundled schema the instant the upgrade finishes — a crash on first launch after a
 * Play update.
 *
 * Two things are asserted, and the second is the one that matters:
 *
 * 1. The upgrade validates. `runMigrationsAndValidate` compares the migrated database against the exported
 *    v6 schema, column types, defaults and all.
 * 2. **A row that was already there keeps everything it had, and gains "SYSTEM".** A migration that dropped
 *    and recreated the table would pass (1) perfectly.
 */
@RunWith(AndroidJUnit4::class)
class SettingsLanguageMigrationTest {

    @get:Rule
    val helper = migrationTestHelper(TEST_DB)

    @Test
    fun aSettingsRowSurvivesTheUpgradeAndFollowsThePhone() {
        // A v5 row with settings a tutor would recognise as theirs: reminders an hour ahead, sync on, the
        // dark theme, rubles. Written as SQL because the v5 entity no longer exists to write it with — which
        // is the whole shape of a migration test.
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
        // The columns beside it, which is what says the row was altered rather than replaced.
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

    /**
     * The other v5 state, and the more common one: **no settings row at all.**
     *
     * The table is only written once the tutor changes something, so an install that never opened Settings
     * upgrades with it empty — and `ALTER TABLE … ADD COLUMN` on an empty table is exactly where a wrong
     * `DEFAULT` hides, since there is no row for it to be visible on.
     */
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
