package com.seblful.tutoringapp.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.seblful.tutoringapp.data.local.AppDatabase
import com.seblful.tutoringapp.data.local.ClassDao
import com.seblful.tutoringapp.data.local.PaymentDao
import com.seblful.tutoringapp.data.local.PriceDao
import com.seblful.tutoringapp.data.local.SettingsDao
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

    /**
     * v3 → v4: adds the new settings columns. A real migration (not a destructive one) so the
     * tutor's live data — students, classes, payments — survives the upgrade. The columns get the
     * same defaults as [com.seblful.tutoringapp.data.local.SettingsEntity].
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE settings ADD COLUMN reminderLeadMinutes INTEGER NOT NULL DEFAULT 60")
            db.execSQL("ALTER TABLE settings ADD COLUMN autoCompleteEnabled INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE settings ADD COLUMN themeMode TEXT NOT NULL DEFAULT 'SYSTEM'")
            db.execSQL("ALTER TABLE settings ADD COLUMN defaultLessonMinutes INTEGER NOT NULL DEFAULT 60")
            db.execSQL("ALTER TABLE settings ADD COLUMN currency TEXT NOT NULL DEFAULT 'BYN'")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_3_4)
            // Fallback for any other version jump (earlier pre-release schemas only lived on dev devices).
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideSubjectDao(db: AppDatabase): SubjectDao = db.subjectDao()
    @Provides fun provideStudentDao(db: AppDatabase): StudentDao = db.studentDao()
    @Provides fun providePriceDao(db: AppDatabase): PriceDao = db.priceDao()
    @Provides fun provideClassDao(db: AppDatabase): ClassDao = db.classDao()
    @Provides fun providePaymentDao(db: AppDatabase): PaymentDao = db.paymentDao()
    @Provides fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
}
