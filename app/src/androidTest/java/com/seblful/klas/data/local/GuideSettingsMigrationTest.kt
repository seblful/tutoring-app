package com.seblful.klas.data.local

import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuideSettingsMigrationTest {

    @get:Rule
    val helper = migrationTestHelper(TEST_DB)

    @Test
    fun aTutorAlreadyThroughSetupStartsWithTheGuideDone() {
        val row = migrate(onboarded = true)
        assertEquals("SCHEDULE_DATE,LESSON_SWIPE,LESSON_PICK,HOMEWORK_SWIPE", row["tipsSeen"])
        assertEquals(1L, row["firstStepsHidden"])
        assertEquals("kept through the step", 90L, row["defaultLessonMinutes"])
    }

    @Test
    fun aTutorStillInSetupGetsTheWholeGuide() {
        val row = migrate(onboarded = false)
        assertEquals("", row["tipsSeen"])
        assertEquals(0L, row["firstStepsHidden"])
    }

    @Test
    fun aPhoneThatUpgradesKeepsItsWeeksWhereThePhoneStartsThem() {
        helper.createDatabase(15).use { db ->
            db.execSQL(
                "INSERT INTO settings (id, notificationsEnabled, calendarSyncEnabled, onboardingComplete, " +
                    "reminderLeadMinutes, autoCompleteEnabled, themeMode, defaultLessonMinutes, currency) " +
                    "VALUES (0, 1, 0, 1, 30, 0, 'SYSTEM', 90, 'BYN')",
            )
        }
        val migrated = helper.runMigrationsAndValidate(16, listOf(Migrations.MIGRATION_15_16))
        val row = migrated.row("SELECT * FROM settings WHERE id = 0", missing = "the settings row went missing")
        assertEquals("PHONE", row["weekStart"])
    }

    private fun migrate(onboarded: Boolean): Map<String, Any?> {
        helper.createDatabase(13).use { db ->
            db.execSQL(
                "INSERT INTO settings (id, notificationsEnabled, calendarSyncEnabled, onboardingComplete, " +
                    "reminderLeadMinutes, autoCompleteEnabled, themeMode, defaultLessonMinutes, currency) " +
                    "VALUES (0, 1, 0, ${if (onboarded) 1 else 0}, 30, 0, 'SYSTEM', 90, 'BYN')",
            )
        }
        val migrated = helper.runMigrationsAndValidate(14, listOf(Migrations.MIGRATION_13_14))
        return migrated.row("SELECT * FROM settings WHERE id = 0", missing = "the settings row went missing")
    }

    private companion object {
        const val TEST_DB = "migration-guide.db"
    }
}
