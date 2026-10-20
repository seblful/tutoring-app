package com.seblful.klas.di

import android.content.Context
import com.seblful.klas.data.billing.PaymentDao
import com.seblful.klas.data.billing.PriceDao
import com.seblful.klas.data.billing.RateHistoryDao
import com.seblful.klas.data.homework.HomeworkDao
import com.seblful.klas.data.lessons.AttendanceDao
import com.seblful.klas.data.lessons.ClassDao
import com.seblful.klas.data.lessons.LessonDao
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.RoomTransactor
import com.seblful.klas.data.local.SnapshotDao
import com.seblful.klas.data.local.Transactor
import com.seblful.klas.data.local.buildDatabase
import com.seblful.klas.data.local.databaseBuilder
import com.seblful.klas.data.people.GroupDao
import com.seblful.klas.data.people.StudentDao
import com.seblful.klas.data.people.SubjectDao
import com.seblful.klas.data.settings.SettingsDao
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
object DatabaseModule {

    /** Shared schema and migrations, with destructive fallback enabled only by the debug host. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(
        @ApplicationContext context: Context,
        @DebugBuild debugBuild: Boolean,
    ): AppDatabase =
        buildDatabase(databaseBuilder(context), allowDestructiveMigration = debugBuild)

    @Provides fun provideSubjectDao(db: AppDatabase): SubjectDao = db.subjectDao()
    @Provides fun provideStudentDao(db: AppDatabase): StudentDao = db.studentDao()
    @Provides fun provideGroupDao(db: AppDatabase): GroupDao = db.groupDao()
    @Provides fun providePriceDao(db: AppDatabase): PriceDao = db.priceDao()
    @Provides fun provideRateHistoryDao(db: AppDatabase): RateHistoryDao = db.rateHistoryDao()
    @Provides fun provideClassDao(db: AppDatabase): ClassDao = db.classDao()
    @Provides fun provideLessonDao(db: AppDatabase): LessonDao = db.lessonDao()
    @Provides fun provideAttendanceDao(db: AppDatabase): AttendanceDao = db.attendanceDao()
    @Provides fun providePaymentDao(db: AppDatabase): PaymentDao = db.paymentDao()
    @Provides fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
    @Provides fun provideHomeworkDao(db: AppDatabase): HomeworkDao = db.homeworkDao()
    @Provides fun provideSnapshotDao(db: AppDatabase): SnapshotDao = db.snapshotDao()

    @Provides
    @SingleIn(AppScope::class)
    fun provideTransactor(db: AppDatabase): Transactor = RoomTransactor(db)
}
