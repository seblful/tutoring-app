package com.seblful.repetit.di

import android.content.Context
import androidx.room.Room
import com.seblful.repetit.data.local.AppDatabase
import com.seblful.repetit.data.local.ClassDao
import com.seblful.repetit.data.local.PaymentDao
import com.seblful.repetit.data.local.PriceDao
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
            // No migrations: the schema is collapsed to a fresh v1 for the first release. Dev/beta
            // devices carrying an older schema get destructively recreated. Once published, schema
            // changes must ship a real migration (and bump the version) instead.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideSubjectDao(db: AppDatabase): SubjectDao = db.subjectDao()
    @Provides fun provideStudentDao(db: AppDatabase): StudentDao = db.studentDao()
    @Provides fun providePriceDao(db: AppDatabase): PriceDao = db.priceDao()
    @Provides fun provideClassDao(db: AppDatabase): ClassDao = db.classDao()
    @Provides fun providePaymentDao(db: AppDatabase): PaymentDao = db.paymentDao()
    @Provides fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
}
