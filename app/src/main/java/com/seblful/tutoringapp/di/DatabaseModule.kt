package com.seblful.tutoringapp.di

import android.content.Context
import androidx.room.Room
import com.seblful.tutoringapp.data.local.AppDatabase
import com.seblful.tutoringapp.data.local.ClassDao
import com.seblful.tutoringapp.data.local.PaymentDao
import com.seblful.tutoringapp.data.local.PriceDao
import com.seblful.tutoringapp.data.local.StudentDao
import com.seblful.tutoringapp.data.local.SubjectDao
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
            .build()

    @Provides fun provideSubjectDao(db: AppDatabase): SubjectDao = db.subjectDao()
    @Provides fun provideStudentDao(db: AppDatabase): StudentDao = db.studentDao()
    @Provides fun providePriceDao(db: AppDatabase): PriceDao = db.priceDao()
    @Provides fun provideClassDao(db: AppDatabase): ClassDao = db.classDao()
    @Provides fun providePaymentDao(db: AppDatabase): PaymentDao = db.paymentDao()
}
