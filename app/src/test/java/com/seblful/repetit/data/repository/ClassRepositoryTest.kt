package com.seblful.repetit.data.repository

import com.seblful.repetit.calendar.Guest
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.domain.SessionEdit
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Money
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
 *
 * Every write takes a lesson, so an individual class is booked and changed as a session of one; the
 * group cases live in `ClassRepositoryGroupTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClassRepositoryTest {

    private val env = TestEnv()
    private val repo = env.classRepo

    private val defaultStart: Instant = Instant.parse("2026-06-20T10:00:00Z")

    /** Books one individual lesson at 30/h for an hour, and returns its class id. */
    private suspend fun book(start: Instant = defaultStart): Long = repo.book(
        subjectId = 1,
        start = start,
        durationMinutes = 60,
        mode = ClassMode.IN_PERSON,
        pricesByStudent = mapOf(STUDENT to Money.ofMajor(30)),
    ).single()

    /** The lesson class [id] belongs to — a session of one, for an individual class. */
    private suspend fun session(id: Long) = repo.getSessionFor(id)!!

    private fun edit(price: Money = Money.ofMajor(30)) = SessionEdit(
        subjectId = 1,
        durationMinutes = 60,
        mode = ClassMode.IN_PERSON,
        pricesByStudent = mapOf(STUDENT to price),
        studentId = STUDENT,
    )

    @Test
    fun `booking persists the class and refreshes reminders, but skips the calendar while sync is off`() = runTest {
        val id = book()
        assertNotNull(repo.getById(id))
        assertEquals(1, env.reminders.refreshCount)
        assertTrue("calendar sync is off by default", env.calendar.upserts.isEmpty())
    }

    @Test
    fun `with sync on, booking a scheduled class mirrors it and stores the returned event id`() = runTest {
        env.settingsRepo.setCalendarSyncEnabled(true)
        val id = book()

        assertEquals(1, env.calendar.upserts.size)
        assertNotNull("the calendar event id is persisted back onto the class", repo.getById(id)?.calendarEventId)
    }

    @Test
    fun `a mirrored class invites the student when email invites are on`() = runTest {
        env.settingsRepo.setCalendarSyncEnabled(true)
        env.studentDao.upsert(StudentEntity(id = STUDENT, name = "Аня", email = "anya@example.com"))

        book()

        assertEquals(Guest("anya@example.com", invited = true), env.calendar.lastGuest)
    }

    @Test
    fun `with email invites off the student's address still travels, marked uninvited`() = runTest {
        // The address is what lets the calendar adapter *withdraw* an invitation it sent earlier, so
        // switching invites off must not degrade to "no guest" — that would leave it standing.
        env.settingsRepo.setCalendarSyncEnabled(true)
        env.settingsRepo.setEmailInvitesEnabled(false)
        env.studentDao.upsert(StudentEntity(id = STUDENT, name = "Аня", email = "anya@example.com"))

        book()

        assertEquals(Guest("anya@example.com", invited = false), env.calendar.lastGuest)
    }

    @Test
    fun `a student with no email yields no guest at all`() = runTest {
        env.settingsRepo.setCalendarSyncEnabled(true)
        env.studentDao.upsert(StudentEntity(id = STUDENT, name = "Аня", email = ""))

        book()

        assertNull("nobody to invite and nobody to withdraw", env.calendar.lastGuest)
    }

    @Test
    fun `cancelling a synced class deletes its calendar event`() = runTest {
        env.settingsRepo.setCalendarSyncEnabled(true)
        val id = book()
        val eventId = repo.getById(id)!!.calendarEventId!!

        repo.setStatus(session(id), ClassStatus.CANCELLED)

        assertEquals(listOf(eventId), env.calendar.deletes)
        assertNull(repo.getById(id)!!.calendarEventId)
    }

    @Test
    fun `completing a class captures the charge and leaves the calendar event untouched`() = runTest {
        env.settingsRepo.setCalendarSyncEnabled(true)
        val id = book() // 1 upsert so far
        env.calendar.calls.clear()

        val completed = repo.setStatus(session(id), ClassStatus.COMPLETED).single()

        assertEquals(Money.ofMajor(30), completed.charge)
        assertTrue("a completed (past) class's event is history, not re-written", env.calendar.upserts.isEmpty())
        assertTrue(env.calendar.deletes.isEmpty())
    }

    @Test
    fun `deleting a completed class removes it and deletes its lingering calendar event`() = runTest {
        env.settingsRepo.setCalendarSyncEnabled(true)
        val id = book()
        // Completing keeps the (past) event; its id stays on the class.
        repo.setStatus(session(id), ClassStatus.COMPLETED)
        val eventId = repo.getById(id)!!.calendarEventId!!
        env.calendar.deletes.clear()

        repo.delete(session(id))

        assertNull("the completed class is gone — its recognized income disappears with it", repo.getById(id))
        assertEquals("its calendar event is cleaned up", listOf(eventId), env.calendar.deletes)
    }

    @Test
    fun `moving a class to the future re-plans it back to scheduled with no charge`() = runTest {
        val id = book(Instant.now().minusSeconds(3600))
        repo.setStatus(session(id), ClassStatus.COMPLETED)

        val moved = repo.move(session(id), Instant.now().plusSeconds(86_400)).single()

        assertEquals(ClassStatus.SCHEDULED, moved.status)
        assertTrue(moved.charge.isZero)
    }

    @Test
    fun `an edit re-bills a completed class instead of keeping its old charge`() = runTest {
        val past = Instant.now().minusSeconds(7200)
        val id = book(past)
        repo.setStatus(session(id), ClassStatus.COMPLETED)
        assertEquals(Money.ofMajor(30), repo.getById(id)!!.charge)

        // Same (past) start, new rate: the class stays completed and its income follows the edit.
        repo.apply(session(id), edit(price = Money.ofMajor(50)), newStart = past)

        val after = repo.getById(id)!!
        assertEquals(ClassStatus.COMPLETED, after.status)
        assertEquals(Money.ofMajor(50), after.charge)
    }

    @Test
    fun `an edit updates the class in place, so income cannot be double-counted`() = runTest {
        val id = book()

        repo.apply(session(id), edit(price = Money.ofMajor(40)), newStart = defaultStart)

        assertEquals(1, repo.getAll().size)
        assertEquals(Money.ofMajor(40), repo.getById(id)!!.price)
    }

    @Test
    fun `an individual class can be re-attributed to another student`() = runTest {
        val id = book()
        val other = 2L

        repo.apply(
            session(id),
            SessionEdit(
                subjectId = 1,
                durationMinutes = 60,
                mode = ClassMode.IN_PERSON,
                pricesByStudent = mapOf(other to Money.ofMajor(45)),
                studentId = other,
            ),
            newStart = defaultStart,
        )

        val after = repo.getById(id)!!
        assertEquals("the new owner holds the class", other, after.studentId)
        assertEquals("at their own rate", Money.ofMajor(45), after.price)
    }

    @Test
    fun `autoCompleteDue completes only scheduled classes whose end has passed`() = runTest {
        val now = Instant.parse("2026-06-20T12:00:00Z")
        val due = book(now.minusSeconds(7200))     // ended an hour ago
        val future = book(now.plusSeconds(7200))   // not yet started

        val completed = repo.autoCompleteDue(now)

        assertEquals(listOf(due), completed.map { it.id })
        assertEquals(ClassStatus.COMPLETED, repo.getById(due)?.status)
        assertEquals(ClassStatus.SCHEDULED, repo.getById(future)?.status)
        assertEquals(Money.ofMajor(30), repo.getById(due)?.charge)
    }

    @Test
    fun `clearCalendar removes every mirrored event and nulls the stored ids`() = runTest {
        env.settingsRepo.setCalendarSyncEnabled(true)
        book()
        book(Instant.parse("2026-06-21T10:00:00Z"))
        assertEquals(2, env.calendar.upserts.size)

        repo.clearCalendar()

        assertEquals(2, env.calendar.deletes.size)
        assertTrue(repo.getAll().all { it.calendarEventId == null })
    }

    @Test
    fun `every write asks for a reminder refresh, exactly once`() = runTest {
        val id = book()                                                   // 1
        repo.apply(session(id), edit(), newStart = defaultStart)           // 2
        repo.setStatus(session(id), ClassStatus.CANCELLED)                 // 3
        repo.move(session(id), Instant.now().plusSeconds(3600))            // 4
        repo.delete(session(id))                                          // 5
        assertEquals(5, env.reminders.refreshCount)
    }

    private companion object {
        const val STUDENT = 1L
    }
}
