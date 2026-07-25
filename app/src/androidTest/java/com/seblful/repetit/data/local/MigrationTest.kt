package com.seblful.repetit.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every migration must carry a published database forward without losing a single lesson or
 * preference — `runMigrationsAndValidate` additionally proves the resulting schema is exactly what
 * Room expects from the entities (columns, defaults, foreign keys and indices), which is the part a
 * hand-written `CREATE TABLE` or `ALTER TABLE` most easily gets wrong.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test fun migrate1To2KeepsClassesAndAddsGroups() {
        helper.createDatabase(testDb, 1).use { db ->
            db.execSQL("INSERT INTO subjects (id, name) VALUES (1, 'Алгебра')")
            db.execSQL(
                "INSERT INTO students (id, name, phone, email, note, defaultMode, archived) " +
                    "VALUES (1, 'Аня', '+375291112233', '', 'ОГЭ', 'IN_PERSON', 0)",
            )
            db.execSQL("INSERT INTO prices (studentId, subjectId, amountMinor) VALUES (1, 1, 3000)")
            db.execSQL("INSERT INTO student_subjects (studentId, subjectId) VALUES (1, 1)")
            // A completed lesson: recognized income that the migration must not touch. Its
            // `recurringGroupId` is the column v2 retires.
            db.execSQL(
                "INSERT INTO classes (id, studentId, subjectId, startMillis, durationMinutes, " +
                    "priceMinor, status, chargeMinor, recurringGroupId, mode, calendarEventId) " +
                    "VALUES (1, 1, 1, 1900000000000, 90, 3000, 'COMPLETED', 4500, 42, 'REMOTE', 'evt-1')",
            )
            db.execSQL("INSERT INTO payments (id, studentId, dateMillis, amountMinor) VALUES (1, 1, 1899000000000, 3000)")
        }

        val db = helper.runMigrationsAndValidate(testDb, 2, true, Migrations.MIGRATION_1_2)

        // The lesson survived, money intact, and is now an individual (group-less) class.
        db.query("SELECT * FROM classes").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(c.getColumnIndexOrThrow("id")))
            assertEquals(1900000000000L, c.getLong(c.getColumnIndexOrThrow("startMillis")))
            assertEquals(90, c.getInt(c.getColumnIndexOrThrow("durationMinutes")))
            assertEquals(3000L, c.getLong(c.getColumnIndexOrThrow("priceMinor")))
            assertEquals("COMPLETED", c.getString(c.getColumnIndexOrThrow("status")))
            assertEquals(4500L, c.getLong(c.getColumnIndexOrThrow("chargeMinor")))
            assertEquals("REMOTE", c.getString(c.getColumnIndexOrThrow("mode")))
            assertEquals("evt-1", c.getString(c.getColumnIndexOrThrow("calendarEventId")))
            assertEquals("the retired column is gone", -1, c.getColumnIndex("recurringGroupId"))
            assertTrue(c.isNull(c.getColumnIndexOrThrow("groupId")))
        }

        // Everything else came through untouched.
        db.query("SELECT COUNT(*) FROM payments").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM prices").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }

        // And the new roster tables are usable, including a class booked into a group.
        db.execSQL("INSERT INTO student_groups (id, name, note, defaultMode, archived) VALUES (7, '11 «А»', '', 'IN_PERSON', 0)")
        db.execSQL("INSERT INTO group_members (groupId, studentId) VALUES (7, 1)")
        db.execSQL(
            "INSERT INTO classes (studentId, subjectId, startMillis, durationMinutes, priceMinor, " +
                "status, chargeMinor, groupId, mode) VALUES (1, 1, 1900100000000, 60, 3000, 'SCHEDULED', 0, 7, 'IN_PERSON')",
        )
        db.query("SELECT groupId FROM classes WHERE groupId IS NOT NULL").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(7L, c.getLong(0))
        }
        db.query("SELECT studentId FROM group_members WHERE groupId = 7").use { c ->
            c.moveToFirst()
            assertEquals(1L, c.getLong(0))
        }
    }

    /**
     * v3 splits "invite students by email" out of calendar sync. The tutor's existing preferences
     * must survive, and the new column must arrive **on** — an upgrade that silently stopped
     * inviting students would withdraw invitations they are already relying on. (A fresh install
     * starts off instead; that default is the entity's, not this migration's.)
     */
    @Test fun migrate2To3KeepsSettingsAndDefaultsEmailInvitesOn() {
        helper.createDatabase(testDb, 2).use { db ->
            db.execSQL(
                "INSERT INTO settings (id, notificationsEnabled, calendarSyncEnabled, onboardingComplete, " +
                    "reminderLeadMinutes, autoCompleteEnabled, themeMode, defaultLessonMinutes, currency) " +
                    "VALUES (0, 1, 1, 1, 30, 1, 'DARK', 90, 'RUB')",
            )
        }

        val db = helper.runMigrationsAndValidate(testDb, 3, true, Migrations.MIGRATION_2_3)

        db.query("SELECT * FROM settings").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("invites default on", 1, c.getInt(c.getColumnIndexOrThrow("emailInvitesEnabled")))
            // Everything the tutor had chosen is untouched.
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow("calendarSyncEnabled")))
            assertEquals(30, c.getInt(c.getColumnIndexOrThrow("reminderLeadMinutes")))
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow("autoCompleteEnabled")))
            assertEquals("DARK", c.getString(c.getColumnIndexOrThrow("themeMode")))
            assertEquals(90, c.getInt(c.getColumnIndexOrThrow("defaultLessonMinutes")))
            assertEquals("RUB", c.getString(c.getColumnIndexOrThrow("currency")))
        }
    }

    /**
     * v4 gives «Группы» its own tab. The tutor's existing preferences — including the v3
     * email-invites switch, which a tutor may deliberately have turned off — must survive, and the
     * new column must arrive **on** so an upgrading install keeps the tab it just gained.
     */
    @Test fun migrate3To4KeepsSettingsAndShowsTheGroupsTab() {
        helper.createDatabase(testDb, 3).use { db ->
            db.execSQL(
                "INSERT INTO settings (id, notificationsEnabled, calendarSyncEnabled, emailInvitesEnabled, " +
                    "onboardingComplete, reminderLeadMinutes, autoCompleteEnabled, themeMode, " +
                    "defaultLessonMinutes, currency) " +
                    "VALUES (0, 1, 1, 0, 1, 30, 1, 'DARK', 45, 'RUB')",
            )
        }

        val db = helper.runMigrationsAndValidate(testDb, 4, true, Migrations.MIGRATION_3_4)

        db.query("SELECT * FROM settings").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            // The tutor's existing choices are untouched…
            assertEquals(30, c.getInt(c.getColumnIndexOrThrow("reminderLeadMinutes")))
            assertEquals("DARK", c.getString(c.getColumnIndexOrThrow("themeMode")))
            assertEquals("RUB", c.getString(c.getColumnIndexOrThrow("currency")))
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow("onboardingComplete")))
            assertEquals(45, c.getInt(c.getColumnIndexOrThrow("defaultLessonMinutes")))
            assertEquals("the v3 switch survives", 0, c.getInt(c.getColumnIndexOrThrow("emailInvitesEnabled")))
            // …and they keep the tab they just gained.
            assertEquals(1, c.getInt(c.getColumnIndexOrThrow("showGroupsTab")))
        }
    }

    /**
     * v5 gives a group its own hourly rate. The roster and, crucially, the **money already billed** on
     * its lessons must come through untouched: each class snapshotted the rate it was billed at, so an
     * upgrade never restates history. The group itself arrives unpriced (0) — its members were billed
     * individually, and no single number is the truth about that — for the tutor to price.
     */
    @Test fun migrate4To5KeepsBilledLessonsAndLeavesGroupsUnpriced() {
        helper.createDatabase(testDb, 4).use { db ->
            db.execSQL("INSERT INTO students (id, name, phone, email, note, defaultMode, archived) VALUES (1, 'Аня', '', '', '', 'IN_PERSON', 0)")
            db.execSQL("INSERT INTO students (id, name, phone, email, note, defaultMode, archived) VALUES (2, 'Борис', '', '', '', 'IN_PERSON', 0)")
            db.execSQL("INSERT INTO subjects (id, name) VALUES (1, 'Алгебра')")
            db.execSQL("INSERT INTO student_groups (id, name, note, defaultMode, archived) VALUES (7, '11 «А»', 'вечер', 'REMOTE', 0)")
            db.execSQL("INSERT INTO group_members (groupId, studentId) VALUES (7, 1)")
            db.execSQL("INSERT INTO group_members (groupId, studentId) VALUES (7, 2)")
            // One group lesson whose two members were billed their own, different rates.
            db.execSQL(
                "INSERT INTO classes (id, studentId, subjectId, startMillis, durationMinutes, priceMinor, " +
                    "status, chargeMinor, groupId, mode, calendarEventId) " +
                    "VALUES (1, 1, 1, 1900000000000, 60, 3000, 'COMPLETED', 3000, 7, 'IN_PERSON', NULL)",
            )
            db.execSQL(
                "INSERT INTO classes (id, studentId, subjectId, startMillis, durationMinutes, priceMinor, " +
                    "status, chargeMinor, groupId, mode, calendarEventId) " +
                    "VALUES (2, 2, 1, 1900000000000, 60, 2500, 'COMPLETED', 2500, 7, 'IN_PERSON', NULL)",
            )
            db.execSQL(
                "INSERT INTO settings (id, notificationsEnabled, calendarSyncEnabled, emailInvitesEnabled, " +
                    "onboardingComplete, reminderLeadMinutes, autoCompleteEnabled, themeMode, " +
                    "defaultLessonMinutes, currency, showGroupsTab) " +
                    "VALUES (0, 1, 1, 0, 1, 30, 1, 'DARK', 45, 'RUB', 0)",
            )
        }

        val db = helper.runMigrationsAndValidate(testDb, 5, true, Migrations.MIGRATION_4_5)

        db.query("SELECT * FROM student_groups").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("11 «А»", c.getString(c.getColumnIndexOrThrow("name")))
            assertEquals("вечер", c.getString(c.getColumnIndexOrThrow("note")))
            assertEquals("REMOTE", c.getString(c.getColumnIndexOrThrow("defaultMode")))
            assertEquals("left for the tutor to price, not guessed", 0, c.getInt(c.getColumnIndexOrThrow("priceMinor")))
        }
        db.query("SELECT COUNT(*) FROM group_members WHERE groupId = 7").use { c ->
            c.moveToFirst()
            assertEquals("the roster survives", 2, c.getInt(0))
        }
        db.query("SELECT priceMinor, chargeMinor FROM classes ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals(3000, c.getInt(0))
            assertEquals("earned income is never restated", 3000, c.getInt(1))
            c.moveToNext()
            assertEquals(2500, c.getInt(0))
            assertEquals(2500, c.getInt(1))
        }
        db.query("SELECT * FROM settings").use { c ->
            c.moveToFirst()
            // The tutor's existing choices are untouched, and the new default arrives unset.
            assertEquals("DARK", c.getString(c.getColumnIndexOrThrow("themeMode")))
            assertEquals(45, c.getInt(c.getColumnIndexOrThrow("defaultLessonMinutes")))
            assertEquals(0, c.getInt(c.getColumnIndexOrThrow("showGroupsTab")))
            assertEquals(0, c.getInt(c.getColumnIndexOrThrow("defaultGroupPriceMinor")))
        }
    }
}
