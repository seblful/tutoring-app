package com.seblful.klas.data.local

import com.seblful.klas.data.billing.BalanceDao
import com.seblful.klas.data.billing.PaymentDao
import com.seblful.klas.data.billing.PriceDao
import com.seblful.klas.data.billing.RateHistoryDao
import com.seblful.klas.data.homework.HomeworkDao
import com.seblful.klas.data.lessons.AttendanceDao
import com.seblful.klas.data.lessons.ClassDao
import com.seblful.klas.data.lessons.LessonDao
import com.seblful.klas.data.people.GroupDao
import com.seblful.klas.data.people.StudentDao
import com.seblful.klas.data.people.SubjectDao
import com.seblful.klas.data.settings.GuideDao
import com.seblful.klas.data.settings.SettingsDao
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

// Metro discovers this contribution from the Android module, so the container must be public.
@ContributesTo(AppScope::class)
@BindingContainer
object PersistenceBindings {
    @Provides fun provideRoomDatabase(db: KlasDatabase): AppDatabase = db.database

    @Provides fun provideSubjectDao(db: AppDatabase): SubjectDao = db.subjectDao()
    @Provides fun provideStudentDao(db: AppDatabase): StudentDao = db.studentDao()
    @Provides fun provideGroupDao(db: AppDatabase): GroupDao = db.groupDao()
    @Provides fun providePriceDao(db: AppDatabase): PriceDao = db.priceDao()
    @Provides fun provideRateHistoryDao(db: AppDatabase): RateHistoryDao = db.rateHistoryDao()
    @Provides fun provideClassDao(db: AppDatabase): ClassDao = db.classDao()
    @Provides fun provideLessonDao(db: AppDatabase): LessonDao = db.lessonDao()
    @Provides fun provideAttendanceDao(db: AppDatabase): AttendanceDao = db.attendanceDao()
    @Provides fun providePaymentDao(db: AppDatabase): PaymentDao = db.paymentDao()
    @Provides fun provideBalanceDao(db: AppDatabase): BalanceDao = db.balanceDao()
    @Provides fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
    @Provides fun provideGuideDao(db: AppDatabase): GuideDao = db.guideDao()
    @Provides fun provideHomeworkDao(db: AppDatabase): HomeworkDao = db.homeworkDao()
    @Provides fun provideSnapshotDao(db: AppDatabase): SnapshotDao = db.snapshotDao()

    @Provides
    @SingleIn(AppScope::class)
    fun provideTransactor(db: AppDatabase): Transactor = RoomTransactor(db)
}
