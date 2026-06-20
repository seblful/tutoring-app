package com.seblful.repetit.data.repository

import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.TutoringClass
import com.seblful.repetit.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Exercises the side effects [ClassRepository] owns — calendar mirroring and reminder refresh —
 * which the instrumented tests can only check on a device. Uses fake DAOs + fake ports.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClassRepositoryTest {

    private val env = TestEnv()
    private val repo = env.classRepo

    private fun scheduled(start: Instant = Instant.parse("2026-06-20T10:00:00Z")) = TutoringClass(
        studentId = 1, subjectId = 1, start = start, durationMinutes = 60,
        price = Money.ofMajor(30), mode = ClassMode.IN_PERSON,
    )

    @Test
    fun `add persists the class and refreshes reminders, but skips the calendar while sync is off`() = runTest {
        val id = repo.add(scheduled())
        assertNotNull(repo.getById(id))
        assertEquals(1, env.reminders.refreshCount)
        assertTrue("calendar sync is off by default", env.calendar.upserts.isEmpty())
    }

    @Test
    fun `with sync on, adding a scheduled class mirrors it and stores the returned event id`() = runTest {
        env.settingsRepo.setCalendarSyncEnabled(true)
        val id = repo.add(scheduled())

        assertEquals(1, env.calendar.upserts.size)
        assertNotNull("the calendar event id is persisted back onto the class", repo.getById(id)?.calendarEventId)
    }

    @Test
    fun `cancelling a synced class deletes its calendar event`() = runTest {
        env.settingsRepo.setCalendarSyncEnabled(true)
        val id = repo.add(scheduled())
        val eventId = repo.getById(id)!!.calendarEventId!!

        repo.setStatus(id, ClassStatus.CANCELLED)

        assertEquals(listOf(eventId), env.calendar.deletes)
        assertNull(repo.getById(id)!!.calendarEventId)
    }

    @Test
    fun `completing a class captures the charge and leaves the calendar event untouched`() = runTest {
        env.settingsRepo.setCalendarSyncEnabled(true)
        val id = repo.add(scheduled()) // 1 upsert so far
        env.calendar.upserts.clear()

        val completed = repo.setStatus(id, ClassStatus.COMPLETED)

        assertEquals(Money.ofMajor(30), completed?.charge)
        assertTrue("a completed (past) class's event is history, not re-written", env.calendar.upserts.isEmpty())
        assertTrue(env.calendar.deletes.isEmpty())
    }

    @Test
    fun `moving a class to the future re-plans it back to scheduled with no charge`() = runTest {
        val id = repo.add(scheduled(Instant.now().minusSeconds(3600)))
        repo.setStatus(id, ClassStatus.COMPLETED)

        val moved = repo.move(id, Instant.now().plusSeconds(86_400))

        assertEquals(ClassStatus.SCHEDULED, moved?.status)
        assertTrue(moved!!.charge.isZero)
    }

    @Test
    fun `autoCompleteDue completes only scheduled classes whose end has passed`() = runTest {
        val now = Instant.parse("2026-06-20T12:00:00Z")
        val due = repo.add(scheduled(now.minusSeconds(7200)))     // ended an hour ago
        val future = repo.add(scheduled(now.plusSeconds(7200)))   // not yet started

        val completed = repo.autoCompleteDue(now)

        assertEquals(listOf(due), completed.map { it.id })
        assertEquals(ClassStatus.COMPLETED, repo.getById(due)?.status)
        assertEquals(ClassStatus.SCHEDULED, repo.getById(future)?.status)
        assertEquals(Money.ofMajor(30), repo.getById(due)?.charge)
    }

    @Test
    fun `clearCalendar removes every mirrored event and nulls the stored ids`() = runTest {
        env.settingsRepo.setCalendarSyncEnabled(true)
        repo.add(scheduled())
        repo.add(scheduled(Instant.parse("2026-06-21T10:00:00Z")))
        assertEquals(2, env.calendar.upserts.size)

        repo.clearCalendar()

        assertEquals(2, env.calendar.deletes.size)
        assertTrue(repo.getAll().all { it.calendarEventId == null })
    }

    @Test
    fun `every write asks for a reminder refresh`() = runTest {
        val id = repo.add(scheduled())          // 1
        repo.update(repo.getById(id)!!)         // 2
        repo.setStatus(id, ClassStatus.CANCELLED) // 3
        repo.move(id, Instant.now().plusSeconds(3600)) // 4
        repo.delete(repo.getById(id)!!)         // 5
        assertEquals(5, env.reminders.refreshCount)
    }
}
