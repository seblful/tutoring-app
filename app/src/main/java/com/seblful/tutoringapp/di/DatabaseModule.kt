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
     * same defaults as [com.seblful.tutoringapp.data.local.SettingsEntity] (auto-complete defaults
     * **off** — billing an unconfirmed lesson could over-state a student's debt).
     */
    internal val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE settings ADD COLUMN reminderLeadMinutes INTEGER NOT NULL DEFAULT 60")
            db.execSQL("ALTER TABLE settings ADD COLUMN autoCompleteEnabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE settings ADD COLUMN themeMode TEXT NOT NULL DEFAULT 'SYSTEM'")
            db.execSQL("ALTER TABLE settings ADD COLUMN defaultLessonMinutes INTEGER NOT NULL DEFAULT 60")
            db.execSQL("ALTER TABLE settings ADD COLUMN currency TEXT NOT NULL DEFAULT 'BYN'")
        }
    }

    /**
     * v4 → v5: changes the `classes` and `payments` foreign keys from `ON DELETE CASCADE` to
     * `ON DELETE RESTRICT`, so the database itself refuses to delete a student or subject that still
     * has classes/payments instead of silently cascade-erasing earned income and history. SQLite
     * can't alter a foreign key in place, so each table is recreated and its rows copied over
     * (the standard recreate pattern). The recreated schema must match Room's expected `TableInfo`
     * exactly, or the post-migration validation fails.
     */
    internal val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // --- classes ---
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `classes_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `studentId` INTEGER NOT NULL,
                    `subjectId` INTEGER NOT NULL,
                    `startMillis` INTEGER NOT NULL,
                    `durationMinutes` INTEGER NOT NULL,
                    `priceMinor` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `chargeMinor` INTEGER NOT NULL,
                    `recurringGroupId` INTEGER,
                    `mode` TEXT NOT NULL,
                    `calendarEventId` TEXT,
                    FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`subjectId`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `classes_new` (id, studentId, subjectId, startMillis, durationMinutes,
                    priceMinor, status, chargeMinor, recurringGroupId, mode, calendarEventId)
                SELECT id, studentId, subjectId, startMillis, durationMinutes,
                    priceMinor, status, chargeMinor, recurringGroupId, mode, calendarEventId
                FROM `classes`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `classes`")
            db.execSQL("ALTER TABLE `classes_new` RENAME TO `classes`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_classes_studentId` ON `classes` (`studentId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_classes_subjectId` ON `classes` (`subjectId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_classes_startMillis` ON `classes` (`startMillis`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_classes_status` ON `classes` (`status`)")

            // --- payments ---
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `payments_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `studentId` INTEGER NOT NULL,
                    `dateMillis` INTEGER NOT NULL,
                    `amountMinor` INTEGER NOT NULL,
                    FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `payments_new` (id, studentId, dateMillis, amountMinor)
                SELECT id, studentId, dateMillis, amountMinor FROM `payments`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `payments`")
            db.execSQL("ALTER TABLE `payments_new` RENAME TO `payments`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_studentId` ON `payments` (`studentId`)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
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
