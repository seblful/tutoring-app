package com.seblful.klas.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * **v1 → v2: a lesson becomes a row, and attendance becomes its own fact.**
 *
 * v1 kept one `classes` row per student, carrying the lesson's own facts — subject, start, length, mode,
 * status — once per member of a group lesson, with `(groupId, startMillis)` as the only thing making
 * those rows one lesson. This splits them: `lessons` holds the hour once, `attendance` holds each
 * student's place in it, and the `classes` view joins them back for every read.
 *
 * **The mapping, and why it moves no money.**
 * - One `lessons` row per group lesson (`groupId`, `startMillis`) and one per individual class. Its
 *   subject, length and mode come from the member rows, which shared them by construction — a lesson-wide
 *   edit was the only way to change any of them.
 * - Its status is the strictest thing true of all its members: **scheduled if any member is**, else
 *   completed if any is, else cancelled. That is the rule the app already showed for such a lesson
 *   (`ClassSession.status`), so a migrated database reads exactly as it did before.
 * - Attendance follows from the pair. A member who was `COMPLETED` is `PRESENT`; one who was `SCHEDULED`
 *   is `EXPECTED`; one who was `CANCELLED` in a lesson that still happened is `ABSENT` — the correction
 *   that used to be expressible only as a cancelled class. In a lesson cancelled outright, everyone is
 *   `EXPECTED`: nobody attended, and nobody was absent from an hour that did not happen.
 * - `priceMinor`, `chargeMinor` and `calendarEventId` are **copied verbatim**, and every attendance row
 *   keeps its class's `id`, so charges, the calendar mirror and any id held elsewhere all survive. The
 *   billing rule recomputes to the same money: a `PRESENT` member of a completed lesson bills, an
 *   `ABSENT` one bills nothing, and a cancelled lesson bills nobody.
 *
 * A migration rather than a drop even though no released build has a v1 database: the ones that exist are
 * on the author's phone, with real students in them (`DatabaseModule` only allows a destructive fallback
 * in debug, and this is what keeps that fallback unreached).
 *
 * `MigrationTest` runs this against a v1 database built by `MigrationTestHelper` and asserts the rows
 * come out the other side.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `lessons` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `subjectId` INTEGER NOT NULL,
                `startMillis` INTEGER NOT NULL,
                `durationMinutes` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `groupId` INTEGER,
                `mode` TEXT NOT NULL,
                FOREIGN KEY(`subjectId`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`groupId`) REFERENCES `student_groups`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_subjectId` ON `lessons` (`subjectId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_startMillis` ON `lessons` (`startMillis`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_status` ON `lessons` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_groupId` ON `lessons` (`groupId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `attendance` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `lessonId` INTEGER NOT NULL,
                `studentId` INTEGER NOT NULL,
                `state` TEXT NOT NULL,
                `priceMinor` INTEGER NOT NULL,
                `chargeMinor` INTEGER NOT NULL,
                `calendarEventId` TEXT,
                FOREIGN KEY(`lessonId`) REFERENCES `lessons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_lessonId` ON `attendance` (`lessonId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_studentId` ON `attendance` (`studentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_state` ON `attendance` (`state`)")

        // One lesson per group lesson, one per individual class. `groupId IS NULL` cannot group — every
        // such class is its own lesson — so the two halves are inserted separately. `MIN(id)` is what
        // ties the attendance rows below back to the lesson they came from, and it is stable because a
        // session's rows are read in id order everywhere else too.
        db.execSQL(
            """
            INSERT INTO `lessons` (`id`, `subjectId`, `startMillis`, `durationMinutes`, `status`, `groupId`, `mode`)
            SELECT MIN(c.id), c.subjectId, c.startMillis, c.durationMinutes,
                CASE
                    WHEN SUM(c.status = 'SCHEDULED') > 0 THEN 'SCHEDULED'
                    WHEN SUM(c.status = 'COMPLETED') > 0 THEN 'COMPLETED'
                    ELSE 'CANCELLED'
                END,
                c.groupId, c.mode
            FROM `classes` c
            WHERE c.groupId IS NOT NULL
            GROUP BY c.groupId, c.startMillis
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `lessons` (`id`, `subjectId`, `startMillis`, `durationMinutes`, `status`, `groupId`, `mode`)
            SELECT c.id, c.subjectId, c.startMillis, c.durationMinutes, c.status, NULL, c.mode
            FROM `classes` c
            WHERE c.groupId IS NULL
            """.trimIndent(),
        )

        // Every class keeps its id as its attendance id, and points at the lesson its (group, start) —
        // or its own id — became. The state is read off both statuses, per the KDoc above.
        db.execSQL(
            """
            INSERT INTO `attendance` (`id`, `lessonId`, `studentId`, `state`, `priceMinor`, `chargeMinor`, `calendarEventId`)
            SELECT c.id, l.id, c.studentId,
                CASE
                    WHEN c.status = 'COMPLETED' THEN 'PRESENT'
                    WHEN c.status = 'SCHEDULED' THEN 'EXPECTED'
                    WHEN l.status = 'CANCELLED' THEN 'EXPECTED'
                    ELSE 'ABSENT'
                END,
                c.priceMinor, c.chargeMinor, c.calendarEventId
            FROM `classes` c
            JOIN `lessons` l
                ON l.startMillis = c.startMillis
                AND ((l.groupId IS NULL AND c.groupId IS NULL AND l.id = c.id) OR l.groupId = c.groupId)
            """.trimIndent(),
        )

        db.execSQL("DROP TABLE `classes`")
        // The view takes the name the table had, so every read query is unchanged. Its SQL must match
        // what Room generates from `ClassRow` exactly, or the schema validation after this migration
        // fails — `schemas/2.json` is where the two are compared.
        db.execSQL(
            "CREATE VIEW `classes` AS SELECT a.id AS id, a.lessonId AS lessonId, " +
                "a.studentId AS studentId, a.state AS state, a.priceMinor AS priceMinor, " +
                "a.chargeMinor AS chargeMinor, a.calendarEventId AS calendarEventId, " +
                "l.subjectId AS subjectId, l.startMillis AS startMillis, " +
                "l.durationMinutes AS durationMinutes, l.status AS status, l.groupId AS groupId, " +
                "l.mode AS mode FROM attendance a JOIN lessons l ON l.id = a.lessonId",
        )
    }
}

