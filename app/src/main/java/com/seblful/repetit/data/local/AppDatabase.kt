package com.seblful.repetit.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubjectEntity::class,
        StudentEntity::class,
        StudentSubjectCrossRef::class,
        PriceEntity::class,
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
    abstract fun priceDao(): PriceDao
    abstract fun classDao(): ClassDao
    abstract fun paymentDao(): PaymentDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val NAME = "tutoring.db"
    }
}
