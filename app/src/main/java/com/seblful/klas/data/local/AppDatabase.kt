package com.seblful.klas.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubjectEntity::class,
        StudentEntity::class,
        StudentSubjectCrossRef::class,
        GroupEntity::class,
        GroupMemberCrossRef::class,
        PriceEntity::class,
        RateChangeEntity::class,
        ClassEntity::class,
        PaymentEntity::class,
        SettingsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun studentDao(): StudentDao
    abstract fun groupDao(): GroupDao
    abstract fun priceDao(): PriceDao
    abstract fun rateHistoryDao(): RateHistoryDao
    abstract fun classDao(): ClassDao
    abstract fun paymentDao(): PaymentDao
    abstract fun settingsDao(): SettingsDao

    /** Whole tables at once, for backup and restore only — see [SnapshotDao]. */
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        // Renamed with the app (was "tutoring.db"). Safe only because the application id changed in
        // the same breath: every install is a fresh one, so there is no old file left to look for.
        // That same fact is why this database starts again at version 1 with no migrations — the
        // only released builds are `com.seblful.repetit`, a different app Android never upgrades
        // from. The next release sets the baseline: every schema change after it needs a migration.
        const val NAME = "klas.db"
    }
}
