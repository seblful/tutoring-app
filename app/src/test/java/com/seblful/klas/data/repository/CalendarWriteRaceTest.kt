package com.seblful.klas.data.repository

import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.calendar.Guest
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class CalendarWriteRaceTest {
    @get:Rule
    val env = TestEnv()

    private val start = Instant.parse("2026-06-01T10:00:00Z")

    private fun mirror(calendar: CalendarSync) = CalendarMirror(
        env.classDao, env.attendanceDao, env.studentDao, env.subjectDao, env.groupDao, calendar, env.settingsRepo,
    )

    @Test
    fun `a delayed event id cannot undo auto-completion billing`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calendar = object : CalendarSync by env.calendar {
            override suspend fun upsert(
                klass: TutoringClass,
                title: String,
                guest: Guest?,
                reminderLeadMinutes: Int,
            ): String {
                entered.complete(Unit)
                release.await()
                return "new-event"
            }
        }
        val repository = ClassRepository(
            env.classDao, env.lessonDao, env.attendanceDao, mirror(calendar), env.reminders, env.transactor,
        )
        val booking = async {
            repository.book(subject, start, 60, ClassMode.IN_PERSON, listOf(student), Money.ofMajor(30))
        }
        entered.await()
        try {
            env.classRepo.autoCompleteDue(start.plusSeconds(7200))
        } finally {
            release.complete(Unit)
        }
        val id = booking.await().written().single()
        val stored = env.classRepo.getById(id)!!

        assertEquals(LessonStatus.COMPLETED, stored.status)
        assertEquals(Attended.PRESENT, stored.attended)
        assertEquals(Money.ofMajor(30), stored.charge)
        assertEquals("new-event", stored.calendarEventId)
    }

    @Test
    fun `withdrawing an event cannot restore stale attendance or charge`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val id = env.fixtures.lesson(subject, listOf(student), start).single()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calendar = object : CalendarSync by env.calendar {
            override suspend fun delete(eventId: String) {
                entered.complete(Unit)
                release.await()
            }
        }
        val withdrawal = async { mirror(calendar).clear() }
        entered.await()
        try {
            env.fixtures.setStatus(id, LessonStatus.COMPLETED)
        } finally {
            release.complete(Unit)
        }
        withdrawal.await()
        val stored = env.classRepo.getById(id)!!

        assertEquals(Attended.PRESENT, stored.attended)
        assertEquals(Money.ofMajor(30), stored.charge)
        assertNull(stored.calendarEventId)
    }
}
