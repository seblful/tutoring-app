package com.seblful.klas.data.local

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.data.lessons.ClassRow
import com.seblful.klas.data.settings.SettingsEntity
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.ThemeChoice
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchemaCleanupMigrationTest {

    @get:Rule
    val helper = migrationTestHelper(TEST_DB)

    private fun SQLiteConnection.seedV9() {
        execSQL("INSERT INTO subjects (id, name) VALUES (1, 'Алгебра'), (2, 'Физика')")
        execSQL(
            "INSERT INTO students (id, name, phone, email, note, defaultMode, archived) VALUES " +
                "(1, 'Аня', '+375291112233', 'anya@example.com', 'ОГЭ', 'REMOTE', 0), " +
                "(2, 'Борис', '', '', '', 'IN_PERSON', 0), " +
                "(3, 'Вера', '', '', 'выпустилась', 'IN_PERSON', 1)",
        )
        execSQL("INSERT INTO student_subjects (studentId, subjectId) VALUES (1, 1), (1, 2), (2, 1), (3, 1)")
        execSQL(
            "INSERT INTO prices (studentId, subjectId, amountMinor) VALUES (1, 1, 3000), (1, " +
                "2, 3500), (2, 1, 2800), (3, 1, 2500)",
        )
        execSQL(
            "INSERT INTO student_groups (id, name, note, subjectId, priceMinor, defaultMode, archived) " +
                "VALUES (7, '9 «Б»', 'вечер', 1, 2000, 'IN_PERSON', 0)",
        )
        execSQL("INSERT INTO group_members (groupId, studentId) VALUES (7, 1), (7, 2)")
        execSQL(
            "INSERT INTO price_history (id, studentId, subjectId, groupId, fromMinor, toMinor, atMillis) VALUES " +
                "(1, 1, 1, NULL, NULL, 2500, NULL), " +
                "(2, 1, 1, NULL, 2500, 3000, 1700000000000), " +
                "(3, NULL, NULL, 7, NULL, 2000, 1700100000000)",
        )
        execSQL(
            "INSERT INTO lessons (id, subjectId, startMillis, durationMinutes, status, " +
                "groupId, mode, notes, homework) " +
                "VALUES " +
                "(10, 1, 1750000000000, 60, 'COMPLETED', NULL, 'REMOTE', '', ''), " +
                "(11, 2, 1750100000000, 90, 'COMPLETED', NULL, 'IN_PERSON', '', ''), " +
                "(12, 1, 1750200000000, 60, 'CANCELLED', NULL, 'IN_PERSON', '', ''), " +
                "(13, 1, 1760000000000, 60, 'SCHEDULED', NULL, 'IN_PERSON', '', ''), " +
                "(14, 1, 1740000000000, 45, 'COMPLETED', NULL, 'IN_PERSON', '', ''), " +
                "(20, 1, 1750300000000, 90, 'COMPLETED', 7, 'IN_PERSON', '', ''), " +
                "(21, 1, 1760100000000, 90, 'SCHEDULED', 7, 'REMOTE', '', ''), " +
                "(99, 1, 1760200000000, 60, 'SCHEDULED', NULL, 'IN_PERSON', '', '')",
        )
        execSQL(
            "INSERT INTO attendance (id, lessonId, studentId, state, priceMinor, chargeMinor, calendarEventId, " +
                "homeworkResult) VALUES " +
                "(100, 10, 1, 'PRESENT', 3000, 3000, 'evt-100', NULL), " +
                "(101, 11, 1, 'ABSENT_BILLED', 3500, 5250, NULL, NULL), " +
                "(102, 12, 2, 'EXPECTED', 2800, 0, NULL, NULL), " +
                "(103, 13, 2, 'EXPECTED', 2800, 0, 'evt-103', NULL), " +
                "(104, 14, 3, 'PRESENT', 2500, 1875, NULL, NULL), " +
                "(200, 20, 1, 'PRESENT', 2000, 3000, NULL, NULL), " +
                "(201, 20, 2, 'ABSENT', 2000, 0, NULL, NULL), " +
                "(210, 21, 1, 'EXPECTED', 2000, 0, 'evt-210', NULL), " +
                "(211, 21, 2, 'EXPECTED', 2000, 0, 'evt-211', NULL), " +
                "(999, 99, 2, 'EXPECTED', 2800, 0, NULL, NULL)",
        )
        execSQL("DELETE FROM attendance WHERE id = 999")
        execSQL("DELETE FROM lessons WHERE id = 99")
        execSQL(
            "INSERT INTO payments (id, studentId, dateMillis, amountMinor) VALUES " +
                "(1, 1, 1750050000000, 6000), (2, 2, 1750060000000, 2800), (3, 3, 1740100000000, 1875)",
        )
        execSQL(
            "INSERT INTO homework (id, studentId, groupId, text, givenAtMillis, doneAtMillis) VALUES " +
                "(1, 1, NULL, '§12, упр. 4–7', 1750000000000, 1750100000000), " +
                "(2, NULL, 7, 'Задачи 31–35', 1750300000000, NULL), " +
                "(3, 2, NULL, 'Повторить', 1750200000000, NULL)",
        )
        execSQL(
            """
            INSERT INTO settings (
                id, notificationsEnabled, calendarSyncEnabled, emailInvitesEnabled, onboardingComplete,
                reminderLeadMinutes, dailyAgendaEnabled, agendaMinuteOfDay, debtDigestEnabled, debtDayOfWeek,
                debtMinuteOfDay, unfinishedDigestEnabled, unfinishedMinuteOfDay, autoCompleteEnabled, themeMode,
                dynamicColorEnabled, defaultLessonMinutes, defaultGroupPriceMinor, currency, language,
                showGroupsTab, lastExportMillis
            ) VALUES (
                0, 0, 1, 1, 1, 45, 1, 480, 1, 5, 1080, 1, 1320, 1, 'DARK', 1, 90, 2000, 'RUB', 'ENGLISH',
                0, 1750400000000
            )
            """.trimIndent(),
        )
    }

    private fun SQLiteConnection.tables(): Set<String> =
        rows("SELECT name FROM sqlite_master WHERE type = 'table'").mapTo(HashSet()) { it.getValue("name") as String } -
            setOf("android_metadata", "sqlite_sequence", "room_master_table")

    private fun SQLiteConnection.everything(): Map<String, List<Map<String, Any?>>> =
        tables().associateWith { table -> rows("SELECT * FROM `$table`").sortedBy { it.toString() } }

    private fun SQLiteConnection.orphans(): List<String> =
        rows("PRAGMA foreign_key_check").map { "${it["table"]} row ${it["rowid"]} -> ${it["parent"]}" }

    private fun Map<String, List<Map<String, Any?>>>.withoutDroppedColumns() = mapValues { (table, rows) ->
        rows.map { row -> row - DROPPED.getOrDefault(table, emptySet()) }.sortedBy { it.toString() }
    }

    @Test
    fun everyRowAndValueSurvivesTheUpgrade() {
        val before = helper.createDatabase(9).use { db ->
            db.seedV9()
            assertEquals("the seed itself is consistent", emptyList<String>(), db.orphans())
            db.everything()
        }

        val migrated = helper.runMigrationsAndValidate(10, listOf(Migrations.MIGRATION_9_10))

        assertEquals(before.withoutDroppedColumns(), migrated.everything())
        assertEquals(7, before.getValue("lessons").size)
        assertEquals(9, before.getValue("attendance").size)
        assertEquals(1, before.getValue("settings").size)
    }

    @Test
    fun theDeadColumnsAndTheViewAreGone() {
        helper.createDatabase(9).use { it.seedV9() }

        val migrated = helper.runMigrationsAndValidate(10, listOf(Migrations.MIGRATION_9_10))

        for ((table, dropped) in DROPPED) {
            val columns = migrated.prepare("SELECT * FROM `$table` LIMIT 0").use { it.getColumnNames().toSet() }
            assertEquals(
                "`$table` still has ${columns intersect dropped}", emptySet<String>(),
                columns intersect dropped,
            )
        }
        assertEquals(0L, migrated.long("SELECT count(*) FROM sqlite_master WHERE type = 'view'"))
    }

    @Test
    fun noRowPointsAtNothingAfterTheUpgrade() {
        helper.createDatabase(9).use { it.seedV9() }

        val migrated = helper.runMigrationsAndValidate(10, listOf(Migrations.MIGRATION_9_10))

        assertEquals(emptyList<String>(), migrated.orphans())
    }

    @Test
    fun withForeignKeysOnNothingCascades() {
        helper.createDatabase(9).use { db ->
            db.seedV9()
            val before = db.everything()
            db.execSQL("PRAGMA foreign_keys = ON")
            assertEquals("foreign keys are really on", 1L, db.long("PRAGMA foreign_keys"))

            db.execSQL("BEGIN IMMEDIATE TRANSACTION")
            Migrations.MIGRATION_9_10.migrate(db)
            db.execSQL("COMMIT")

            assertEquals(before.withoutDroppedColumns(), db.everything())
            assertEquals(emptyList<String>(), db.orphans())
        }
    }

    @Test
    fun aDeletedIdIsNotReused() {
        helper.createDatabase(9).use { it.seedV9() }

        val migrated = helper.runMigrationsAndValidate(10, listOf(Migrations.MIGRATION_9_10))
        migrated.execSQL(
            "INSERT INTO lessons (subjectId, startMillis, durationMinutes, status, groupId, mode) " +
                "VALUES (1, 1770000000000, 60, 'SCHEDULED', NULL, 'IN_PERSON')",
        )
        migrated.execSQL(
            "INSERT INTO attendance (lessonId, studentId, state, priceMinor, chargeMinor, calendarEventId) " +
                "VALUES (100, 1, 'EXPECTED', 3000, 0, NULL)",
        )

        assertEquals(
            "the next lesson id is past 99, which was used",
            100L,
            migrated.long("SELECT max(id) FROM lessons"),
        )
        assertEquals("and the next class id past 999", 1000L, migrated.long("SELECT max(id) FROM attendance"))
    }

    @Test
    fun anEmptyDatabaseStillValidates() {
        helper.createDatabase(9).close()

        val migrated = helper.runMigrationsAndValidate(10, listOf(Migrations.MIGRATION_9_10))

        assertEquals(0L, migrated.long("SELECT count(*) FROM lessons"))
        assertEquals(0L, migrated.long("SELECT count(*) FROM settings"))
    }

    @Test
    fun roomOpensTheUpgradedDatabaseAndReadsItThroughTheDaos() = runTest {
        helper.createDatabase(9).use { it.seedV9() }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            val classes = db.classDao().getAll()
            assertEquals(listOf(100L, 101, 102, 103, 104, 200, 201, 210, 211), classes.map { it.id }.sorted())
            assertEquals(
                ClassRow(
                    id = 101, lessonId = 11, studentId = 1, subjectId = 2, startMillis = 1750100000000,
                    durationMinutes = 90, priceMinor = 3500, status = LessonStatus.COMPLETED,
                    state = Attended.ABSENT_BILLED, chargeMinor = 5250, groupId = null, mode = ClassMode.IN_PERSON,
                ),
                db.classDao().getById(101),
            )
            assertEquals(listOf(200L, 201L), db.classDao().getSession(20).map { it.id })
            assertEquals(listOf(103L, 211L), db.classDao().scheduledForStudent(2).map { it.id }.sorted())

            val settings = db.settingsDao().get()
            assertEquals(
                SettingsEntity(
                    notificationsEnabled = false, calendarSyncEnabled = true, emailInvitesEnabled = true,
                    onboardingComplete = true, reminderLeadMinutes = 45, dailyAgendaEnabled = true,
                    agendaMinuteOfDay = 480, debtDigestEnabled = true, debtDayOfWeek = 5, debtMinuteOfDay = 1080,
                    unfinishedDigestEnabled = true, unfinishedMinuteOfDay = 1320, autoCompleteEnabled = true,
                    themeMode = ThemeChoice.DARK, dynamicColorEnabled = true, defaultLessonMinutes = 90,
                    defaultGroupPriceMinor = 2000, currency = Currency.RUB, language = AppLanguage.ENGLISH,
                    lastExportMillis = 1750400000000,
                    // An onboarded tutor upgrades past the first-run guide (MIGRATION_13_14).
                    tipsSeen = "SCHEDULE_DATE,LESSON_SWIPE,LESSON_PICK,HOMEWORK_SWIPE", firstStepsHidden = true,
                ),
                settings,
            )

            val snapshot = db.snapshotDao().read()
            assertEquals(7, snapshot.lessons.size)
            assertEquals(9, snapshot.attendance.size)
            assertEquals(3, snapshot.payments.size)
            assertEquals(3, snapshot.homework.size)
            assertEquals(3, snapshot.rateHistory.size)
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-schema-cleanup-klas.db"

        /** What v10 drops, by table — and nothing else may differ. */
        val DROPPED = mapOf(
            "lessons" to setOf("notes", "homework"),
            "attendance" to setOf("homeworkResult"),
            "settings" to setOf("showGroupsTab"),
        )
    }
}
