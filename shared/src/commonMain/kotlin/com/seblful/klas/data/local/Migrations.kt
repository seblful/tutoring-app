package com.seblful.klas.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.seblful.klas.data.settings.SettingsEntity

/**
 * Migrations from [AppDatabase.BASELINE_VERSION] through the current Room schema. Keep them aligned with
 * exported schemas and the migration rules in the database-and-backup guide.
 *
 * - Added-column SQL defaults must match the entity's `defaultValue`.
 * - Historical migrations recreate the matching frozen `classes` view when changing its projected columns;
 *   Room validates views but does not recreate them.
 * - Rebuild tables with Room's exported DDL, preserving IDs, foreign keys, and `sqlite_sequence`.
 * - Override `migrate(SQLiteConnection)` for the bundled SQLite driver. Instrumented tests verify each step
 *   against the exported schemas; `MigrationChainTest` checks that the chain is registered.
 */
object Migrations {

    /**
     * v5 → v6: the settings row learns which language the app is shown in.
     *
     * `'SYSTEM'` — follow the phone — is both the honest backfill and the right default, and matches
     * `SettingsEntity.language`'s `defaultValue`. It is not `'RUSSIAN'` even though Russian is the only
     * language any v5 install has ever displayed: the tutor never chose it, and stating a preference on
     * their behalf would outlive them changing their phone.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE settings ADD COLUMN language TEXT NOT NULL DEFAULT 'SYSTEM'")
        }
    }

    /**
     * v6 → v7 adds lesson notes and evening-digest settings. SQL defaults must match the entities;
     * [CLASSES_VIEW_V7] preserves the view definition for this schema version.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE lessons ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE settings ADD COLUMN unfinishedDigestEnabled INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE settings ADD COLUMN unfinishedMinuteOfDay INTEGER NOT NULL DEFAULT 1260")
            connection.execSQL("DROP VIEW IF EXISTS `classes`")
            connection.execSQL("CREATE VIEW `classes` AS $CLASSES_VIEW_V7")
        }
    }

    private const val CLASSES_VIEW_V7: String =
        "SELECT a.id AS id, a.lessonId AS lessonId, a.studentId AS studentId, " +
            "a.state AS state, a.priceMinor AS priceMinor, a.chargeMinor AS chargeMinor, " +
            "a.calendarEventId AS calendarEventId, l.subjectId AS subjectId, " +
            "l.startMillis AS startMillis, l.durationMinutes AS durationMinutes, " +
            "l.status AS status, l.groupId AS groupId, l.mode AS mode, l.notes AS notes " +
            "FROM attendance a JOIN lessons l ON l.id = a.lessonId"

    /**
     * v7 → v8 adds homework text and per-student results, both empty for existing rows. Recreate the
     * matching [CLASSES_VIEW_V8] because the migration changes its projected columns.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE lessons ADD COLUMN homework TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE attendance ADD COLUMN homeworkResult TEXT")
            connection.execSQL("DROP VIEW IF EXISTS `classes`")
            connection.execSQL("CREATE VIEW `classes` AS $CLASSES_VIEW_V8")
        }
    }

    private const val CLASSES_VIEW_V8: String =
        "SELECT a.id AS id, a.lessonId AS lessonId, a.studentId AS studentId, " +
            "a.state AS state, a.priceMinor AS priceMinor, a.chargeMinor AS chargeMinor, " +
            "a.calendarEventId AS calendarEventId, l.subjectId AS subjectId, " +
            "l.startMillis AS startMillis, l.durationMinutes AS durationMinutes, " +
            "l.status AS status, l.groupId AS groupId, l.mode AS mode, l.notes AS notes, " +
            "l.homework AS homework, a.homeworkResult AS homeworkResult " +
            "FROM attendance a JOIN lessons l ON l.id = a.lessonId"

    /**
     * v8 → v9 moves homework into its own table. Each non-cancelled lesson produces one assignment for
     * its student or group; it is marked done when it has a homework result. The old note and homework
     * columns are cleared, and the matching view is recreated.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `homework` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`studentId` INTEGER, `groupId` INTEGER, `text` TEXT NOT NULL, " +
                    "`givenAtMillis` INTEGER NOT NULL, `doneAtMillis` INTEGER, " +
                    "FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`groupId`) REFERENCES `student_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_homework_studentId` ON `homework` (`studentId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_homework_groupId` ON `homework` (`groupId`)")
            connection.execSQL(
                "INSERT INTO homework (studentId, groupId, text, givenAtMillis, doneAtMillis) " +
                    "SELECT a.studentId, NULL, l.homework, l.startMillis, " +
                    "CASE WHEN a.homeworkResult IS NULL THEN NULL ELSE l.startMillis END " +
                    "FROM lessons l JOIN attendance a ON a.lessonId = l.id " +
                    "WHERE l.groupId IS NULL AND l.homework != '' AND l.status != 'CANCELLED' " +
                    "ORDER BY l.startMillis",
            )
            connection.execSQL(
                "INSERT INTO homework (studentId, groupId, text, givenAtMillis, doneAtMillis) " +
                    "SELECT NULL, l.groupId, l.homework, l.startMillis, " +
                    "CASE WHEN EXISTS (SELECT 1 FROM attendance a WHERE a.lessonId = l.id " +
                    "AND a.homeworkResult IS NULL) THEN NULL ELSE l.startMillis END " +
                    "FROM lessons l " +
                    "WHERE l.groupId IS NOT NULL AND l.homework != '' AND l.status != 'CANCELLED' " +
                    "ORDER BY l.startMillis",
            )
            connection.execSQL("UPDATE lessons SET notes = '', homework = '' WHERE notes != '' OR homework != ''")
            connection.execSQL("UPDATE attendance SET homeworkResult = NULL WHERE homeworkResult IS NOT NULL")
            connection.execSQL("DROP VIEW IF EXISTS `classes`")
            connection.execSQL("CREATE VIEW `classes` AS $CLASSES_VIEW_V9")
        }
    }

    private const val CLASSES_VIEW_V9: String =
        "SELECT a.id AS id, a.lessonId AS lessonId, a.studentId AS studentId, " +
            "a.state AS state, a.priceMinor AS priceMinor, a.chargeMinor AS chargeMinor, " +
            "a.calendarEventId AS calendarEventId, l.subjectId AS subjectId, " +
            "l.startMillis AS startMillis, l.durationMinutes AS durationMinutes, " +
            "l.status AS status, l.groupId AS groupId, l.mode AS mode " +
            "FROM attendance a JOIN lessons l ON l.id = a.lessonId"

    /**
     * v9 → v10 removes cleared legacy columns and replaces the `classes` view with DAO queries. Rebuild
     * tables using [rebuild], preserving IDs and `sqlite_sequence`. Drop the old attendance table before
     * the old lessons table: dropping a referenced lesson could cascade-delete attendance rows. Run
     * [requireNoOrphans] before committing the migration.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("PRAGMA defer_foreign_keys = ON")
            connection.execSQL("DROP VIEW IF EXISTS `classes`")
            // Parent before child: `attendance` is refilled into a table pointing at the new `lessons`.
            V10_REBUILDS.forEach { rebuild(connection, it) }
            // Child before parent: nothing may refer to `lessons_v9` by the time it is dropped.
            V10_REBUILDS.asReversed().forEach { connection.execSQL("DROP TABLE `${it.table}_v9`") }
            V10_REBUILDS.forEach { table -> table.indices.forEach(connection::execSQL) }
            requireNoOrphans(connection, "lessons", "attendance")
        }
    }

    private class Rebuilt(
        val table: String,
        val createSql: String,
        val columns: List<String>,
        val indices: List<String>,
    )

    private val V10_REBUILDS = listOf(
        Rebuilt(
            table = "lessons",
            createSql = "CREATE TABLE IF NOT EXISTS `lessons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`subjectId` INTEGER NOT NULL, `startMillis` INTEGER NOT NULL, `durationMinutes` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, `groupId` INTEGER, `mode` TEXT NOT NULL, " +
                "FOREIGN KEY(`subjectId`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`groupId`) REFERENCES `student_groups`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            columns = listOf("id", "subjectId", "startMillis", "durationMinutes", "status", "groupId", "mode"),
            indices = listOf(
                "CREATE INDEX IF NOT EXISTS `index_lessons_subjectId` ON `lessons` (`subjectId`)",
                "CREATE INDEX IF NOT EXISTS `index_lessons_startMillis` ON `lessons` (`startMillis`)",
                "CREATE INDEX IF NOT EXISTS `index_lessons_status` ON `lessons` (`status`)",
                "CREATE INDEX IF NOT EXISTS `index_lessons_groupId` ON `lessons` (`groupId`)",
            ),
        ),
        Rebuilt(
            table = "attendance",
            createSql = "CREATE TABLE IF NOT EXISTS `attendance` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`lessonId` INTEGER NOT NULL, `studentId` INTEGER NOT NULL, `state` TEXT NOT NULL, " +
                "`priceMinor` INTEGER NOT NULL, `chargeMinor` INTEGER NOT NULL, `calendarEventId` TEXT, " +
                "FOREIGN KEY(`lessonId`) REFERENCES `lessons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            columns = listOf("id", "lessonId", "studentId", "state", "priceMinor", "chargeMinor", "calendarEventId"),
            indices = listOf(
                "CREATE INDEX IF NOT EXISTS `index_attendance_lessonId` ON `attendance` (`lessonId`)",
                "CREATE INDEX IF NOT EXISTS `index_attendance_studentId` ON `attendance` (`studentId`)",
                "CREATE INDEX IF NOT EXISTS `index_attendance_state` ON `attendance` (`state`)",
            ),
        ),
        Rebuilt(
            table = "settings",
            createSql = "CREATE TABLE IF NOT EXISTS `settings` (`id` INTEGER NOT NULL, " +
                "`notificationsEnabled` INTEGER NOT NULL, `calendarSyncEnabled` INTEGER NOT NULL, " +
                "`emailInvitesEnabled` INTEGER NOT NULL DEFAULT 1, `onboardingComplete` INTEGER NOT NULL, " +
                "`reminderLeadMinutes` INTEGER NOT NULL, `dailyAgendaEnabled` INTEGER NOT NULL DEFAULT 0, " +
                "`agendaMinuteOfDay` INTEGER NOT NULL DEFAULT 540, `debtDigestEnabled` INTEGER NOT NULL DEFAULT 0, " +
                "`debtDayOfWeek` INTEGER NOT NULL DEFAULT 1, `debtMinuteOfDay` INTEGER NOT NULL DEFAULT 540, " +
                "`unfinishedDigestEnabled` INTEGER NOT NULL DEFAULT 0, " +
                "`unfinishedMinuteOfDay` INTEGER NOT NULL DEFAULT 1260, `autoCompleteEnabled` INTEGER NOT NULL, " +
                "`themeMode` TEXT NOT NULL, `dynamicColorEnabled` INTEGER NOT NULL DEFAULT 0, " +
                "`defaultLessonMinutes` INTEGER NOT NULL, `defaultGroupPriceMinor` INTEGER NOT NULL DEFAULT 0, " +
                "`currency` TEXT NOT NULL, `language` TEXT NOT NULL DEFAULT 'SYSTEM', " +
                "`lastExportMillis` INTEGER, PRIMARY KEY(`id`))",
            columns = listOf(
                "id", "notificationsEnabled", "calendarSyncEnabled", "emailInvitesEnabled", "onboardingComplete",
                "reminderLeadMinutes", "dailyAgendaEnabled", "agendaMinuteOfDay", "debtDigestEnabled",
                "debtDayOfWeek", "debtMinuteOfDay", "unfinishedDigestEnabled", "unfinishedMinuteOfDay",
                "autoCompleteEnabled", "themeMode", "dynamicColorEnabled", "defaultLessonMinutes",
                "defaultGroupPriceMinor", "currency", "language", "lastExportMillis",
            ),
            indices = emptyList(),
        ),
    )

    private fun rebuild(connection: SQLiteConnection, table: Rebuilt) {
        val old = "${table.table}_v9"
        val columns = table.columns.joinToString { "`$it`" }
        connection.execSQL("ALTER TABLE `${table.table}` RENAME TO `$old`")
        connection.execSQL(table.createSql)
        connection.execSQL("INSERT INTO `${table.table}` ($columns) SELECT $columns FROM `$old`")
        connection.execSQL("DELETE FROM sqlite_sequence WHERE name = '${table.table}'")
        connection.execSQL("UPDATE sqlite_sequence SET name = '${table.table}' WHERE name = '$old'")
    }

    private fun requireNoOrphans(connection: SQLiteConnection, vararg tables: String) {
        val orphans = tables.flatMap { table ->
            connection.prepare("PRAGMA foreign_key_check($table)").use { row ->
                buildList {
                    while (row.step()) add("${row.getText(0)} row ${row.getLong(1)} -> missing ${row.getText(2)}")
                }
            }
        }
        check(orphans.isEmpty()) { "v9 -> v10 left rows pointing at nothing: $orphans" }
    }

    /** v10 → v11 adds the privacy settings, every one starting closed — see `Privacy`. */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE settings ADD COLUMN lockScreenDetails INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE settings ADD COLUMN lockScreenWidgets INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE settings ADD COLUMN appLock INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE settings ADD COLUMN relockAfterMinutes INTEGER NOT NULL DEFAULT 1")
            connection.execSQL("ALTER TABLE settings ADD COLUMN hideInRecents INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v11 → v12 adds the weekly backup, off until the tutor picks a folder. */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE settings ADD COLUMN autoBackupFolder TEXT")
            connection.execSQL("ALTER TABLE settings ADD COLUMN autoBackupFailed INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v12 → v13 says how a payment was made; every earlier one has no method. */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE payments ADD COLUMN method TEXT")
        }
    }

    /**
     * The guide a new tutor gets. An install already through setup has found its way without it, so it starts
     * with every tip of this version seen and the first-steps card put away. The names are frozen here: a tip
     * added later is one these tutors have not seen either.
     */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE settings ADD COLUMN tipsSeen TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE settings ADD COLUMN firstStepsHidden INTEGER NOT NULL DEFAULT 0")
            connection.execSQL(
                "UPDATE settings SET tipsSeen = 'SCHEDULE_DATE,LESSON_SWIPE,LESSON_PICK,HOMEWORK_SWIPE', " +
                    "firstStepsHidden = 1 WHERE onboardingComplete = 1",
            )
        }
    }

    /** What a phone is to be given after a restore — nothing yet on any phone that upgrades. */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE settings ADD COLUMN phoneSetup TEXT NOT NULL DEFAULT ''")
        }
    }

    /** Everything above, in the order Room should be handed it. See `di/DatabaseModule`. */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
    )
}
