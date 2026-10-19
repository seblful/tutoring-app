package com.seblful.klas.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Every way **into** the current schema from a version some phone is still at.
 *
 * The chain starts at [AppDatabase.BASELINE_VERSION] — the oldest version any `klas.db` can be — and every
 * single step from there to the version `@Database` declares must be here, or Room finds no path and the
 * app either wipes the database (debug) or refuses to open it (release). `MigrationChainTest` fails the
 * build over a missing step, a stale exported schema, and this list not being handed to `addMigrations`.
 *
 * This file existed once before, as four steps from v1, and was deleted: no device had ever held any of the
 * versions it migrated (see [AppDatabase.BASELINE_VERSION] for that argument in full). What is here now is
 * not that. v5 **shipped**, in `0.3.0`, so this step runs on real phones holding real students.
 *
 * The discipline for the next one:
 *
 * - A column added here must carry the same `DEFAULT` its entity declares as `defaultValue`, or Room's
 *   schema validation fails the instant the upgrade completes — a crash on first launch after a Play
 *   update, which is the worst place to find out.
 * - The instrumented tests (`androidTest`) are what prove a step *works*, against the exported schema JSONs
 *   in `app/schemas/`. Those need a device; the unit test only proves the step is *there*.
 * - **A column is removed by rebuilding its table.** The bundled SQLite has `ALTER TABLE … DROP COLUMN`, but
 *   it refuses an indexed, foreign-key or primary-key column, and v10 was written for Android's own SQLite,
 *   which has none below Android 12. [MIGRATION_9_10] is how: set the old table aside, create the new one
 *   with Room's own DDL, copy every row by name, drop the old, recreate the indices, check the foreign keys.
 *   `MigrationChainTest` holds every table a step rebuilds to the exported schema of the version it ends at.
 * - **The schema has no views since v10**, and should not grow one: Room validates a view after a migration
 *   but never recreates it, so every step touching a column it projected had to rebuild it by hand, with
 *   that version's `SELECT` frozen here — [MIGRATION_6_7] is where that was learned, twice. The steps up to
 *   v9 still do it, and `MigrationChainTest` still holds the view each leaves to its version's schema.
 * - **A step overrides `migrate(SQLiteConnection)`**, never `migrate(SupportSQLiteDatabase)`. The database
 *   is opened through the bundled SQLite driver (`di/DatabaseModule`), and with a driver set Room calls only
 *   the connection overload; one written against the other throws on the phone it was meant to upgrade.
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
     * v6 → v7: a lesson learns to carry a note, and the settings row learns about the evening digest.
     *
     * Three `ADD COLUMN`s and no table rebuild, which is what keeps this safe on API 24 (see the class
     * KDoc). Every `DEFAULT` here has a twin `defaultValue` on the entity — `''`, `0` and `1260` — and
     * Room validates the pair the instant the upgrade finishes, so they are written next to each other
     * deliberately.
     *
     * The backfills are all "this install has never had the feature", which is the honest answer in each
     * case: a lesson taught before the app could hold a note has none, and a tutor who has never been
     * offered an evening digest has not asked for one. Note the `''` — an empty string literal *inside*
     * the SQL, not an empty SQL fragment, which is the way this particular default is easy to write
     * wrongly and have SQLite accept.
     *
     * **The `classes` view is recreated here, and that is not optional.** Room *validates* views after a
     * migration and never recreates them, so a view left projecting the old columns is compared against
     * the new schema and the upgrade dies with `Migration didn't properly handle: classes` — on first
     * launch after an update, with everything rolled back. This step shipped that way for one install:
     * `lessons.notes` was added to the view's shared `SELECT` and to the table, and the view on disk still had
     * thirteen columns.
     *
     * The discipline this adds to the list above: **a migration that changes any column the view projects
     * recreates the view.** `LessonNotesMigrationTest` reads a lesson back *through* `classes`, which is
     * what fails when this is forgotten.
     *
     * **And it recreates v7's view, not today's** ([CLASSES_VIEW_V7]). This step read the shared `SELECT`
     * until v8 added two columns to it, and from then on it built the v8 view onto a v7 schema —
     * `LessonNotesMigrationTest` failed on the first phone it ran on.
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

    /** The `classes` view as v7 declares it, frozen for [MIGRATION_6_7]; checked against `7.json`. */
    private const val CLASSES_VIEW_V7: String =
        "SELECT a.id AS id, a.lessonId AS lessonId, a.studentId AS studentId, " +
            "a.state AS state, a.priceMinor AS priceMinor, a.chargeMinor AS chargeMinor, " +
            "a.calendarEventId AS calendarEventId, l.subjectId AS subjectId, " +
            "l.startMillis AS startMillis, l.durationMinutes AS durationMinutes, " +
            "l.status AS status, l.groupId AS groupId, l.mode AS mode, l.notes AS notes " +
            "FROM attendance a JOIN lessons l ON l.id = a.lessonId"

    /**
     * v7 → v8: homework — the lesson learns what was set at it, and each student's row learns how they did.
     *
     * Two `ADD COLUMN`s, no rebuild. `lessons.homework` defaults to `''`, the twin of its entity's
     * `defaultValue`, and starts empty on every existing lesson rather than being copied out of the note:
     * the note was «Заметки и домашнее задание», and guessing which half is which would hand a tutor's
     * private remarks to the student the homework is now sent to. `attendance.homeworkResult` is nullable
     * with no `DEFAULT`, which is how Room declares it and what "not checked yet" is stored as.
     *
     * **Both columns are projected by the `classes` view, so it is recreated** — the rule [MIGRATION_6_7]
     * learned on a real phone. `HomeworkMigrationTest` reads a lesson back through the view. It builds v8's
     * view ([CLASSES_VIEW_V8]), frozen when v9 took the columns back out of the shared one.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE lessons ADD COLUMN homework TEXT NOT NULL DEFAULT ''")
            connection.execSQL("ALTER TABLE attendance ADD COLUMN homeworkResult TEXT")
            connection.execSQL("DROP VIEW IF EXISTS `classes`")
            connection.execSQL("CREATE VIEW `classes` AS $CLASSES_VIEW_V8")
        }
    }

    /** The `classes` view as v8 declares it, frozen for [MIGRATION_7_8]; checked against `8.json`. */
    private const val CLASSES_VIEW_V8: String =
        "SELECT a.id AS id, a.lessonId AS lessonId, a.studentId AS studentId, " +
            "a.state AS state, a.priceMinor AS priceMinor, a.chargeMinor AS chargeMinor, " +
            "a.calendarEventId AS calendarEventId, l.subjectId AS subjectId, " +
            "l.startMillis AS startMillis, l.durationMinutes AS durationMinutes, " +
            "l.status AS status, l.groupId AS groupId, l.mode AS mode, l.notes AS notes, " +
            "l.homework AS homework, a.homeworkResult AS homeworkResult " +
            "FROM attendance a JOIN lessons l ON l.id = a.lessonId"

    /**
     * v8 → v9: homework becomes its own table, and the lesson's note goes.
     *
     * v8 kept homework on the lesson it was set at, with a verdict per student; the tutor wanted it set
     * for a student (or a group) from anywhere, and only ever done or not. So:
     *
     * - **`homework` is created** exactly as Room declares [HomeworkEntity] — `HomeworkTableMigrationTest`
     *   validates it column for column.
     * - **Every lesson's homework moves into it**, one row per lesson: an individual lesson's for its one
     *   student, a group lesson's for the group. Given at the lesson's start, which attaches it to the
     *   assignee's next lesson after that one (by v9's rule — any lesson they are in, where v8 kept to the
     *   same subject and kind). Done if it had a verdict — any verdict, «частично» and «не сделано»
     *   included, because each meant "checked", and checked is what done means now; for a group, once
     *   every member had one. A cancelled lesson's is dropped, as v8 already ignored it.
     * - **The vestigial columns are emptied** — the note, v8's homework text and its verdicts. Left
     *   holding text, they would still ride every backup file (`BackupData` carries the rows whole) and
     *   read like data. [MIGRATION_9_10] drops them.
     * - **The view is recreated**, since it stops projecting all three.
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

    /**
     * The `classes` view as v9 declares it, frozen for [MIGRATION_8_9]; checked against `9.json`. The last
     * one: [MIGRATION_9_10] drops the view for good.
     */
    private const val CLASSES_VIEW_V9: String =
        "SELECT a.id AS id, a.lessonId AS lessonId, a.studentId AS studentId, " +
            "a.state AS state, a.priceMinor AS priceMinor, a.chargeMinor AS chargeMinor, " +
            "a.calendarEventId AS calendarEventId, l.subjectId AS subjectId, " +
            "l.startMillis AS startMillis, l.durationMinutes AS durationMinutes, " +
            "l.status AS status, l.groupId AS groupId, l.mode AS mode " +
            "FROM attendance a JOIN lessons l ON l.id = a.lessonId"

    /**
     * v9 → v10: the dead columns go, and so does the `classes` view.
     *
     * - **`lessons.notes`, `lessons.homework`, `attendance.homeworkResult`** — emptied by [MIGRATION_8_9] and
     *   written empty ever since — and **`settings.showGroupsTab`**, unread since groups and students share
     *   a tab. They stayed while removing one meant a table rebuild for a single column; three tables are
     *   rebuilt here, once, instead of carrying four columns that look like data for good (the first three
     *   rode every backup file).
     * - **The view**, replaced by the join written into `ClassDao`'s queries ([CLASS_ROWS], see [ClassRow]).
     *
     * **How a table is rebuilt** ([rebuild]): renamed aside to `<table>_v9`, created again with Room's own
     * v10 DDL, every row copied **by column name, id included**, the old one dropped, its indices created.
     * The autoincrement high-water mark (`sqlite_sequence`) moves to the new table too, so an id that was
     * used and then deleted is not handed out a second time.
     *
     * **Foreign keys are the hazard**, and `PRAGMA foreign_keys = OFF` — the textbook answer — does nothing
     * inside the transaction Room runs this in. With them on, `DROP TABLE` first deletes every row and fires
     * the `ON DELETE` actions, and `attendance` is `ON DELETE CASCADE` from `lessons`: dropping the old
     * lessons while anything pointed at them would cascade away every attendance row — every charge the
     * tutor has. So the order makes that impossible whether foreign keys are on or off, and whatever the
     * SQLite version does to references on a rename (3.26+ rewrites them; older ones only with keys on):
     *
     * 1. the old `lessons` is renamed aside first, so nothing is ever *created* pointing at it;
     * 2. the new `attendance` points at `lessons` — the new one, already holding every lesson;
     * 3. the old `attendance` is dropped **before** the old `lessons`, so by the time that goes nothing
     *    refers to it. Dropping a child table cascades nothing.
     *
     * `defer_foreign_keys` holds the constraint checks to the commit, and [requireNoOrphans] runs them itself
     * at the end: a row pointing at nothing fails the step loudly and by name, the whole upgrade rolls back,
     * and the v9 rows stay on disk for a later build. The view goes first, because a rename (SQLite 3.25+)
     * rewrites a view naming the table and would leave it reading `lessons_v9`.
     *
     * `SchemaCleanupMigrationTest` runs it on a device against a populated v9 database.
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

    /** A table [MIGRATION_9_10] rebuilds: its v10 DDL, the columns it keeps, and its indices. */
    private class Rebuilt(
        val table: String,
        val createSql: String,
        val columns: List<String>,
        val indices: List<String>,
    )

    /**
     * The three tables, **in the order they are rebuilt** — `lessons` before `attendance`, which points at
     * it. Each statement is Room's own for v10, verbatim; `MigrationChainTest` holds them, the columns
     * copied and the indices to `10.json`.
     */
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

    /**
     * [table] renamed aside, created again as v10 declares it, and refilled — ids and the autoincrement
     * high-water mark included. The old copy is left for the caller to drop, in foreign-key order, and the
     * indices for after that: an index keeps its name when its table is renamed, so creating it now would
     * find the name taken (by the old table's) and quietly do nothing.
     */
    private fun rebuild(connection: SQLiteConnection, table: Rebuilt) {
        val old = "${table.table}_v9"
        val columns = table.columns.joinToString { "`$it`" }
        connection.execSQL("ALTER TABLE `${table.table}` RENAME TO `$old`")
        connection.execSQL(table.createSql)
        connection.execSQL("INSERT INTO `${table.table}` ($columns) SELECT $columns FROM `$old`")
        connection.execSQL("DELETE FROM sqlite_sequence WHERE name = '${table.table}'")
        connection.execSQL("UPDATE sqlite_sequence SET name = '${table.table}' WHERE name = '$old'")
    }

    /**
     * Fails the step if a row of [tables] points at a row that does not exist — the check
     * `defer_foreign_keys` postponed, run here so the failure names what it found instead of surfacing as a
     * bare constraint error at commit (or, with foreign keys off, not at all). Thrown inside the migration,
     * it rolls the whole upgrade back.
     */
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

    /** Everything above, in the order Room should be handed it. See `di/DatabaseModule`. */
    val ALL: Array<Migration> =
        arrayOf(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
}
