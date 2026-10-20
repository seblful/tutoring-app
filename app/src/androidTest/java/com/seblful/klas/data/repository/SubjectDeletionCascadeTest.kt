package com.seblful.klas.data.repository

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seblful.klas.calendar.CalendarMirror
import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.calendar.Guest
import com.seblful.klas.data.billing.PaymentEntity
import com.seblful.klas.data.billing.PriceEntity
import com.seblful.klas.data.billing.RateChangeEntity
import com.seblful.klas.data.billing.RateHistoryRepository
import com.seblful.klas.data.lessons.AttendanceEntity
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.LessonEntity
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.DatabaseSnapshot
import com.seblful.klas.data.local.RoomTransactor
import com.seblful.klas.data.people.GroupEntity
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.StudentSubjectCrossRef
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.data.toDomain
import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.toDevicePreferences
import com.seblful.klas.testutil.RecordingReminderScheduler
import com.seblful.klas.testutil.RussianMinskPhone
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class SubjectDeletionCascadeTest {

    private lateinit var db: AppDatabase
    private lateinit var subjects: SubjectRepository

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

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java,
        )
            .setDriver(BundledSQLiteDriver())
            .build()
        db.snapshotDao().insert(
            DatabaseSnapshot(
                subjects = listOf(SubjectEntity(1, "Математика"), SubjectEntity(2, "Физика")),
                students = listOf(StudentEntity(1, "Иван")),
                prices = listOf(PriceEntity(1, 1, amountMinor = 5000)),
            ),
        )
        val mirror = CalendarMirror(
            db.classDao(), db.attendanceDao(), db.studentDao(), db.subjectDao(), db.groupDao(),
            noCalendar, SettingsRepository(db.settingsDao(), RussianMinskPhone.toDevicePreferences()),
            db.homeworkDao(), RoomTransactor(db), WallClock.system(),
        )
        val transactor = RoomTransactor(db)
        val classes = ClassRepository(
            db.classDao(), db.lessonDao(), db.attendanceDao(), mirror, RecordingReminderScheduler(), transactor,
        )
        val rateHistory = RateHistoryRepository(db.rateHistoryDao(), db.groupDao(), db.priceDao(), WallClock.system())
        val groups = GroupRepository(
            db.groupDao(), classes, db.studentDao(), rateHistory, transactor, mirror,
        )
        subjects = SubjectRepository(db.subjectDao(), classes, groups, db.studentDao(), mirror, transactor)
    }

    @After fun tearDown() = db.close()

    @Test fun deletingSubjectWithClassesIsBlockedAndPreservesIncome() = runTest {
        db.snapshotDao().insert(
            DatabaseSnapshot(
                lessons = listOf(
                    LessonEntity(
                        1, 1, Instant.parse("2026-05-10T10:00:00Z").toEpochMilliseconds(), 60,
                        LessonStatus.COMPLETED,
                    ),
                ),
                attendance = listOf(
                    AttendanceEntity(1, 1, 1, Attended.PRESENT, chargeMinor = 5000),
                ),
                payments = listOf(
                    PaymentEntity(
                        1,
                        1,
                        Instant.parse("2026-05-10T12:00:00Z").toEpochMilliseconds(),
                        amountMinor = 2000,
                    ),
                ),
            ),
        )

        val deleted = subjects.delete(Subject(id = 1, name = "Математика"))

        assertEquals(SubjectRepository.Delete.HasClasses, deleted) // refused, and says why
        val rows = db.snapshotDao().read()
        assertEquals(1, rows.lessons.size) // the lesson, and the income on it, preserved
        assertEquals(1, rows.attendance.size)
        assertEquals(
            Money(3000), // student still correctly owes 30 BYN
            Billing.balanceOf(
                1, db.classDao().getAll().map { it.toDomain() }, rows.payments.map { it.toDomain() },
            ).balance,
        )
    }

    @Test fun deletingSubjectWithoutClassesSucceeds() = runTest {
        val deleted = subjects.delete(Subject(id = 2, name = "Физика")) // no classes reference it

        assertEquals(SubjectRepository.Delete.Ok, deleted)
        assertEquals(1, db.snapshotDao().read().subjects.size) // only Математика remains
    }

    @Test fun deletingSubjectTaughtToAGroupIsBlocked() = runTest {
        db.groupDao().upsert(GroupEntity(name = "Физика, 11 класс", subjectId = 2, priceMinor = 2000))

        val deleted = subjects.delete(Subject(id = 2, name = "Физика"))

        assertEquals(SubjectRepository.Delete.TaughtToGroup, deleted)
        assertEquals(2, db.snapshotDao().read().subjects.size) // both subjects still there
    }

    @Test fun deletingSubjectAStudentTakesIsBlocked() = runTest {
        db.priceDao().upsert(PriceEntity(1, 2, amountMinor = 4000))
        db.studentDao().addSubjectLink(StudentSubjectCrossRef(studentId = 1, subjectId = 2))

        val deleted = subjects.delete(Subject(id = 2, name = "Физика"))

        assertEquals(SubjectRepository.Delete.TakenByStudents, deleted)
        val rows = db.snapshotDao().read()
        assertEquals(2, rows.subjects.size) // both subjects still there
        assertEquals(2, rows.prices.size) // and the student still priced for both
    }

    @Test fun deletingSubjectTakesItsRateLogWithIt() = runTest {
        db.priceDao().upsert(PriceEntity(1, 2, amountMinor = 4000))
        db.rateHistoryDao().insert(
            RateChangeEntity(
                studentId = 1, subjectId = 2, fromMinor = 3000, toMinor = 4000,
                atMillis = Instant.parse("2026-05-10T12:00:00Z").toEpochMilliseconds(),
            ),
        )

        val deleted = subjects.delete(Subject(id = 2, name = "Физика")) // no classes reference it

        assertEquals(SubjectRepository.Delete.Ok, deleted)
        val rows = db.snapshotDao().read()
        assertTrue(rows.rateHistory.isEmpty()) // the log went with the subject
        assertEquals(1, rows.prices.size) // only the Математика price remains
    }
}
