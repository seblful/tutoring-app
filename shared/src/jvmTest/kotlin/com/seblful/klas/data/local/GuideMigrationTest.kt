package com.seblful.klas.data.local

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The v13 → v14, v14 → v15 and v15 → v16 steps on the settings table as the version before exported it. The
 * device test (`GuideSettingsMigrationTest`) also validates v14 against its schema; these run with the unit tests.
 */
class GuideMigrationTest {

    private val directory = Files.createTempDirectory("klas-guide-migration-").toFile()

    @AfterTest
    fun removeDatabase() {
        directory.deleteRecursively()
    }

    @Test
    fun `a tutor already through setup starts with the guide done`() {
        val row = migrated(onboarded = true)
        assertEquals("SCHEDULE_DATE,LESSON_SWIPE,LESSON_PICK,HOMEWORK_SWIPE", row.first)
        assertEquals(1L, row.second)
    }

    @Test
    fun `a tutor still in setup gets the whole guide`() {
        val row = migrated(onboarded = false)
        assertEquals("", row.first)
        assertEquals(0L, row.second)
    }

    @Test
    fun `a phone that upgrades has nothing to set up`() {
        val phoneSetup = BundledSQLiteDriver().open(File(directory, "klas.db").path).use { connection ->
            connection.execSQL(settingsTableAt(14))
            connection.execSQL(
                "INSERT INTO settings (id, notificationsEnabled, calendarSyncEnabled, onboardingComplete, " +
                    "reminderLeadMinutes, autoCompleteEnabled, themeMode, defaultLessonMinutes, currency) " +
                    "VALUES (0, 1, 0, 1, 30, 0, 'SYSTEM', 60, 'BYN')",
            )
            Migrations.MIGRATION_14_15.migrate(connection)
            connection.prepare("SELECT phoneSetup FROM settings WHERE id = 0").use {
                check(it.step()) { "the settings row went missing" }
                it.getText(0)
            }
        }
        assertEquals("", phoneSetup)
    }

    @Test
    fun `a phone that upgrades keeps its weeks where the phone starts them`() {
        val weekStart = BundledSQLiteDriver().open(File(directory, "klas.db").path).use { connection ->
            connection.execSQL(settingsTableAt(15))
            connection.execSQL(
                "INSERT INTO settings (id, notificationsEnabled, calendarSyncEnabled, onboardingComplete, " +
                    "reminderLeadMinutes, autoCompleteEnabled, themeMode, defaultLessonMinutes, currency) " +
                    "VALUES (0, 1, 0, 1, 30, 0, 'SYSTEM', 60, 'BYN')",
            )
            Migrations.MIGRATION_15_16.migrate(connection)
            connection.prepare("SELECT weekStart FROM settings WHERE id = 0").use {
                check(it.step()) { "the settings row went missing" }
                it.getText(0)
            }
        }
        assertEquals("PHONE", weekStart)
    }

    private fun migrated(onboarded: Boolean): Pair<String, Long> =
        BundledSQLiteDriver().open(File(directory, "klas.db").path).use { connection ->
            connection.execSQL(settingsTableAt(13))
            connection.execSQL(
                "INSERT INTO settings (id, notificationsEnabled, calendarSyncEnabled, onboardingComplete, " +
                    "reminderLeadMinutes, autoCompleteEnabled, themeMode, defaultLessonMinutes, currency) " +
                    "VALUES (0, 1, 0, ${if (onboarded) 1 else 0}, 30, 0, 'SYSTEM', 60, 'BYN')",
            )
            Migrations.MIGRATION_13_14.migrate(connection)
            connection.settingsGuide()
        }

    private fun SQLiteConnection.settingsGuide(): Pair<String, Long> =
        prepare("SELECT tipsSeen, firstStepsHidden FROM settings WHERE id = 0").use {
            check(it.step()) { "the settings row went missing" }
            it.getText(0) to it.getLong(1)
        }

    /** The table exactly as [version] declared it, so the step runs against what an installed phone holds. */
    private fun settingsTableAt(version: Int): String {
        val schema =
            Json.parseToJsonElement(File("schemas/com.seblful.klas.data.local.AppDatabase/$version.json").readText())
        val settings = schema.jsonObject.getValue("database").jsonObject.getValue("entities").jsonArray
            .map { it.jsonObject }
            .single { it.getValue("tableName").jsonPrimitive.content == "settings" }
        return settings.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", "settings")
    }
}
