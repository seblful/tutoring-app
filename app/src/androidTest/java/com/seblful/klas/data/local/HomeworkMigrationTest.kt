package com.seblful.klas.data.local

import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeworkMigrationTest {

    @get:Rule
    val helper = migrationTestHelper(TEST_DB)

    @Test
    fun aLessonKeepsItsNoteAndGainsNoHomework() {
        helper.createDatabase(7).use { db ->
            db.execSQL("INSERT INTO subjects (id, name) VALUES (1, 'Алгебра')")
            db.execSQL(
                "INSERT INTO students (id, name, phone, email, note, defaultMode, archived) " +
                    "VALUES (1, 'Аня', '', '', '', 'IN_PERSON', 0)",
            )
            db.execSQL(
                "INSERT INTO lessons (id, subjectId, startMillis, durationMinutes, status, groupId, mode, notes) " +
                    "VALUES (7, 1, 1750000000000, 90, 'COMPLETED', NULL, 'REMOTE', '§12, упр. 4–7; устала')",
            )
            db.execSQL(
                "INSERT INTO attendance (id, lessonId, studentId, state, priceMinor, chargeMinor, " +
                    "calendarEventId) VALUES (3, 7, 1, 'PRESENT', 3000, 4500, 'evt-1')",
            )
        }

        val migrated = helper.runMigrationsAndValidate(8, listOf(Migrations.MIGRATION_7_8))

        val lesson = migrated.row(
            "SELECT notes, homework FROM lessons WHERE id = 7",
            missing = "the lesson went missing — a migration must not recreate the table",
        )
        assertEquals("the note is the tutor's and stays as written", "§12, упр. 4–7; устала", lesson["notes"])
        assertEquals(
            "no homework is guessed out of the note — its private half would reach the student",
            "",
            lesson["homework"],
        )

        val viewed = migrated.row(
            "SELECT homework, homeworkResult, chargeMinor, notes FROM classes WHERE lessonId = 7",
            missing = "the view came back without the lesson in it",
        )
        assertEquals("", viewed["homework"])
        assertNull("nobody's homework has been checked before the app could hold a verdict", viewed["homeworkResult"])
        assertEquals("the student's half of the join is untouched", 4500L, viewed["chargeMinor"])
        assertEquals("and the lesson's", "§12, упр. 4–7; устала", viewed["notes"])
    }

    @Test
    fun aVerdictWrittenAfterTheUpgradeReadsBack() {
        helper.createDatabase(7).use { db ->
            db.execSQL("INSERT INTO subjects (id, name) VALUES (1, 'Алгебра')")
            db.execSQL(
                "INSERT INTO students (id, name, phone, email, note, defaultMode, archived) " +
                    "VALUES (1, 'Аня', '', '', '', 'IN_PERSON', 0)",
            )
            db.execSQL(
                "INSERT INTO lessons (id, subjectId, startMillis, durationMinutes, status, groupId, mode, notes) " +
                    "VALUES (7, 1, 1750000000000, 60, 'COMPLETED', NULL, 'IN_PERSON', '')",
            )
            db.execSQL(
                "INSERT INTO attendance (id, lessonId, studentId, state, priceMinor, chargeMinor, " +
                    "calendarEventId) VALUES (3, 7, 1, 'PRESENT', 3000, 3000, NULL)",
            )
        }

        val migrated = helper.runMigrationsAndValidate(8, listOf(Migrations.MIGRATION_7_8))
        migrated.execSQL("UPDATE lessons SET homework = '§13' WHERE id = 7")
        migrated.execSQL("UPDATE attendance SET homeworkResult = 'DONE' WHERE id = 3")

        assertEquals(
            mapOf("homework" to "§13", "homeworkResult" to "DONE"),
            migrated.row("SELECT homework, homeworkResult FROM classes WHERE id = 3"),
        )
    }

    @Test
    fun anEmptyDatabaseStillValidates() {
        helper.createDatabase(7).close()

        val migrated = helper.runMigrationsAndValidate(8, listOf(Migrations.MIGRATION_7_8))

        assertEquals("nothing was invented on the way", 0L, migrated.long("SELECT count(*) FROM classes"))
    }

    private companion object {
        const val TEST_DB = "migration-homework-klas.db"
    }
}
