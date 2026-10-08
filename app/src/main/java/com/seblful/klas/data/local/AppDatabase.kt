package com.seblful.klas.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SubjectEntity::class,
        StudentEntity::class,
        StudentSubjectCrossRef::class,
        GroupEntity::class,
        GroupMemberCrossRef::class,
        PriceEntity::class,
        RateChangeEntity::class,
        LessonEntity::class,
        AttendanceEntity::class,
        PaymentEntity::class,
        HomeworkEntity::class,
        SettingsEntity::class,
    ],
    views = [ClassRow::class],
    version = 9,
    exportSchema = true,
)
// Every enum column, written as the constant's name and read back totally — see [EnumConverters].
@TypeConverters(EnumConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun studentDao(): StudentDao
    abstract fun groupDao(): GroupDao
    abstract fun priceDao(): PriceDao
    abstract fun rateHistoryDao(): RateHistoryDao
    abstract fun classDao(): ClassDao
    abstract fun lessonDao(): LessonDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun paymentDao(): PaymentDao
    abstract fun settingsDao(): SettingsDao
    abstract fun homeworkDao(): HomeworkDao

    /** Whole tables at once, for backup and restore only — see [SnapshotDao]. */
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        // Renamed with the app (was "tutoring.db"). Safe only because the application id changed in
        // the same breath: every install is a fresh one, so there is no old file left to look for.
        const val NAME = "klas.db"

        /**
         * **The oldest schema version any `klas.db` can be at**, and therefore the version Room needs no
         * way *into*. Equal to the version `@Database` declares would mean: there are no migrations,
         * because there is nothing to migrate from. It is four below it — the app is at v9 — so
         * `data/local/Migrations.kt` holds exactly four steps.
         *
         * This is a claim about **releases**, not about the schema, and it is why that file was deleted
         * once already. It was 1, with a chain of four migrations up to v5, and every one of them was dead
         * code:
         *
         * - `com.seblful.klas` v0.2.0 (`versionCode` 3) declared schema v1 and reached nobody — it is a
         *   tag, not a shipped artifact. `com.seblful.repetit` is a *different application id*, which
         *   Android never upgrades from, so its `tutoring.db` is unreachable by this app whatever version
         *   it is at (the JSON export is the only bridge, and that is `BackupData`'s, not a migration's).
         * - v2, v3, v4 and v5 were all written after that tag, so no device has ever held one of the
         *   intermediate versions — they existed only between unreleased commits.
         * - The one database that holds real students, the author's own `.debug` install, was already at
         *   v5 when this was squashed.
         *
         * So the chain migrated nothing, and every step of it was code that could only ever be wrong in
         * silence. Deleting it is safe exactly once — before the first release of a v5 build.
         *
         * **That release has happened: 0.3.0 (`versionCode` 4).** So the window is shut, and this number
         * is no longer free. It is the version in tutors' hands, and the v6 bump that added
         * `settings.language` duly shipped [Migrations.MIGRATION_5_6] from it — the first real migration
         * this app has ever had. [Migrations.MIGRATION_6_7] is the second, adding `lessons.notes` and the
         * evening digest's two settings columns, [Migrations.MIGRATION_7_8] the third, adding homework,
         * and [Migrations.MIGRATION_8_9] the fourth, giving homework its own table. `MigrationChainTest`
         * reads this constant to demand one per step.
         *
         * Raising it is a promise that no database below it exists anywhere, which after a release means
         * *every* install has been upgraded past it — not a thing to assume, and not a thing v6 changes.
         */
        const val BASELINE_VERSION = 5
    }
}
