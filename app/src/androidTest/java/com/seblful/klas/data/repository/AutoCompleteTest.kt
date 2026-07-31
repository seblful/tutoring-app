package com.seblful.klas.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.calendar.Guest
import com.seblful.klas.testutil.RussianMinskPhone
import com.seblful.klas.testutil.RecordingReminderScheduler
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.RoomTransactor
import com.seblful.klas.data.local.AttendanceEntity
import com.seblful.klas.data.local.LessonEntity
import com.seblful.klas.data.local.DatabaseSnapshot
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.Billing
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import kotlinx.coroutines.test.runTest
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Criterion 1: a class whose end has passed auto-completes and adds its then-current price to debt. */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class AutoCompleteTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    private val noCalendar = object : CalendarSync {
        override suspend fun upsert(klass: TutoringClass, title: String, guest: Guest?, reminderLeadMinutes: Int): String? = null
        override suspend fun delete(eventId: String) = Unit
    }

    private lateinit var db: AppDatabase
    private lateinit var repo: ClassRepository

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java,
        ).build()
        db.snapshotDao().insert(
            DatabaseSnapshot(
                subjects = listOf(SubjectEntity(1, "Математика")),
                students = listOf(StudentEntity(1, "Иван")),
                prices = listOf(PriceEntity(1, 1, amountMinor = 5000)), // 50 BYN
            ),
        )
        val settings = SettingsRepository(db.settingsDao(), RussianMinskPhone)
        val mirror = CalendarMirror(
            db.classDao(), db.attendanceDao(), db.studentDao(), db.subjectDao(), db.groupDao(), noCalendar, settings,
        )
        repo = ClassRepository(
            db.classDao(), db.lessonDao(), db.attendanceDao(), mirror, RecordingReminderScheduler(), RoomTransactor(db),
        )
    }

    @After fun tearDown() = db.close()

    @Test fun pastClassAutoCompletesAtBookedPriceAndBecomesDebt() = runTest {
        // A scheduled class that ended an hour ago, booked at 50 BYN.
        val start = Instant.parse("2026-06-18T10:00:00Z")
        db.snapshotDao().insert(
            DatabaseSnapshot(
                lessons = listOf(
                    LessonEntity(1, 1, start.toEpochMilli(), 60, LessonStatus.SCHEDULED.name),
                ),
                attendance = listOf(AttendanceEntity(1, 1, 1, Attended.EXPECTED.name, priceMinor = 5000)),
            ),
        )
        val now = start.plusSeconds(2 * 3600)

        val completed = repo.autoCompleteDue(now)

        assertEquals(1, completed.size)
        val klass = repo.getById(1)!!
        assertEquals(LessonStatus.COMPLETED, klass.status)
        assertEquals(Money(5000), klass.charge) // billed its booked price
        // ...and it shows up as the student's debt.
        val balance = Billing.balanceOf(1, repo.getAll(), emptyList()).balance
        assertEquals(Money(5000), balance)
    }
}
