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
        LessonEntity::class,
        AttendanceEntity::class,
        PaymentEntity::class,
        SettingsEntity::class,
    ],
    views = [ClassRow::class],
    version = 3,
    exportSchema = true,
)
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

    /** Whole tables at once, for backup and restore only — see [SnapshotDao]. */
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        // Renamed with the app (was "tutoring.db"). Safe only because the application id changed in
        // the same breath: every install is a fresh one, so there is no old file left to look for.
        // That same fact is why this database started again at version 1 — the only released builds are
        // `com.seblful.repetit`, a different app Android never upgrades from.
        //
        // v2 splits `classes` into `lessons` + `attendance` and migrates rather than dropping, even
        // though no release has to be upgraded from v1: the databases that do exist are the ones on the
        // author's own phone, holding real students. See [MIGRATION_1_2].
        const val NAME = "klas.db"
    }
}
