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
    version = 7,
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

    companion object {
        // Renamed with the app (was "tutoring.db"). Safe only because the application id changed in
        // the same breath: every install is a fresh one, so there is no old file left to look for.
        const val NAME = "klas.db"
    }
}
