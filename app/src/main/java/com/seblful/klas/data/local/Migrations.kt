package com.seblful.klas.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
 * - Nothing here may drop or rebuild a table lightly: `minSdk` is 24, so `ALTER TABLE … DROP COLUMN` does
 *   not exist below Android 12 and a real removal means copying the table. `SettingsEntity.showGroupsTab`
 *   is the standing example of a column left in place for exactly that reason.
 * - **A view is not recreated for you.** Room validates views against the schema after the migrations run
 *   but recreates none of them, so any step that changes a column the `classes` view projects must drop
 *   and recreate it. The latest such step builds it from [CLASSES_VIEW_SQL]; every earlier one keeps the
 *   `SELECT` of the version it ends at, frozen here, because a step describes that version forever and
 *   the constant moves on. `MigrationChainTest` runs every step and holds the view it leaves to that
 *   version's exported schema. See [MIGRATION_6_7], which is where both halves were learned.
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
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE settings ADD COLUMN language TEXT NOT NULL DEFAULT 'SYSTEM'")
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
     * `lessons.notes` was added to [CLASSES_VIEW_SQL] and to the table, and the view on disk still had
     * thirteen columns.
     *
     * The discipline this adds to the list above: **a migration that changes any column the view projects
     * recreates the view.** `LessonNotesMigrationTest` reads a lesson back *through* `classes`, which is
     * what fails when this is forgotten.
     *
     * **And it recreates v7's view, not today's** ([CLASSES_VIEW_V7]). This step read [CLASSES_VIEW_SQL]
     * until v8 added two columns to it, and from then on it built the v8 view onto a v7 schema —
     * `LessonNotesMigrationTest` failed on the first phone it ran on.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE lessons ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE settings ADD COLUMN unfinishedDigestEnabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE settings ADD COLUMN unfinishedMinuteOfDay INTEGER NOT NULL DEFAULT 1260")
            db.execSQL("DROP VIEW IF EXISTS `classes`")
            db.execSQL("CREATE VIEW `classes` AS $CLASSES_VIEW_V7")
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
     * view ([CLASSES_VIEW_V8]), frozen when v9 took the columns back out of [CLASSES_VIEW_SQL].
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE lessons ADD COLUMN homework TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE attendance ADD COLUMN homeworkResult TEXT")
            db.execSQL("DROP VIEW IF EXISTS `classes`")
            db.execSQL("CREATE VIEW `classes` AS $CLASSES_VIEW_V8")
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
     *   read like data. They are not dropped; see the class KDoc for why a column stays.
     * - **The view is recreated**, since it stops projecting all three.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `homework` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`studentId` INTEGER, `groupId` INTEGER, `text` TEXT NOT NULL, " +
                    "`givenAtMillis` INTEGER NOT NULL, `doneAtMillis` INTEGER, " +
                    "FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`groupId`) REFERENCES `student_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_homework_studentId` ON `homework` (`studentId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_homework_groupId` ON `homework` (`groupId`)")
            db.execSQL(
                "INSERT INTO homework (studentId, groupId, text, givenAtMillis, doneAtMillis) " +
                    "SELECT a.studentId, NULL, l.homework, l.startMillis, " +
                    "CASE WHEN a.homeworkResult IS NULL THEN NULL ELSE l.startMillis END " +
                    "FROM lessons l JOIN attendance a ON a.lessonId = l.id " +
                    "WHERE l.groupId IS NULL AND l.homework != '' AND l.status != 'CANCELLED' " +
                    "ORDER BY l.startMillis",
            )
            db.execSQL(
                "INSERT INTO homework (studentId, groupId, text, givenAtMillis, doneAtMillis) " +
                    "SELECT NULL, l.groupId, l.homework, l.startMillis, " +
                    "CASE WHEN EXISTS (SELECT 1 FROM attendance a WHERE a.lessonId = l.id " +
                    "AND a.homeworkResult IS NULL) THEN NULL ELSE l.startMillis END " +
                    "FROM lessons l " +
                    "WHERE l.groupId IS NOT NULL AND l.homework != '' AND l.status != 'CANCELLED' " +
                    "ORDER BY l.startMillis",
            )
            db.execSQL("UPDATE lessons SET notes = '', homework = '' WHERE notes != '' OR homework != ''")
            db.execSQL("UPDATE attendance SET homeworkResult = NULL WHERE homeworkResult IS NOT NULL")
            db.execSQL("DROP VIEW IF EXISTS `classes`")
            db.execSQL("CREATE VIEW `classes` AS $CLASSES_VIEW_SQL")
        }
    }

    /** Everything above, in the order Room should be handed it. See `di/DatabaseModule`. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
}
