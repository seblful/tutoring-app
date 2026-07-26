package com.seblful.repetit.di

import android.content.Context
import androidx.room.Room
import com.seblful.repetit.data.local.AppDatabase
import com.seblful.repetit.data.local.ClassDao
import com.seblful.repetit.data.local.GroupDao
import com.seblful.repetit.data.local.Migrations
import com.seblful.repetit.data.local.PaymentDao
import com.seblful.repetit.data.local.PriceDao
import com.seblful.repetit.data.local.RateHistoryDao
import com.seblful.repetit.data.local.SettingsDao
import com.seblful.repetit.data.local.StudentDao
import com.seblful.repetit.data.local.SubjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // Real migrations (see Migrations.ALL) carry user data forward. The destructive fallback
            // stays only as the last resort for dev/beta devices whose schema predates v1 and so has
            // no migration path at all; every schema change from v1 on must ship a migration here.
            .addMigrations(*Migrations.ALL)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun provideSubjectDao(db: AppDatabase): SubjectDao = db.subjectDao()
    @Provides fun provideStudentDao(db: AppDatabase): StudentDao = db.studentDao()
    @Provides fun provideGroupDao(db: AppDatabase): GroupDao = db.groupDao()
    @Provides fun providePriceDao(db: AppDatabase): PriceDao = db.priceDao()
    @Provides fun provideRateHistoryDao(db: AppDatabase): RateHistoryDao = db.rateHistoryDao()
    @Provides fun provideClassDao(db: AppDatabase): ClassDao = db.classDao()
    @Provides fun providePaymentDao(db: AppDatabase): PaymentDao = db.paymentDao()
    @Provides fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
}
