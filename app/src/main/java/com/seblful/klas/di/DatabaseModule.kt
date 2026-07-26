package com.seblful.klas.di

import android.content.Context
import androidx.room.Room
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.ClassDao
import com.seblful.klas.data.local.GroupDao
import com.seblful.klas.data.local.PaymentDao
import com.seblful.klas.data.local.PriceDao
import com.seblful.klas.data.local.RateHistoryDao
import com.seblful.klas.data.local.SettingsDao
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
     * No migrations yet: v1 is the first schema this application id ever ships, so no device can hold
     * an older one (see [AppDatabase.NAME]). Once v1 is released, every schema change must bump the
     * version and add a real migration here — `MigrationChainTest` fails the build if it doesn't.
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
            .apply { if (debugBuild) fallbackToDestructiveMigration(dropAllTables = true) }
            .build()

    @Provides fun provideSubjectDao(db: AppDatabase): SubjectDao = db.subjectDao()
    @Provides fun provideStudentDao(db: AppDatabase): StudentDao = db.studentDao()
    @Provides fun provideGroupDao(db: AppDatabase): GroupDao = db.groupDao()
    @Provides fun providePriceDao(db: AppDatabase): PriceDao = db.priceDao()
    @Provides fun provideRateHistoryDao(db: AppDatabase): RateHistoryDao = db.rateHistoryDao()
    @Provides fun provideClassDao(db: AppDatabase): ClassDao = db.classDao()
    @Provides fun providePaymentDao(db: AppDatabase): PaymentDao = db.paymentDao()
    @Provides fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()

    @Provides
    @Singleton
    fun provideTransactor(db: AppDatabase): Transactor = RoomTransactor(db)
}
