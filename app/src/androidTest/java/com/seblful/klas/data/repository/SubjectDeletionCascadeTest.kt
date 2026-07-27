package com.seblful.klas.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.calendar.Guest
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.data.repository.CalendarMirror
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.PaymentEntity
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.data.toDomain
import com.seblful.klas.domain.Billing
import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Subject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * What a subject's deletion is allowed to take with it, against a real database — the foreign keys
 * being the point, and the fake DAOs having none.
 *
 * Deleting a subject used to CASCADE-delete its classes — including COMPLETED ones (earned income) —
 * while payments survived, flipping an owing student into a false credit. [SubjectRepository.delete]
 * refuses a subject that still has classes, or that a group is taught (each for its own reason), and
 * takes only its rate log with it. (Needs a device/emulator for Room.)
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
        override suspend fun delete(eventId: String) = Unit
    }

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java,
        ).build()
        db.subjectDao().insertAll(listOf(SubjectEntity(1, "Математика"), SubjectEntity(2, "Физика")))
        db.studentDao().insertAll(listOf(StudentEntity(1, "Иван")))
        db.priceDao().insertAll(listOf(PriceEntity(1, 1, amountMinor = 5000)))
        // Sync stays off in this fixture, so the mirror is reached and returns immediately: what
        // is under test here is the foreign-key behaviour, not the calendar.
        val mirror = CalendarMirror(
            db.classDao(), db.studentDao(), db.subjectDao(), db.groupDao(),
            noCalendar, SettingsRepository(db.settingsDao()),
        )
        subjects = SubjectRepository(db.subjectDao(), db.classDao(), db.groupDao(), mirror)
    }

    @After fun tearDown() = db.close()

    @Test fun deletingSubjectWithClassesIsBlockedAndPreservesIncome() = runTest {
        // A completed 50 BYN lesson with a 20 BYN payment -> student owes 30 BYN.
        db.classDao().insertAll(
            listOf(
                ClassEntity(
                    1, 1, 1, Instant.parse("2026-05-10T10:00:00Z").toEpochMilli(), 60,
                    status = ClassStatus.COMPLETED.name, chargeMinor = 5000,
                ),
            ),
        )
        db.paymentDao().insertAll(
            listOf(PaymentEntity(1, 1, Instant.parse("2026-05-10T12:00:00Z").toEpochMilli(), amountMinor = 2000)),
        )

        val deleted = subjects.delete(Subject(id = 1, name = "Математика"))

        assertEquals(SubjectRepository.Delete.HasClasses, deleted) // refused, and says why
        assertEquals(1, db.classDao().getAll().size) // class (income) preserved
        assertEquals(
            Money(3000), // student still correctly owes 30 BYN
            Billing.balanceOf(
                1, db.classDao().getAll().map { it.toDomain() }, db.paymentDao().getAll().map { it.toDomain() },
            ).balance,
        )
    }

    @Test fun deletingSubjectWithoutClassesSucceeds() = runTest {
        val deleted = subjects.delete(Subject(id = 2, name = "Физика")) // no classes reference it

        assertEquals(SubjectRepository.Delete.Ok, deleted)
        assertEquals(1, db.subjectDao().getAll().size) // only Математика remains
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
        assertEquals(2, db.subjectDao().getAll().size) // both subjects still there
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
                atMillis = Instant.parse("2026-05-10T12:00:00Z").toEpochMilli(),
            ),
        )

        val deleted = subjects.delete(Subject(id = 2, name = "Физика")) // no classes reference it

        assertEquals(SubjectRepository.Delete.Ok, deleted)
        assertTrue(db.rateHistoryDao().getAll().isEmpty()) // the log went with the subject
        assertEquals(1, db.priceDao().getAll().size) // only the Математика price remains
    }
}
