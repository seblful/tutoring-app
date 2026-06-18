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
        override suspend fun upsert(klass: TutoringClass, title: String): String {
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
        repo = ClassRepository(
            db.classDao(), db.priceDao(), db.studentDao(), db.subjectDao(), calendar,
            ReminderScheduler { /* no-op in tests */ },
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

        val newStart = Instant.parse("2026-07-03T16:00:00Z")
        repo.move(id, newStart)

        // Time updated locally...
        assertEquals(newStart, repo.getById(id)?.start)
        // ...and the same calendar event was updated (reused id) to the new start.
        assertEquals(1, calendar.upserts.size)
        assertEquals(newStart, calendar.upserts.last().start)
        assertEquals("100", calendar.upserts.last().calendarEventId)
    }

    @Test fun movingACompletedClassIsRefusedAndLeavesItUnchanged() = runTest {
        // BUG-9 guard: a completed class has already billed; moving it would relocate that income
        // to another month, so ClassRepository.move must refuse it.
        val start = Instant.parse("2026-07-01T13:00:00Z")
        val id = repo.add(scheduled(start))
        repo.setStatus(id, ClassStatus.COMPLETED)
        calendar.upserts.clear()

        val result = repo.move(id, Instant.parse("2026-07-10T10:00:00Z"))

        assertNull(result) // refused
        assertEquals(start, repo.getById(id)?.start) // start unchanged
        assertEquals(ClassStatus.COMPLETED, repo.getById(id)?.status)
        assertTrue(calendar.upserts.isEmpty()) // no calendar change
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
