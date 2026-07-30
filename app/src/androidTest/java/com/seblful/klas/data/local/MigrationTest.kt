package com.seblful.klas.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **v1 → v2 keeps every row.** The database this migrates is a tutor's only copy of their students,
 * lessons and money, so what it does to them is asserted rather than reasoned about.
 *
 * Room's [MigrationTestHelper] builds a real v1 database from the exported `schemas/1.json`, runs
 * [MIGRATION_1_2] against it, and — via `validateMigration` — refuses to pass if the result does not
 * match `schemas/2.json` column for column, index for index, view for view. That last part is why the
 * migration writes the `classes` view out in full: a view that drifts from the one Room generates fails
 * here rather than on somebody's phone.
 *
 * The rows are inserted as SQL rather than through Room, because at v1 the entities that could write them
 * no longer exist in this build. That is the point of the exported schema.
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class MigrationTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private val name = "migration-test.db"

    /** The v1 shape: one row per student per lesson, carrying the lesson's own facts with it. */
    private fun SupportSQLiteDatabase.seedV1() {
        execSQL("INSERT INTO subjects (id, name) VALUES (1, 'Математика')")
        execSQL(
            "INSERT INTO students (id, name, phone, email, note, defaultMode, archived) " +
                "VALUES (1, 'Аня', '', '', '', 'IN_PERSON', 0), (2, 'Борис', '', '', '', 'IN_PERSON', 0)",
        )
        execSQL(
            "INSERT INTO student_groups (id, name, note, subjectId, priceMinor, defaultMode, archived) " +
                "VALUES (7, '11 «А»', '', 1, 2000, 'IN_PERSON', 0)",
        )
        // A group lesson that was taught, with Борис corrected to cancelled afterwards — the state the
        // old shape expressed by cancelling one member's class, and the one this migration has to read.
        execSQL(
            "INSERT INTO classes (id, studentId, subjectId, startMillis, durationMinutes, priceMinor, " +
                "status, chargeMinor, groupId, mode, calendarEventId) VALUES " +
                "(1, 1, 1, 1900000000000, 90, 2000, 'COMPLETED', 3000, 7, 'IN_PERSON', 'evt-1'), " +
                "(2, 2, 1, 1900000000000, 90, 2000, 'CANCELLED', 0, 7, 'IN_PERSON', NULL)",
        )
        // And one student's own lesson, still to come.
        execSQL(
            "INSERT INTO classes (id, studentId, subjectId, startMillis, durationMinutes, priceMinor, " +
                "status, chargeMinor, groupId, mode, calendarEventId) VALUES " +
                "(3, 1, 1, 1900100000000, 60, 5000, 'SCHEDULED', 0, NULL, 'REMOTE', NULL)",
        )
    }

    private fun migrate(): SupportSQLiteDatabase {
        helper.createDatabase(name, 1).apply {
            seedV1()
            close()
        }
        return helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)
    }

    private fun SupportSQLiteDatabase.rows(sql: String): List<Map<String, String?>> =
        query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).associate { cursor.getColumnName(it) to cursor.getString(it) })
                }
            }
        }

    /** Three classes in two lessons: the group's two members are one lesson, the individual class its own. */
    @Test
    fun classesBecomeLessonsAndAttendance() {
        val db = migrate()

        val lessons = db.rows("SELECT * FROM lessons ORDER BY id")
        assertEquals(2, lessons.size)
        assertEquals(listOf("1", "3"), lessons.map { it["id"] })
        assertEquals(listOf("7", null), lessons.map { it["groupId"] })
        assertEquals(listOf("90", "60"), lessons.map { it["durationMinutes"] })
        assertEquals(listOf("1900000000000", "1900100000000"), lessons.map { it["startMillis"] })

        val attendance = db.rows("SELECT * FROM attendance ORDER BY id")
        assertEquals("every class keeps a row, and its id", listOf("1", "2", "3"), attendance.map { it["id"] })
        assertEquals("pointing at the lesson it was part of", listOf("1", "1", "3"), attendance.map { it["lessonId"] })
    }

    /**
     * The taught group lesson: **the lesson is taught, and Борис was absent from it.** One status and one
     * attendance where there used to be two statuses and no way to tell those two facts apart.
     */
    @Test
    fun aTaughtLessonWithACancelledMemberBecomesTaughtWithAnAbsentee() {
        val db = migrate()

        val lesson = db.rows("SELECT * FROM lessons WHERE id = 1").single()
        assertEquals(LessonStatus.COMPLETED.name, lesson["status"])

        val members = db.rows("SELECT * FROM attendance WHERE lessonId = 1 ORDER BY id")
        assertEquals(Attended.PRESENT.name, members[0]["state"])
        assertEquals(Attended.ABSENT.name, members[1]["state"])
    }

    /** A lesson still to come: nobody attended it, and nobody was absent from it either. */
    @Test
    fun aScheduledLessonsMembersAreMerelyExpected() {
        val db = migrate()

        assertEquals(LessonStatus.SCHEDULED.name, db.rows("SELECT * FROM lessons WHERE id = 3").single()["status"])
        assertEquals(
            Attended.EXPECTED.name,
            db.rows("SELECT * FROM attendance WHERE lessonId = 3").single()["state"],
        )
    }

    /**
     * **The money is carried across verbatim** — and still adds up to the same, which is the assertion the
     * tutor would make: the rate, the charge and the calendar event id all belong to the student's row and
     * are copied, not recomputed.
     */
    @Test
    fun ratesChargesAndCalendarEventsSurvive() {
        val db = migrate()

        val rows = db.rows("SELECT * FROM attendance ORDER BY id")
        assertEquals(listOf("2000", "2000", "5000"), rows.map { it["priceMinor"] })
        assertEquals(listOf("3000", "0", "0"), rows.map { it["chargeMinor"] })
        assertEquals(listOf("evt-1", null, null), rows.map { it["calendarEventId"] })
        assertEquals(
            "the one charge there was is the one charge there is",
            "3000",
            db.rows("SELECT SUM(chargeMinor) AS total FROM attendance").single()["total"],
        )
    }

    /** And the view reads them back as the classes they were, so every query above it is unchanged. */
    @Test
    fun theViewReadsThemBackAsClasses() {
        val db = migrate()

        val classes = db.rows("SELECT * FROM classes ORDER BY id")
        assertEquals(3, classes.size)
        assertEquals(listOf("1", "2", "1"), classes.map { it["studentId"] })
        assertEquals(
            "the lesson's status on every one of its classes",
            listOf(LessonStatus.COMPLETED.name, LessonStatus.COMPLETED.name, LessonStatus.SCHEDULED.name),
            classes.map { it["status"] },
        )
        assertEquals(listOf("90", "90", "60"), classes.map { it["durationMinutes"] })
        assertEquals(listOf("7", "7", null), classes.map { it["groupId"] })
    }
}
