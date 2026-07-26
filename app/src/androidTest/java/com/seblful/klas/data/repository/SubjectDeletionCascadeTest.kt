package com.seblful.klas.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.ClassEntity
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
 * BUG-1 (HIGH) regression: deleting a subject used to CASCADE-delete its classes — including
 * COMPLETED ones (earned income) — while payments survived, flipping an owing student into a
 * false credit. [SubjectRepository.delete] now refuses to delete a subject that still has
 * classes, so the history is preserved. (Needs a device/emulator for Room.)
 */
@RunWith(AndroidJUnit4::class)
class SubjectDeletionCascadeTest {

    private lateinit var db: AppDatabase
    private lateinit var subjects: SubjectRepository

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java,
        ).build()
        db.subjectDao().insertAll(listOf(SubjectEntity(1, "Математика"), SubjectEntity(2, "Физика")))
        db.studentDao().insertAll(listOf(StudentEntity(1, "Иван")))
        db.priceDao().insertAll(listOf(PriceEntity(1, 1, amountMinor = 5000)))
        subjects = SubjectRepository(db.subjectDao(), db.classDao())
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

        assertFalse(deleted) // refused
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

        assertTrue(deleted)
        assertEquals(1, db.subjectDao().getAll().size) // only Математика remains
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

        assertTrue(deleted)
        assertTrue(db.rateHistoryDao().getAll().isEmpty()) // the log went with the subject
        assertEquals(1, db.priceDao().getAll().size) // only the Математика price remains
    }
}
