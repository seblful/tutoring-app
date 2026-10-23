package com.seblful.klas.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.seblful.klas.data.billing.BalanceDao
import com.seblful.klas.data.billing.PaymentDao
import com.seblful.klas.data.billing.PaymentEntity
import com.seblful.klas.data.billing.PriceDao
import com.seblful.klas.data.billing.PriceEntity
import com.seblful.klas.data.billing.RateChangeEntity
import com.seblful.klas.data.billing.RateHistoryDao
import com.seblful.klas.data.homework.HomeworkDao
import com.seblful.klas.data.homework.HomeworkEntity
import com.seblful.klas.data.lessons.AttendanceDao
import com.seblful.klas.data.lessons.AttendanceEntity
import com.seblful.klas.data.lessons.ClassDao
import com.seblful.klas.data.lessons.LessonDao
import com.seblful.klas.data.lessons.LessonEntity
import com.seblful.klas.data.people.GroupDao
import com.seblful.klas.data.people.GroupEntity
import com.seblful.klas.data.people.GroupMemberCrossRef
import com.seblful.klas.data.people.StudentDao
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.StudentSubjectCrossRef
import com.seblful.klas.data.people.SubjectDao
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.data.settings.SettingsDao
import com.seblful.klas.data.settings.SettingsEntity

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
    version = 13,
    exportSchema = true,
)
// Every enum column, written as the constant's name and read back totally — see [EnumConverters].
@TypeConverters(EnumConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
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
    abstract fun balanceDao(): BalanceDao
    abstract fun settingsDao(): SettingsDao
    abstract fun homeworkDao(): HomeworkDao

    /** Whole tables at once, for backup and restore only — see [SnapshotDao]. */
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        // Preserve this filename across releases; Android uses it to find the on-device database.
        const val NAME = "klas.db"

        /**
         * The oldest database version this app supports. A migration is required from every released
         * schema at or above this version; see [Migrations] and `docs/database-and-backup.md`.
         *
         * Raise this only when no supported install can still hold an older database. The baseline can
         * advance only before a schema version ships to users.
         */
        const val BASELINE_VERSION = 5
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT") // Room generates the constructor for each target.
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
