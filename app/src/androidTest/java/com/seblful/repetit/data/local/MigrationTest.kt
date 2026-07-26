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

    /**
     * v6 fixes a group's subject on the group. Existing groups must come through with **no** subject
     * and an intact roster: their members may share none, so inferring one from lesson history would
     * silently narrow a roster the tutor never narrowed.
     *
     * `runMigrationsAndValidate` is the real assertion here — the column is added by `ALTER TABLE`
     * carrying a `REFERENCES`, which SQLite only permits because it defaults to NULL, and Room checks
     * the resulting foreign key and index against the entity. A hand-written index name that didn't
     * match `index_student_groups_subjectId` would fail here rather than on a tutor's phone.
     */
    @Test fun migrate5To6LeavesExistingGroupsWithoutASubject() {
        helper.createDatabase(testDb, 5).use { db ->
            db.execSQL("INSERT INTO students (id, name, phone, email, note, defaultMode, archived) VALUES (1, 'Аня', '', '', '', 'IN_PERSON', 0)")
            db.execSQL("INSERT INTO students (id, name, phone, email, note, defaultMode, archived) VALUES (2, 'Борис', '', '', '', 'IN_PERSON', 0)")
            db.execSQL("INSERT INTO subjects (id, name) VALUES (1, 'Алгебра')")
            db.execSQL("INSERT INTO student_groups (id, name, note, priceMinor, defaultMode, archived) VALUES (7, '11 «А»', 'вечер', 4000, 'REMOTE', 0)")
            db.execSQL("INSERT INTO group_members (groupId, studentId) VALUES (7, 1)")
            db.execSQL("INSERT INTO group_members (groupId, studentId) VALUES (7, 2)")
        }

        val db = helper.runMigrationsAndValidate(testDb, 6, true, Migrations.MIGRATION_5_6)

        db.query("SELECT * FROM student_groups").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("11 «А»", c.getString(c.getColumnIndexOrThrow("name")))
            assertEquals("the group's rate is untouched", 4000, c.getInt(c.getColumnIndexOrThrow("priceMinor")))
            assertTrue("no subject is invented", c.isNull(c.getColumnIndexOrThrow("subjectId")))
        }
        db.query("SELECT COUNT(*) FROM group_members WHERE groupId = 7").use { c ->
            c.moveToFirst()
            assertEquals("the roster survives untouched", 2, c.getInt(0))
        }
        // The new column really is usable as a foreign key, not just present.
        db.execSQL("UPDATE student_groups SET subjectId = 1 WHERE id = 7")
        db.query("SELECT subjectId FROM student_groups WHERE id = 7").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
    }

    /**
     * v7 starts keeping a rate history, and seeds every rate that already exists as the **start** of
     * its own — so an upgrading tutor opens a card and sees where each price stands rather than an
     * empty list that looks broken.
     *
     * The seeded rows must carry `fromMinor` and `atMillis` as **NULL**: the amount is known, but
     * neither a previous rate nor the day it was set is, and a 0 or an epoch date would read as a real
     * amount on a real day. Nothing else may be touched — the prices, the group and the money already
     * billed all have to come through untouched, since a migration that rewrote a charge would restate
     * earned income.
     */
    @Test fun migrate6To7SeedsExistingRatesAsTheStartOfTheirHistory() {
        helper.createDatabase(testDb, 6).use { db ->
            db.execSQL("INSERT INTO students (id, name, phone, email, note, defaultMode, archived) VALUES (1, 'Аня', '', '', '', 'IN_PERSON', 0)")
            db.execSQL("INSERT INTO students (id, name, phone, email, note, defaultMode, archived) VALUES (2, 'Борис', '', '', '', 'IN_PERSON', 0)")
            db.execSQL("INSERT INTO subjects (id, name) VALUES (1, 'Алгебра')")
            db.execSQL("INSERT INTO subjects (id, name) VALUES (2, 'Геометрия')")
            db.execSQL("INSERT INTO student_subjects (studentId, subjectId) VALUES (1, 1)")
            db.execSQL("INSERT INTO student_subjects (studentId, subjectId) VALUES (1, 2)")
            db.execSQL("INSERT INTO student_subjects (studentId, subjectId) VALUES (2, 1)")
            db.execSQL("INSERT INTO prices (studentId, subjectId, amountMinor) VALUES (1, 1, 3000)")
            db.execSQL("INSERT INTO prices (studentId, subjectId, amountMinor) VALUES (1, 2, 3500)")
            db.execSQL("INSERT INTO prices (studentId, subjectId, amountMinor) VALUES (2, 1, 4550)")
            db.execSQL(
                "INSERT INTO student_groups (id, name, note, priceMinor, subjectId, defaultMode, archived) " +
                    "VALUES (7, '11 «А»', 'вечер', 2000, 1, 'REMOTE', 0)",
            )
            // A group v5 left explicitly unpriced: 0 is what the column holds, so 0 is what is recorded.
            db.execSQL(
                "INSERT INTO student_groups (id, name, note, priceMinor, subjectId, defaultMode, archived) " +
                    "VALUES (8, 'Выпускники', '', 0, NULL, 'IN_PERSON', 0)",
            )
            db.execSQL("INSERT INTO group_members (groupId, studentId) VALUES (7, 1)")
            db.execSQL(
                "INSERT INTO classes (id, studentId, subjectId, startMillis, durationMinutes, priceMinor, " +
                    "status, chargeMinor, groupId, mode, calendarEventId) " +
                    "VALUES (1, 1, 1, 1900000000000, 90, 3000, 'COMPLETED', 4500, NULL, 'REMOTE', 'evt-1')",
            )
        }

        val db = helper.runMigrationsAndValidate(testDb, 7, true, Migrations.MIGRATION_6_7)

        // Three prices and two group rates, each seeded once as the start of its history.
        db.query("SELECT COUNT(*) FROM price_history").use { c ->
            c.moveToFirst()
            assertEquals(5, c.getInt(0))
        }
        db.query(
            "SELECT * FROM price_history WHERE studentId = 2 AND subjectId = 1",
        ).use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("the amount is carried over exactly", 4550, c.getInt(c.getColumnIndexOrThrow("toMinor")))
            assertTrue("no previous rate is invented", c.isNull(c.getColumnIndexOrThrow("fromMinor")))
            assertTrue("no date is invented", c.isNull(c.getColumnIndexOrThrow("atMillis")))
            assertTrue("a student's row names no group", c.isNull(c.getColumnIndexOrThrow("groupId")))
        }
        db.query("SELECT * FROM price_history WHERE groupId = 7").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(2000, c.getInt(c.getColumnIndexOrThrow("toMinor")))
            assertTrue("a group's row names no student", c.isNull(c.getColumnIndexOrThrow("studentId")))
            assertTrue("nor a subject", c.isNull(c.getColumnIndexOrThrow("subjectId")))
        }
        db.query("SELECT toMinor FROM price_history WHERE groupId = 8").use { c ->
            c.moveToFirst()
            assertEquals("an unpriced group records the 0 it actually holds", 0, c.getInt(0))
        }

        // Nothing else moved: the prices, the group and the billed lesson are exactly as they were.
        db.query("SELECT COUNT(*) FROM prices").use { c ->
            c.moveToFirst()
            assertEquals(3, c.getInt(0))
        }
        db.query("SELECT priceMinor, chargeMinor FROM classes WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals(3000, c.getInt(0))
            assertEquals("earned income is never restated", 4500, c.getInt(1))
        }
        db.query("SELECT priceMinor, subjectId FROM student_groups WHERE id = 7").use { c ->
            c.moveToFirst()
            assertEquals(2000, c.getInt(0))
            assertEquals(1, c.getInt(1))
        }

        // The log is usable as a log: a real change, dated, on top of the seeded row.
        db.execSQL(
            "INSERT INTO price_history (studentId, subjectId, groupId, fromMinor, toMinor, atMillis) " +
                "VALUES (2, 1, NULL, 4550, 5000, 1900000000000)",
        )
        db.query(
            "SELECT toMinor FROM price_history WHERE studentId = 2 ORDER BY atMillis DESC, id DESC",
        ).use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals("the dated change reads first", 5000, c.getInt(0))
            c.moveToNext()
            assertEquals("the undated seed sorts last", 4550, c.getInt(0))
        }
    }

    /**
     * `price_history` cascades from all three of its parents (unlike `classes`, which restricts):
     * it is a log *about* a price, not income, so a subject that is still deletable — one with no
     * lessons — must take its rate log with it rather than leave rows describing a price for something
     * that no longer exists.
     */
    @Test fun rateHistoryIsCascadedAwayWithTheSubjectItPrices() {
        helper.createDatabase(testDb, 6).use { db ->
            db.execSQL("INSERT INTO students (id, name, phone, email, note, defaultMode, archived) VALUES (1, 'Аня', '', '', '', 'IN_PERSON', 0)")
            db.execSQL("INSERT INTO subjects (id, name) VALUES (1, 'Алгебра')")
            db.execSQL("INSERT INTO prices (studentId, subjectId, amountMinor) VALUES (1, 1, 3000)")
        }

        val db = helper.runMigrationsAndValidate(testDb, 7, true, Migrations.MIGRATION_6_7)
        db.execSQL("PRAGMA foreign_keys = ON")

        db.query("SELECT COUNT(*) FROM price_history").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }

        // The subject has no classes, so deleting it is legal — and takes the price and the log with it.
        db.execSQL("DELETE FROM subjects WHERE id = 1")

        db.query("SELECT COUNT(*) FROM price_history").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM prices").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }
}
