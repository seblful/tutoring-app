package com.seblful.klas.data.local

import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LessonNotesMigrationTest {

    @get:Rule
    val helper = migrationTestHelper(TEST_DB)

    @Test
    fun aLessonKeepsItsHourAndGainsAnEmptyNote() {
        helper.createDatabase(6).use { db ->
            db.execSQL("INSERT INTO subjects (id, name) VALUES (1, 'Алгебра')")
            db.execSQL(
                "INSERT INTO students (id, name, phone, email, note, defaultMode, archived) " +
                    "VALUES (1, 'Аня', '', '', '', 'IN_PERSON', 0)",
            )
            db.execSQL(
                "INSERT INTO lessons (id, subjectId, startMillis, durationMinutes, status, groupId, mode) " +
                    "VALUES (7, 1, 1750000000000, 90, 'COMPLETED', NULL, 'REMOTE')",
            )
            db.execSQL(
                "INSERT INTO attendance (id, lessonId, studentId, state, priceMinor, chargeMinor, " +
                    "calendarEventId) VALUES (3, 7, 1, 'PRESENT', 3000, 4500, 'evt-1')",
            )
        }

        val migrated = helper.runMigrationsAndValidate(7, listOf(Migrations.MIGRATION_6_7))

        val lesson = migrated.row(
            "SELECT * FROM lessons WHERE id = 7",
            missing = "the lesson went missing — a migration must not recreate the table",
        )
        assertEquals(
            "a lesson taught before the app could hold a note has none, rather than invented text",
            "",
            lesson["notes"],
        )
        assertEquals(1750000000000L, lesson["startMillis"])
        assertEquals(90L, lesson["durationMinutes"])
        assertEquals("COMPLETED", lesson["status"])
        assertEquals("REMOTE", lesson["mode"])

        val viewed = migrated.row(
            "SELECT notes, chargeMinor, status FROM classes WHERE lessonId = 7",
            missing = "the view came back without the lesson in it",
        )
        assertEquals("", viewed["notes"])
        assertEquals("the student's half of the join is untouched", 4500L, viewed["chargeMinor"])
        assertEquals("the lesson's half too", "COMPLETED", viewed["status"])
    }

    @Test
    fun theSettingsRowGainsTheEveningDigestSwitchedOff() {
        helper.createDatabase(6).use { db ->
            db.execSQL(
                """
                INSERT INTO settings (
                    id, notificationsEnabled, calendarSyncEnabled, emailInvitesEnabled, onboardingComplete,
                    reminderLeadMinutes, dailyAgendaEnabled, agendaMinuteOfDay, debtDigestEnabled,
                    debtDayOfWeek, debtMinuteOfDay, autoCompleteEnabled, themeMode, dynamicColorEnabled,
                    defaultLessonMinutes, defaultGroupPriceMinor, currency, showGroupsTab, lastExportMillis,
                    language
                ) VALUES (
                    0, 1, 1, 0, 1, 60, 1, 480, 1, 3, 600, 1, 'DARK', 0, 90, 3000, 'RUB', 1,
                    1750000000000, 'ENGLISH'
                )
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(7, listOf(Migrations.MIGRATION_6_7))

        val row = migrated.row("SELECT * FROM settings WHERE id = 0")
        assertEquals(
            "a notification nobody asked for does not arrive because the app was updated",
            0L,
            row["unfinishedDigestEnabled"],
        )
        assertEquals("21:00 as minutes past local midnight", 1260L, row["unfinishedMinuteOfDay"])
        assertEquals("ENGLISH", row["language"])
        assertEquals("RUB", row["currency"])
        assertEquals("DARK", row["themeMode"])
        assertEquals(480L, row["agendaMinuteOfDay"])
        assertEquals(3L, row["debtDayOfWeek"])
        assertEquals(600L, row["debtMinuteOfDay"])
    }

    @Test
    fun anEmptyDatabaseStillValidates() {
        helper.createDatabase(6).close()

        val migrated = helper.runMigrationsAndValidate(7, listOf(Migrations.MIGRATION_6_7))

        assertEquals("nothing was invented on the way", 0L, migrated.long("SELECT count(*) FROM lessons"))
    }

    private companion object {
        const val TEST_DB = "migration-notes-klas.db"
    }
}
