package com.seblful.klas.di

import android.content.Context
import androidx.room.Room
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.AttendanceDao
import com.seblful.klas.data.local.ClassDao
import com.seblful.klas.data.local.GroupDao
import com.seblful.klas.data.local.LessonDao
import com.seblful.klas.data.local.MIGRATION_1_2
import com.seblful.klas.data.local.MIGRATION_2_3
import com.seblful.klas.data.local.MIGRATION_3_4
import com.seblful.klas.data.local.MIGRATION_4_5
import com.seblful.klas.data.local.PaymentDao
import com.seblful.klas.data.local.PriceDao
import com.seblful.klas.data.local.RateHistoryDao
import com.seblful.klas.data.local.SettingsDao
import com.seblful.klas.data.local.SnapshotDao
import com.seblful.klas.data.local.StudentDao
import com.seblful.klas.data.local.SubjectDao
import com.seblful.klas.data.local.RoomTransactor
import com.seblful.klas.data.local.Transactor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Every schema version this app has ever had, and the way from each to the next
     * (`MigrationChainTest` fails the build if one is missing).
     *
     * v2 — splitting `classes` into `lessons` + `attendance` — is migrated rather than dropped even
     * though no *released* build holds a v1 database: the ones that exist are on development phones with
     * real students in them, and "no released baseline" is not the same as "no data".
     *
     * The destructive fallback is **gated on the debug build**, because that is the only case it was
     * ever meant for: a dev device carrying a pre-release schema, which the squash to v1 makes every
     * `.debug` install on hand. Room applies that fallback to *any* version it cannot find a path for,
     * though, so left unconditional it also answers "the version was bumped and the migration
     * forgotten" — by recreating a released database empty, silently, on first launch after an
     * ordinary Play update, with nothing to recover from but whatever backup the tutor last exported.
     * The tutor's students, lessons and payments live only here; there is no backend and no cloud copy.
     *
     * A release build therefore **refuses to open** a database it cannot migrate. That throws where
     * every row is still on disk and the next build can still migrate it properly — a crash is
     * recoverable, a wipe is not.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        @DebugBuild debugBuild: Boolean,
    ): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .apply { if (debugBuild) fallbackToDestructiveMigration(dropAllTables = true) }
            .build()

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
    @Provides fun provideSnapshotDao(db: AppDatabase): SnapshotDao = db.snapshotDao()

    @Provides
    @Singleton
    fun provideTransactor(db: AppDatabase): Transactor = RoomTransactor(db)
}
