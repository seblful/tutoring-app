package com.seblful.klas.data.local

import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **v6 → v7 on a real database**: a lesson gains its note, and the settings row gains the evening digest.
 *
 * The sibling of [SettingsLanguageMigrationTest] and written for the same reason. `MigrationChainTest`
 * proves on the JVM that the step exists and is wired in; whether it *works* needs SQLite, and this step
 * has three `DEFAULT`s that have to match three `defaultValue`s exactly — `''`, `0` and `1260`. Room
 * validates that the instant the upgrade finishes, which without a test here means finding out as a crash
 * on first launch after a Play update.
 *
 * It also covers the half a settings migration does not: this one touches **`lessons`**, a table with a
 * tutor's history in it, and the view over it (`classes`) has to come back projecting the new column or
 * every read of a lesson fails. Room drops and recreates its views after the migrations run, so that is
 * exactly the kind of thing that is true by construction until it is not.
 */
@RunWith(AndroidJUnit4::class)
class LessonNotesMigrationTest {

    @get:Rule
    val helper = migrationTestHelper(TEST_DB)

    /**
     * A v6 lesson with a student in it, migrated — and still readable **through the view**, which is what
     * every screen in the app reads a lesson with.
     */
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
        // The columns beside it, which is what says the row was altered rather than replaced.
        assertEquals(1750000000000L, lesson["startMillis"])
        assertEquals(90L, lesson["durationMinutes"])
        assertEquals("COMPLETED", lesson["status"])
        assertEquals("REMOTE", lesson["mode"])

        // The `classes` view is what every read above storage goes through, and it now projects
        // `lessons.notes`. Room recreates its views after the migrations; this is that actually happening.
        val viewed = migrated.row(
            "SELECT notes, chargeMinor, status FROM classes WHERE lessonId = 7",
            missing = "the view came back without the lesson in it",
        )
        assertEquals("", viewed["notes"])
        assertEquals("the student's half of the join is untouched", 4500L, viewed["chargeMinor"])
        assertEquals("the lesson's half too", "COMPLETED", viewed["status"])
    }

    /**
     * The settings half, on a row that already exists — so the two new columns arrive with the tutor's
     * other answers intact and the evening digest **off**.
     *
     * Off is the decision rather than the absence of one: an install being upgraded has never been offered
     * this notification, so acquiring one by updating the app would be the app asking on its own behalf.
     */
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
        // The answers the tutor did give, still theirs.
        assertEquals("ENGLISH", row["language"])
        assertEquals("RUB", row["currency"])
        assertEquals("DARK", row["themeMode"])
        assertEquals(480L, row["agendaMinuteOfDay"])
        assertEquals(3L, row["debtDayOfWeek"])
        assertEquals(600L, row["debtMinuteOfDay"])
    }

    /**
     * The other v6 state, and the more common one: **no settings row and no lessons at all.**
     *
     * `ALTER TABLE … ADD COLUMN` on an empty table is exactly where a wrong `DEFAULT` hides — there is no
     * row for it to be visible on, and only Room's schema validation catches it.
     */
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
