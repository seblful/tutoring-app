package com.seblful.klas.data.repository

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seblful.klas.calendar.CalendarMirror
import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.calendar.Guest
import com.seblful.klas.data.billing.PriceEntity
import com.seblful.klas.data.lessons.AttendanceEntity
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.LessonEntity
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.DatabaseSnapshot
import com.seblful.klas.data.local.RoomTransactor
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.format.toDevicePreferences
import com.seblful.klas.testutil.RecordingReminderScheduler
import com.seblful.klas.testutil.RussianMinskPhone
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class AutoCompleteTest {

    private val noCalendar = object : CalendarSync {
        override suspend fun upsert(
            klass: TutoringClass,
            title: String,
            guest: Guest?,
            reminderLeadMinutes: Int,
            description: String,
        ): String? = null
        override suspend fun refresh(klass: TutoringClass, title: String, description: String) = Unit
        override suspend fun delete(eventId: String) = Unit
    }

    private lateinit var db: AppDatabase
    private lateinit var repo: ClassRepository

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java,
        )
            .setDriver(BundledSQLiteDriver())
            .build()
        db.snapshotDao().insert(
            DatabaseSnapshot(
                subjects = listOf(SubjectEntity(1, "Математика")),
                students = listOf(StudentEntity(1, "Иван")),
                prices = listOf(PriceEntity(1, 1, amountMinor = 5000)), // 50 BYN
            ),
        )
        val settings = SettingsRepository(db.settingsDao(), RussianMinskPhone.toDevicePreferences())
        val mirror = CalendarMirror(
            db.classDao(), db.attendanceDao(), db.studentDao(), db.subjectDao(), db.groupDao(), noCalendar, settings,
            db.homeworkDao(),
        )
        repo = ClassRepository(
            db.classDao(), db.lessonDao(), db.attendanceDao(), mirror, RecordingReminderScheduler(), RoomTransactor(db),
        )
    }

    @After fun tearDown() = db.close()

    @Test fun pastClassAutoCompletesAtBookedPriceAndBecomesDebt() = runTest {
        val start = Instant.parse("2026-06-18T10:00:00Z")
        db.snapshotDao().insert(
            DatabaseSnapshot(
                lessons = listOf(
                    LessonEntity(1, 1, start.toEpochMilliseconds(), 60, LessonStatus.SCHEDULED),
                ),
                attendance = listOf(AttendanceEntity(1, 1, 1, Attended.EXPECTED, priceMinor = 5000)),
            ),
        )
        val now = start.plus((2 * 3600).seconds)

        val completed = repo.autoCompleteDue(now)

        assertEquals(1, completed.size)
        val klass = repo.getById(1)!!
        assertEquals(LessonStatus.COMPLETED, klass.status)
        assertEquals(Money(5000), klass.charge) // billed its booked price
        val balance = Billing.balanceOf(1, repo.getAll(), emptyList()).balance
        assertEquals(Money(5000), balance)
    }
}
