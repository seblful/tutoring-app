package com.seblful.klas.data.repository

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
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.LessonEntity
import com.seblful.klas.data.local.PaymentEntity
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.data.local.RoomTransactor
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.StudentSubjectCrossRef
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.data.toDomain
import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.time.WallClock
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

/**
 * What a subject's deletion is allowed to take with it, against the platform's own SQLite — the foreign
 * keys being the point.
 *
 * Deleting a subject used to CASCADE-delete its classes — including COMPLETED ones (earned income) —
 * while payments survived, flipping an owing student into a false credit. [SubjectRepository.delete]
 * refuses a subject that still has classes, that a group is taught, or that a student takes (each for its
 * own reason), and takes only its rate log with it. (Needs a device/emulator for Room.)
 */
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
        ): String? = null
        override suspend fun refresh(klass: TutoringClass, title: String) = Unit
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
        // Sync stays off in this fixture, so the mirror is reached and returns immediately: what
        // is under test here is the foreign-key behaviour, not the calendar.
        val mirror = CalendarMirror(
            db.classDao(), db.attendanceDao(), db.studentDao(), db.subjectDao(), db.groupDao(),
            noCalendar, SettingsRepository(db.settingsDao(), RussianMinskPhone),
        )
        // The real repositories, because the guards being tested are theirs: "is this subject still
        // taught" is a question about classes and about groups, and each is answered by whoever owns it.
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
        // A completed 50 BYN lesson with a 20 BYN payment -> student owes 30 BYN.
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
                    PaymentEntity(1, 1, Instant.parse("2026-05-10T12:00:00Z").toEpochMilliseconds(), amountMinor = 2000),
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

    /**
     * The other reference a subject can have: `student_groups.subjectId` is `ON DELETE RESTRICT`.
     *
     * A brand-new group with no lessons of its own is enough to reach it — the class count is 0, so
     * until the group was checked too the delete went through to SQLite and threw a constraint
     * violation from inside the `viewModelScope.launch` it was called in, which reaches the tutor as a
     * crash rather than as a refusal. This is the half of the guard a JVM test cannot show: the fake
     * DAOs have no foreign keys, so only a real database says no.
     */
    @Test fun deletingSubjectTaughtToAGroupIsBlocked() = runTest {
        db.groupDao().upsert(GroupEntity(name = "Физика, 11 класс", subjectId = 2, priceMinor = 2000))

        val deleted = subjects.delete(Subject(id = 2, name = "Физика"))

        // Named apart from HasClasses: "remove its lessons first" is no help when the blocker is a
        // group that has taught none.
        assertEquals(SubjectRepository.Delete.TaughtToGroup, deleted)
        assertEquals(2, db.snapshotDao().read().subjects.size) // both subjects still there
    }

    /**
     * A student who takes the subject, with no lesson in it yet. Their price and the link cascade with the
     * subject, so before this was checked the delete went through and quietly took the subject off them.
     */
    @Test fun deletingSubjectAStudentTakesIsBlocked() = runTest {
        db.priceDao().upsert(PriceEntity(1, 2, amountMinor = 4000))
        db.studentDao().addSubjectLink(StudentSubjectCrossRef(studentId = 1, subjectId = 2))

        val deleted = subjects.delete(Subject(id = 2, name = "Физика"))

        assertEquals(SubjectRepository.Delete.TakenByStudents, deleted)
        val rows = db.snapshotDao().read()
        assertEquals(2, rows.subjects.size) // both subjects still there
        assertEquals(2, rows.prices.size) // and the student still priced for both
    }

    /**
     * `price_history` cascades from all three of its parents (unlike `classes`, which restricts):
     * it is a log *about* a price, not income, so a subject that is still deletable — one with no
     * lessons — must take its rate log with it rather than leave rows describing a price for
     * something that no longer exists.
     */
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
