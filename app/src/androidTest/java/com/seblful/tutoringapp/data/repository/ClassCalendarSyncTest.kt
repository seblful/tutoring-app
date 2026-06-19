package com.seblful.tutoringapp.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seblful.tutoringapp.calendar.CalendarSync
import com.seblful.tutoringapp.domain.ReminderScheduler
import com.seblful.tutoringapp.data.local.AppDatabase
import com.seblful.tutoringapp.data.local.StudentEntity
import com.seblful.tutoringapp.data.local.SubjectEntity
import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.TutoringClass
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Verifies that class mutations mirror into the calendar (criterion 5: move updates the event). */
@RunWith(AndroidJUnit4::class)
class ClassCalendarSyncTest {

    /** Records calendar operations; hands back a stable id so updates reuse the same event. */
    private class FakeCalendarSync : CalendarSync {
        val upserts = mutableListOf<TutoringClass>()
        val deletes = mutableListOf<String>()
        private var nextId = 100L
        override suspend fun isAvailable() = true
        override suspend fun upsert(klass: TutoringClass, title: String, guestEmail: String?): String {
            upserts += klass
            return klass.calendarEventId ?: (nextId++).toString()
        }
        override suspend fun delete(eventId: String) { deletes += eventId }
    }

    private lateinit var db: AppDatabase
    private lateinit var calendar: FakeCalendarSync
    private lateinit var repo: ClassRepository

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java,
        ).build()
        db.subjectDao().insertAll(listOf(SubjectEntity(1, "Математика")))
        db.studentDao().insertAll(listOf(StudentEntity(1, "Иван")))
        calendar = FakeCalendarSync()
        val settings = SettingsRepository(db.settingsDao())
        settings.setCalendarSyncEnabled(true) // calendar mirroring is off unless enabled
        repo = ClassRepository(
            db.classDao(), db.studentDao(), db.subjectDao(), calendar,
            ReminderScheduler { /* no-op in tests */ }, settings,
        )
    }

    @After fun tearDown() = db.close()

    @Test fun addingScheduledClassCreatesEventAndPersistsItsId() = runTest {
        val id = repo.add(scheduled(Instant.parse("2026-07-01T13:00:00Z")))
        assertEquals(1, calendar.upserts.size)
        assertEquals("100", repo.getById(id)?.calendarEventId)
    }

    @Test fun movingClassUpdatesTimeAndTheSyncedEvent() = runTest {
        val id = repo.add(scheduled(Instant.parse("2026-07-01T13:00:00Z")))
        calendar.upserts.clear()

        val now = Instant.parse("2026-07-02T09:00:00Z")
        val newStart = Instant.parse("2026-07-03T16:00:00Z")
        repo.move(id, newStart, now)

        // Time updated locally...
        assertEquals(newStart, repo.getById(id)?.start)
        // ...and the same calendar event was updated (reused id) to the new start.
        assertEquals(1, calendar.upserts.size)
        assertEquals(newStart, calendar.upserts.last().start)
        assertEquals("100", calendar.upserts.last().calendarEventId)
    }

    @Test fun movingACompletedClassToTheFutureReplansItAndVoidsTheCharge() = runTest {
        // Moving a billed class forward re-plans it: it becomes scheduled again, its charge is
        // voided, and its (future) event is re-created/updated.
        val start = Instant.parse("2026-07-01T13:00:00Z")
        val id = repo.add(scheduled(start).copy(price = Money(5000)))
        repo.setStatus(id, ClassStatus.COMPLETED)
        calendar.upserts.clear()

        val now = Instant.parse("2026-07-05T09:00:00Z")
        val newStart = Instant.parse("2026-07-10T10:00:00Z")
        val result = repo.move(id, newStart, now)

        assertEquals(newStart, result?.start)
        assertEquals(ClassStatus.SCHEDULED, repo.getById(id)?.status)
        assertTrue(repo.getById(id)?.charge?.isZero == true) // charge voided
        assertEquals(1, calendar.upserts.size) // event re-synced
    }

    @Test fun movingAClassToThePastKeepsItsStatus() = runTest {
        val start = Instant.parse("2026-07-10T13:00:00Z")
        val id = repo.add(scheduled(start).copy(price = Money(5000)))
        repo.setStatus(id, ClassStatus.COMPLETED)

        val now = Instant.parse("2026-07-15T09:00:00Z")
        val newStart = Instant.parse("2026-07-12T10:00:00Z") // still in the past relative to now
        repo.move(id, newStart, now)

        assertEquals(newStart, repo.getById(id)?.start)
        assertEquals(ClassStatus.COMPLETED, repo.getById(id)?.status) // status unchanged
        assertEquals(Money(5000), repo.getById(id)?.charge) // charge kept
    }

    @Test fun cancellingClassDeletesItsEvent() = runTest {
        val id = repo.add(scheduled(Instant.parse("2026-07-01T13:00:00Z")))

        repo.setStatus(id, ClassStatus.CANCELLED)

        assertTrue(calendar.deletes.contains("100"))
        assertNull(repo.getById(id)?.calendarEventId)
    }

    private fun scheduled(start: Instant) = TutoringClass(
        studentId = 1, subjectId = 1, start = start, durationMinutes = 60, status = ClassStatus.SCHEDULED,
    )
}
