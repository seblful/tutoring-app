package com.seblful.klas.data.local

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeworkTableMigrationTest {

    @get:Rule
    val helper = migrationTestHelper(TEST_DB)

    private data class Row(
        val studentId: Long?,
        val groupId: Long?,
        val text: String,
        val givenAtMillis: Long,
        val doneAtMillis: Long?,
    )

    private fun SQLiteConnection.seedV8() {
        execSQL("INSERT INTO subjects (id, name) VALUES (1, 'Алгебра')")
        execSQL(
            "INSERT INTO students (id, name, phone, email, note, defaultMode, archived) VALUES " +
                "(1, 'Аня', '', '', '', 'IN_PERSON', 0), (2, 'Борис', '', '', '', 'IN_PERSON', 0)",
        )
        execSQL(
            "INSERT INTO student_groups (id, name, note, subjectId, priceMinor, defaultMode, archived) " +
                "VALUES (7, '9 «Б»', '', 1, 2000, 'IN_PERSON', 0)",
        )
        execSQL("INSERT INTO group_members (groupId, studentId) VALUES (7, 1), (7, 2)")
        execSQL(
            "INSERT INTO lessons (id, subjectId, startMillis, durationMinutes, status, groupId, mode, notes, homework) " +
                "VALUES " +
                "(10, 1, 1000, 60, 'COMPLETED', NULL, 'IN_PERSON', 'устала к концу', '§12'), " +
                "(11, 1, 2000, 60, 'SCHEDULED', NULL, 'IN_PERSON', '', '§13'), " +
                "(20, 1, 3000, 60, 'COMPLETED', 7, 'IN_PERSON', '', 'Задачи'), " +
                "(21, 1, 4000, 60, 'COMPLETED', 7, 'IN_PERSON', '', 'Ещё'), " +
                "(30, 1, 5000, 60, 'CANCELLED', NULL, 'IN_PERSON', '', 'не было'), " +
                "(40, 1, 6000, 60, 'SCHEDULED', NULL, 'IN_PERSON', '', '')",
        )
        execSQL(
            "INSERT INTO attendance (id, lessonId, studentId, state, priceMinor, chargeMinor, calendarEventId, " +
                "homeworkResult) VALUES " +
                "(100, 10, 1, 'PRESENT', 3000, 3000, NULL, 'PARTIAL'), " +
                "(101, 11, 1, 'EXPECTED', 3000, 0, NULL, NULL), " +
                "(200, 20, 1, 'PRESENT', 2000, 2000, NULL, 'DONE'), " +
                "(201, 20, 2, 'PRESENT', 2000, 2000, NULL, 'NOT_DONE'), " +
                "(210, 21, 1, 'PRESENT', 2000, 2000, NULL, 'DONE'), " +
                "(211, 21, 2, 'PRESENT', 2000, 2000, NULL, NULL), " +
                "(300, 30, 2, 'EXPECTED', 3000, 0, NULL, NULL), " +
                "(400, 40, 2, 'EXPECTED', 3000, 0, 'evt-9', NULL)",
        )
    }

    private fun migrated(): SQLiteConnection {
        helper.createDatabase(8).use { it.seedV8() }
        return helper.runMigrationsAndValidate(9, listOf(Migrations.MIGRATION_8_9))
    }

    private fun SQLiteConnection.homework(): List<Row> =
        rows("SELECT studentId, groupId, text, givenAtMillis, doneAtMillis FROM homework ORDER BY givenAtMillis")
            .map {
                Row(
                    studentId = it["studentId"] as Long?,
                    groupId = it["groupId"] as Long?,
                    text = it["text"] as String,
                    givenAtMillis = it["givenAtMillis"] as Long,
                    doneAtMillis = it["doneAtMillis"] as Long?,
                )
            }

    @Test
    fun everyLessonsHomeworkMovesIntoItsOwnTable() {
        val db = migrated()

        assertEquals(
            listOf(
                Row(studentId = 1, groupId = null, text = "§12", givenAtMillis = 1000, doneAtMillis = 1000),
                Row(studentId = 1, groupId = null, text = "§13", givenAtMillis = 2000, doneAtMillis = null),
                Row(studentId = null, groupId = 7, text = "Задачи", givenAtMillis = 3000, doneAtMillis = 3000),
                Row(studentId = null, groupId = 7, text = "Ещё", givenAtMillis = 4000, doneAtMillis = null),
            ),
            db.homework(),
        )
    }

    @Test
    fun theNoteAndTheOldHomeworkColumnsAreEmptied() {
        val db = migrated()

        assertEquals(0L, db.long("SELECT count(*) FROM lessons WHERE notes != '' OR homework != ''"))
        assertEquals(0L, db.long("SELECT count(*) FROM attendance WHERE homeworkResult IS NOT NULL"))
    }

    @Test
    fun theViewStillReadsEveryClass() {
        val db = migrated()

        assertEquals(6L, db.long("SELECT count(*) FROM lessons"))
        assertEquals(8L, db.long("SELECT count(*) FROM classes"))
        val viewed = db.row("SELECT chargeMinor, calendarEventId FROM classes WHERE id = 400")
        assertEquals("the student's half of the join is untouched", 0L, viewed["chargeMinor"])
        assertEquals("evt-9", viewed["calendarEventId"])
    }

    @Test
    fun homeworkWrittenAfterTheUpgradeReadsBack() {
        val db = migrated()

        db.execSQL("INSERT INTO homework (studentId, groupId, text, givenAtMillis, doneAtMillis) VALUES (2, NULL, '§14', 7000, NULL)")

        assertEquals(Row(2, null, "§14", 7000, null), db.homework().last())
    }

    @Test
    fun aReleasedV7DatabaseUpgradesThroughBothSteps() {
        helper.createDatabase(7).use { db ->
            db.execSQL("INSERT INTO subjects (id, name) VALUES (1, 'Алгебра')")
            db.execSQL(
                "INSERT INTO students (id, name, phone, email, note, defaultMode, archived) " +
                    "VALUES (1, 'Аня', '', '', '', 'IN_PERSON', 0)",
            )
            db.execSQL(
                "INSERT INTO lessons (id, subjectId, startMillis, durationMinutes, status, groupId, mode, notes) " +
                    "VALUES (7, 1, 1750000000000, 60, 'COMPLETED', NULL, 'IN_PERSON', '§12; устала')",
            )
            db.execSQL(
                "INSERT INTO attendance (id, lessonId, studentId, state, priceMinor, chargeMinor, " +
                    "calendarEventId) VALUES (3, 7, 1, 'PRESENT', 3000, 3000, NULL)",
            )
        }

        val db = helper.runMigrationsAndValidate(9, listOf(Migrations.MIGRATION_7_8, Migrations.MIGRATION_8_9))

        assertEquals(0L, db.long("SELECT count(*) FROM homework"))
        assertEquals(0L, db.long("SELECT count(*) FROM lessons WHERE notes != ''"))
        assertEquals(1L, db.long("SELECT count(*) FROM classes"))
    }

    @Test
    fun anEmptyDatabaseStillValidates() {
        helper.createDatabase(8).close()

        val db = helper.runMigrationsAndValidate(9, listOf(Migrations.MIGRATION_8_9))

        assertEquals(0L, db.long("SELECT count(*) FROM homework"))
    }

    private companion object {
        const val TEST_DB = "migration-homework-table-klas.db"
    }
}
