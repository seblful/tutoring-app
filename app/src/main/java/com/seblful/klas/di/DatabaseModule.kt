package com.seblful.klas.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.AttendanceDao
import com.seblful.klas.data.local.ClassDao
import com.seblful.klas.data.local.GroupDao
import com.seblful.klas.data.local.HomeworkDao
import com.seblful.klas.data.local.LessonDao
import com.seblful.klas.data.local.Migrations
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
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * **Every migration the app owns is handed over here** — `Migrations.kt` is where they are written and
     * this is the only thing Room reads, so a chain that exists but is never passed to `addMigrations`
     * leaves every upgrade with no path at all. `MigrationChainTest` asserts both halves: that a step exists
     * for every version between [AppDatabase.BASELINE_VERSION] and the one declared, and that they are
     * wired in here. A fresh install skips all of it and creates the current schema outright.
     *
     * The destructive fallback is **gated on the debug build**, because that is the only case it was
     * ever meant for: a dev device carrying a schema from some earlier point in development. Room applies
     * that fallback to *any* version it cannot find a path for, though, so left unconditional it would
     * also answer "the version was bumped and the migration forgotten" — by recreating a released
     * database empty, silently, on first launch after an ordinary Play update, with nothing to recover
     * from but whatever backup the tutor last exported. The tutor's students, lessons and payments live
     * only here; there is no backend and no cloud copy.
     *
     * A release build therefore **refuses to open** a database it cannot migrate. That throws where
     * every row is still on disk and the next build can still migrate it properly — a crash is
     * recoverable, a wipe is not.
     *
     * **SQLite is the bundled driver's**, compiled into the app, not the one Android ships. It is the one
     * SQLite Room can use on iOS as well, so the database code an iOS build would share runs on the same
     * engine here, whatever the phone's Android version carries; the JVM tests run it too
     * (`testutil/TestDatabase`). The cost is the native library in the APK. With a driver set, Room runs
     * the `SQLiteConnection` overload of each migration (see `Migrations.kt`) and queries on [Dispatchers.IO].
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        @DebugBuild debugBuild: Boolean,
    ): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*Migrations.ALL)
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
    @Provides fun provideHomeworkDao(db: AppDatabase): HomeworkDao = db.homeworkDao()
    @Provides fun provideSnapshotDao(db: AppDatabase): SnapshotDao = db.snapshotDao()

    @Provides
    @Singleton
    fun provideTransactor(db: AppDatabase): Transactor = RoomTransactor(db)
}
