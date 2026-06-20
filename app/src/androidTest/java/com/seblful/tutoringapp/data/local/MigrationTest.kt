package com.seblful.tutoringapp.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.tutoringapp.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Room migrations against the exported schema JSONs (`app/schemas`, shipped as
 * androidTest assets). [MigrationTestHelper.runMigrationsAndValidate] fails if the post-migration
 * tables don't match the bundled schema for the target version, so this is what actually proves the
 * hand-written SQL in [DatabaseModule.MIGRATION_4_5] reproduces Room's expected schema exactly.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    /** Seeds a v4 database with one student, subject, a completed class and a payment. */
    private fun SupportSQLiteDatabase.seedV4() {
        execSQL(
            "INSERT INTO students (id, name, phone, email, note, defaultMode, archived) " +
                "VALUES (1, 'Иван', '', '', '', 'IN_PERSON', 0)",
        )
        execSQL("INSERT INTO subjects (id, name) VALUES (1, 'Математика')")
        execSQL(
            "INSERT INTO classes (id, studentId, subjectId, startMillis, durationMinutes, priceMinor, " +
                "status, chargeMinor, recurringGroupId, mode, calendarEventId) " +
                "VALUES (1, 1, 1, 1000, 60, 5000, 'COMPLETED', 5000, NULL, 'IN_PERSON', NULL)",
        )
        execSQL("INSERT INTO payments (id, studentId, dateMillis, amountMinor) VALUES (1, 1, 2000, 3000)")
    }

    @Test
    fun migrate4To5_preservesIncomeAndPayments_andMatchesSchema() {
        helper.createDatabase(TEST_DB, 4).apply {
            seedV4()
            close()
        }

        // Throws if the migrated schema doesn't match the bundled v5 schema (5.json).
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, DatabaseModule.MIGRATION_4_5)

        db.query("SELECT studentId, subjectId, chargeMinor, status FROM classes WHERE id = 1").use { c ->
            assertTrue("the completed class must survive the migration", c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals(1L, c.getLong(1))
            assertEquals(5000L, c.getLong(2)) // recognized income preserved
            assertEquals("COMPLETED", c.getString(3))
        }
        db.query("SELECT amountMinor FROM payments WHERE id = 1").use { p ->
            assertTrue("the payment must survive the migration", p.moveToFirst())
            assertEquals(3000L, p.getLong(0))
        }
    }

    @Test
    fun migrate4To5_makesDeleteOfAReferencedParentFail() {
        helper.createDatabase(TEST_DB, 4).apply {
            seedV4()
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, DatabaseModule.MIGRATION_4_5)
        db.execSQL("PRAGMA foreign_keys = ON")

        // After v5, RESTRICT must refuse to delete a student/subject that still has classes/payments,
        // instead of cascade-erasing earned income and history.
        assertThrows(Exception::class.java) { db.execSQL("DELETE FROM students WHERE id = 1") }
        assertThrows(Exception::class.java) { db.execSQL("DELETE FROM subjects WHERE id = 1") }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