/**
 * **v2 → v3: the settings row remembers the last export.**
 *
 * One nullable column on a single-row table (`SettingsEntity.lastExportMillis`). Null is what an existing
 * install upgrades to and it is the honest answer: the app was not recording this, so it does not know
 * when — or whether — that tutor last made a copy. The backup screen says «Копий ещё не было» until the
 * next export, which is a fair thing to tell somebody the app cannot vouch for.
 *
 * No `DEFAULT` clause, and therefore no `defaultValue` on the entity either: the two must agree or Room's
 * schema validation fails after the migration, and a nullable column with no default is what
 * `ALTER TABLE … ADD COLUMN` produces.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `settings` ADD COLUMN `lastExportMillis` INTEGER")
    }
}

/**
 * **v3 → v4: the tutor may ask for the wallpaper's colours.**
 *
 * One boolean on the same single-row table (`SettingsEntity.dynamicColorEnabled`). `NOT NULL DEFAULT 0`,
 * which is both what SQLite requires of a non-null column added to a table that already has a row and
 * the answer that matches what the tutor has been looking at: every existing install has been running on
 * the app's own palette, so backfilling "off" changes nothing about how their phone looks the moment
 * they upgrade. The declared default on the entity has to say `"0"` too, or Room's schema validation
 * fails the moment this migration finishes.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `settings` ADD COLUMN `dynamicColorEnabled` INTEGER NOT NULL DEFAULT 0")
    }
}
