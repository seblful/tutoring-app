package com.seblful.repetit.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real (non-destructive) schema migrations. A published build must never drop user data, so every
 * schema change ships one of these plus a bumped [AppDatabase] version — the destructive fallback
 * in `DatabaseModule` only ever covers dev/beta devices whose schema predates v1.
 */
object Migrations {

    /**
     * v1 → v2: student groups.
     *
     * Adds the `student_groups` roster and its `group_members` link table, and gives `classes` a
     * nullable `groupId` (the group a class was booked as part of). SQLite can't add a column with a
     * foreign key or drop one, so `classes` is recreated and copied — which also retires the unused
     * `recurringGroupId` column (recurring series are a non-goal; the name clashed confusingly with
     * the new group id). No table references `classes`, so recreating it breaks no constraint.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `student_groups` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `note` TEXT NOT NULL,
                    `defaultMode` TEXT NOT NULL,
                    `archived` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `group_members` (
                    `groupId` INTEGER NOT NULL,
                    `studentId` INTEGER NOT NULL,
                    PRIMARY KEY(`groupId`, `studentId`),
                    FOREIGN KEY(`groupId`) REFERENCES `student_groups`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE ,
                    FOREIGN KEY(`studentId`) REFERENCES `students`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_members_studentId` ON `group_members` (`studentId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `classes_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `studentId` INTEGER NOT NULL,
                    `subjectId` INTEGER NOT NULL,
                    `startMillis` INTEGER NOT NULL,
                    `durationMinutes` INTEGER NOT NULL,
                    `priceMinor` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `chargeMinor` INTEGER NOT NULL,
                    `groupId` INTEGER,
                    `mode` TEXT NOT NULL,
                    `calendarEventId` TEXT,
                    FOREIGN KEY(`studentId`) REFERENCES `students`(`id`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT ,
                    FOREIGN KEY(`subjectId`) REFERENCES `subjects`(`id`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT ,
                    FOREIGN KEY(`groupId`) REFERENCES `student_groups`(`id`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `classes_new` (
                    `id`, `studentId`, `subjectId`, `startMillis`, `durationMinutes`,
                    `priceMinor`, `status`, `chargeMinor`, `groupId`, `mode`, `calendarEventId`
                )
                SELECT `id`, `studentId`, `subjectId`, `startMillis`, `durationMinutes`,
                       `priceMinor`, `status`, `chargeMinor`, NULL, `mode`, `calendarEventId`
                FROM `classes`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `classes`")
            db.execSQL("ALTER TABLE `classes_new` RENAME TO `classes`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_classes_studentId` ON `classes` (`studentId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_classes_subjectId` ON `classes` (`subjectId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_classes_startMillis` ON `classes` (`startMillis`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_classes_status` ON `classes` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_classes_groupId` ON `classes` (`groupId`)")
        }
    }

    /**
     * v2 → v3: the «Группы» tab can be hidden.
     *
     * Adds `settings.showGroupsTab`, defaulting to 1 so an existing install keeps the tab it just
     * gained. A plain `ADD COLUMN` is enough — no constraint is involved, and the settings row is
     * device-local anyway (it is not in the backup snapshot).
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `settings` ADD COLUMN `showGroupsTab` INTEGER NOT NULL DEFAULT 1")
        }
    }

    /** Every migration, in order — passed to the Room builder. */
    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
