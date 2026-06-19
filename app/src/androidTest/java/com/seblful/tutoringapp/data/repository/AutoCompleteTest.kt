package com.seblful.tutoringapp.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seblful.tutoringapp.calendar.CalendarSync
import com.seblful.tutoringapp.domain.ReminderScheduler
import com.seblful.tutoringapp.data.local.AppDatabase
import com.seblful.tutoringapp.data.local.ClassEntity
import com.seblful.tutoringapp.data.local.PriceEntity
import com.seblful.tutoringapp.data.local.StudentEntity
import com.seblful.tutoringapp.data.local.SubjectEntity
import com.seblful.tutoringapp.domain.Billing
import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.TutoringClass
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Criterion 1: a class whose end has passed auto-completes and adds its then-current price to debt. */
@RunWith(AndroidJUnit4::class)
class AutoCompleteTest {

    private val noCalendar = object : CalendarSync {
        override suspend fun isAvailable() = false
        override suspend fun upsert(klass: TutoringClass, title: String, guestEmail: String?, reminderLeadMinutes: Int): String? = null
        override suspend fun delete(eventId: String) = Unit
    }

    private lateinit var db: AppDatabase
    private lateinit var repo: ClassRepository

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java,
        ).build()
        db.subjectDao().insertAll(listOf(SubjectEntity(1, "Математика")))
        db.studentDao().insertAll(listOf(StudentEntity(1, "Иван")))
        db.priceDao().insertAll(listOf(PriceEntity(1, 1, amountMinor = 5000))) // 50 BYN
        repo = ClassRepository(
            db.classDao(), db.studentDao(), db.subjectDao(), noCalendar,
            ReminderScheduler { /* no-op in tests */ }, SettingsRepository(db.settingsDao()),
        )
    }

    @After fun tearDown() = db.close()

    @Test fun pastClassAutoCompletesAtBookedPriceAndBecomesDebt() = runTest {
        // A scheduled class that ended an hour ago, booked at 50 BYN.
        val start = Instant.parse("2026-06-18T10:00:00Z")
        db.classDao().insertAll(
            listOf(ClassEntity(1, 1, 1, start.toEpochMilli(), 60, priceMinor = 5000, status = ClassStatus.SCHEDULED.name)),
        )
        val now = start.plusSeconds(2 * 3600)

        val completed = repo.autoCompleteDue(now)

        assertEquals(1, completed.size)
        val klass = repo.getById(1)!!
        assertEquals(ClassStatus.COMPLETED, klass.status)
        assertEquals(Money(5000), klass.charge) // billed its booked price
        // ...and it shows up as the student's debt.
        val balance = Billing.balanceOf(1, repo.getAll(), emptyList()).balance
        assertEquals(Money(5000), balance)
    }
}
