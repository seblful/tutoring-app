package com.seblful.klas.data.backup

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.calendar.Guest
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.AttendanceEntity
import com.seblful.klas.data.local.DatabaseSnapshot
import com.seblful.klas.data.local.LessonEntity
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.RoomTransactor
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.data.repository.CalendarMirror
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.testutil.RecordingReminderScheduler
import com.seblful.klas.testutil.RussianMinskPhone
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

/**
 * The restore path as a whole: replacing the data also ends first run (so a tutor restoring onto a
 * fresh install isn't asked to set the app up again) and re-plans reminders.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    private val noCalendar = object : CalendarSync {
        override suspend fun upsert(
            klass: TutoringClass,
            title: String,
            guest: Guest?,
            reminderLeadMinutes: Int,
        ): String? = null

        override suspend fun refresh(klass: TutoringClass, title: String) = Unit

        override suspend fun delete(eventId: String) = Unit
    }

    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var restore: BackupRestore
    private val reminders = RecordingReminderScheduler()

    private fun newDb(): AppDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java,
    )
        .setDriver(BundledSQLiteDriver())
        .build()

    // The real RoomTransactor, not a fake: these tests run against real Room, so the import's
    // all-or-nothing transaction is one of the things being exercised.
    private fun managerFor(db: AppDatabase) = BackupManager(RoomTransactor(db), db.snapshotDao())

    @Before fun setUp() {
        source = newDb()
        target = newDb()
        settings = SettingsRepository(target.settingsDao(), RussianMinskPhone)
        val mirror = CalendarMirror(
            target.classDao(), target.attendanceDao(), target.studentDao(), target.subjectDao(), target.groupDao(),
            noCalendar, settings,
        )
        restore = BackupRestore(
            backup = managerFor(target),
            settings = settings,
            calendar = mirror,
            reminders = reminders,
        )
    }

    @After fun tearDown() {
        source.close()
        target.close()
    }

    /** A backup of one lesson, exported from [source]. */
    private suspend fun snapshot(): String {
        source.snapshotDao().insert(
            DatabaseSnapshot(
                subjects = listOf(SubjectEntity(1, "Математика")),
                students = listOf(StudentEntity(1, "Иван")),
                prices = listOf(PriceEntity(1, 1, amountMinor = 5000)),
                lessons = listOf(
                    LessonEntity(
                        1, 1, Instant.parse("2026-06-18T10:00:00Z").toEpochMilliseconds(), 60,
                        status = LessonStatus.SCHEDULED,
                    ),
                ),
                attendance = listOf(AttendanceEntity(1, 1, 1, Attended.EXPECTED, priceMinor = 5000)),
            ),
        )
        return managerFor(source).exportJson()
    }

    @Test fun restoreLoadsTheDataAndEndsFirstRun() = runTest {
        val json = snapshot()
        assertFalse(settings.get().onboardingComplete) // a fresh install: onboarding pending

        restore.restore(json)

        val restored = target.snapshotDao().read()
        assertEquals(listOf("Математика"), restored.subjects.map { it.name })
        assertEquals(listOf("Иван"), restored.students.map { it.name })
        assertEquals(1, restored.lessons.size)
        assertEquals(1, restored.attendance.size)
        assertTrue(settings.get().onboardingComplete) // restored data *is* the setup
        assertEquals(1, reminders.refreshCount) // imported classes re-plan reminders
    }

    @Test fun aFileThatIsNotABackupChangesNothing() = runTest {
        target.snapshotDao().insert(DatabaseSnapshot(subjects = listOf(SubjectEntity(7, "Физика"))))

        val failure = runCatching { restore.restore("not a backup at all") }

        assertTrue(failure.isFailure)
        assertEquals(listOf("Физика"), target.snapshotDao().read().subjects.map { it.name })
        assertFalse(settings.get().onboardingComplete) // still first run
        assertEquals(0, reminders.refreshCount)
    }
}
