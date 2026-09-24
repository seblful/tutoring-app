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
import com.seblful.klas.data.local.DatabaseSnapshot
import com.seblful.klas.data.local.RoomTransactor
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import kotlinx.coroutines.test.runTest
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Verifies that class mutations mirror into the calendar (criterion 5: move updates the event). */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class ClassCalendarSyncTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    /** Records calendar operations; hands back a stable id so updates reuse the same event. */
    private class FakeCalendarSync : CalendarSync {
        val upserts = mutableListOf<TutoringClass>()
        val refreshes = mutableListOf<TutoringClass>()
        val deletes = mutableListOf<String>()
        private var nextId = 100L
        override suspend fun upsert(klass: TutoringClass, title: String, guest: Guest?, reminderLeadMinutes: Int): String {
            upserts += klass
            return klass.calendarEventId ?: (nextId++).toString()
        }
        /** Only ever an update in place, so a refreshed lesson is recorded apart from an upserted one. */
        override suspend fun refresh(klass: TutoringClass, title: String) {
            if (klass.calendarEventId != null) refreshes += klass
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
        db.snapshotDao().insert(
            DatabaseSnapshot(
                subjects = listOf(SubjectEntity(1, "Математика")),
                students = listOf(StudentEntity(1, "Иван")),
            ),
        )
        calendar = FakeCalendarSync()
        val settings = SettingsRepository(db.settingsDao(), RussianMinskPhone)
        settings.update { it.copy(calendarSyncEnabled = true) } // calendar mirroring is off unless enabled
        val mirror = CalendarMirror(
            db.classDao(), db.attendanceDao(), db.studentDao(), db.subjectDao(), db.groupDao(), calendar, settings,
        )
        repo = ClassRepository(
            db.classDao(), db.lessonDao(), db.attendanceDao(), mirror, RecordingReminderScheduler(), RoomTransactor(db),
        )
    }

    @After fun tearDown() = db.close()

    @Test fun addingScheduledClassCreatesEventAndPersistsItsId() = runTest {
        val id = book(Instant.parse("2026-07-01T13:00:00Z"))
        assertEquals(1, calendar.upserts.size)
        assertEquals("100", repo.getById(id)?.calendarEventId)
    }

    @Test fun movingClassUpdatesTimeAndTheSyncedEvent() = runTest {
        val id = book(Instant.parse("2026-07-01T13:00:00Z"))
        calendar.upserts.clear()

        val now = Instant.parse("2026-07-02T09:00:00Z")
        val newStart = Instant.parse("2026-07-03T16:00:00Z")
        repo.move(session(id), newStart, now)

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
        val id = book(start, price = Money(5000))
        repo.setStatus(session(id), LessonStatus.COMPLETED)
        calendar.upserts.clear()

        val now = Instant.parse("2026-07-05T09:00:00Z")
        val newStart = Instant.parse("2026-07-10T10:00:00Z")
        val result = repo.move(session(id), newStart, now).written().single()

        assertEquals(newStart, result.start)
        assertEquals(LessonStatus.SCHEDULED, repo.getById(id)?.status)
        assertTrue(repo.getById(id)?.charge?.isZero == true) // charge voided
        assertEquals(1, calendar.upserts.size) // event re-synced
    }

    @Test fun movingAClassToThePastKeepsItsStatus() = runTest {
        val start = Instant.parse("2026-07-10T13:00:00Z")
        val id = book(start, price = Money(5000))
        repo.setStatus(session(id), LessonStatus.COMPLETED)

        calendar.upserts.clear()

        val now = Instant.parse("2026-07-15T09:00:00Z")
        val newStart = Instant.parse("2026-07-12T10:00:00Z") // still in the past relative to now
        repo.move(session(id), newStart, now)

        assertEquals(newStart, repo.getById(id)?.start)
        assertEquals(LessonStatus.COMPLETED, repo.getById(id)?.status) // status unchanged
        assertEquals(Money(5000), repo.getById(id)?.charge) // charge kept
        // The event follows the correction without being re-created: a lesson already taught is
        // refreshed, never upserted (`CalendarSyncPolicy.Action.REFRESH`).
        assertTrue(calendar.upserts.isEmpty())
        assertEquals(newStart, calendar.refreshes.last().start)
    }

    @Test fun cancellingClassDeletesItsEvent() = runTest {
        val id = book(Instant.parse("2026-07-01T13:00:00Z"))

        repo.setStatus(session(id), LessonStatus.CANCELLED)

        assertTrue(calendar.deletes.contains("100"))
        assertNull(repo.getById(id)?.calendarEventId)
    }

    /** Books one individual lesson (a session of one) and returns its class id. */
    private suspend fun book(start: Instant, price: Money = Money.ZERO): Long = repo.book(
        subjectId = 1,
        start = start,
        durationMinutes = 60,
        mode = ClassMode.IN_PERSON,
        studentIds = listOf(1L),
        price = price,
    ).written().single()

    /** The lesson class [id] belongs to. */
    private suspend fun session(id: Long) = repo.getSessionFor(id)!!
}
