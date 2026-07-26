package com.seblful.klas.data.repository

import com.seblful.klas.calendar.Guest
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.domain.SessionEdit
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
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

    /**
     * The moment these tests treat as "now" — two hours after [defaultStart], so a lesson booked at
     * the default start is in the past.
     *
     * Pinned, and passed explicitly to every write whose rules consult the clock, rather than left to
     * `Instant.now()`. A test that says `Instant.now().minusSeconds(3600)` decides on the hour the
     * suite happens to run at: it passes at 14:00 and can fail on a boundary, and when it does fail it
     * indicts the time of day rather than the behaviour it was written for.
     */
    private val now: Instant = Instant.parse("2026-06-20T12:00:00Z")

    /** Books one individual lesson at 30/h for an hour, and returns its class id. */
    private suspend fun book(start: Instant = defaultStart): Long = repo.book(
        subjectId = 1,
        start = start,
        durationMinutes = 60,
        mode = ClassMode.IN_PERSON,
        studentIds = listOf(STUDENT),
        price = Money.ofMajor(30),
    ).single()

    /** The lesson class [id] belongs to — a session of one, for an individual class. */
    private suspend fun session(id: Long) = repo.getSessionFor(id)!!

    private fun edit(price: Money = Money.ofMajor(30)) = SessionEdit(
        subjectId = 1,
        durationMinutes = 60,
        mode = ClassMode.IN_PERSON,
        price = price,
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
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val id = book()

        assertEquals(1, env.calendar.upserts.size)
        assertNotNull("the calendar event id is persisted back onto the class", repo.getById(id)?.calendarEventId)
    }

    @Test
    fun `a mirrored class invites the student when email invites are on`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        env.settingsRepo.update { it.copy(emailInvitesEnabled = true) }
        env.studentDao.upsert(StudentEntity(id = STUDENT, name = "Аня", email = "anya@example.com"))

        book()

        assertEquals(Guest("anya@example.com", invited = true), env.calendar.lastGuest)
    }

    @Test
    fun `syncing alone mails nobody — invites are off until the tutor opts in`() = runTest {
        // Turning sync on is a statement about the tutor's own calendar, not about writing to their
        // students' inboxes, so the mirror must leave the guest uninvited on a fresh install.
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        env.studentDao.upsert(StudentEntity(id = STUDENT, name = "Аня", email = "anya@example.com"))

        book()

        assertEquals(Guest("anya@example.com", invited = false), env.calendar.lastGuest)
    }

    @Test
    fun `with email invites off the student's address still travels, marked uninvited`() = runTest {
        // The address is what lets the calendar adapter *withdraw* an invitation it sent earlier, so
        // switching invites off must not degrade to "no guest" — that would leave it standing.
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        env.settingsRepo.update { it.copy(emailInvitesEnabled = false) }
        env.studentDao.upsert(StudentEntity(id = STUDENT, name = "Аня", email = "anya@example.com"))

        book()

        assertEquals(Guest("anya@example.com", invited = false), env.calendar.lastGuest)
    }

    @Test
    fun `a student with no email yields no guest at all`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        env.studentDao.upsert(StudentEntity(id = STUDENT, name = "Аня", email = ""))

        book()

        assertNull("nobody to invite and nobody to withdraw", env.calendar.lastGuest)
    }

    @Test
    fun `cancelling a synced class deletes its calendar event`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val id = book()
        val eventId = repo.getById(id)!!.calendarEventId!!

        repo.setStatus(session(id), ClassStatus.CANCELLED)

        assertEquals(listOf(eventId), env.calendar.deletes)
        assertNull(repo.getById(id)!!.calendarEventId)
    }

    @Test
    fun `completing a class captures the charge and leaves the calendar event untouched`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val id = book() // 1 upsert so far
        env.calendar.calls.clear()

        val completed = repo.setStatus(session(id), ClassStatus.COMPLETED).single()

        assertEquals(Money.ofMajor(30), completed.charge)
        assertTrue("a completed (past) class's event is history, not re-written", env.calendar.upserts.isEmpty())
        assertTrue(env.calendar.deletes.isEmpty())
    }

    @Test
    fun `deleting a completed class removes it and deletes its lingering calendar event`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
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
        val id = book(now.minusSeconds(3600))
        repo.setStatus(session(id), ClassStatus.COMPLETED)

        val moved = repo.move(session(id), now.plusSeconds(86_400), now = now).single()

        assertEquals(ClassStatus.SCHEDULED, moved.status)
        assertTrue(moved.charge.isZero)
    }

    @Test
    fun `an edit re-bills a completed class instead of keeping its old charge`() = runTest {
        val past = now.minusSeconds(7200)
        val id = book(past)
        repo.setStatus(session(id), ClassStatus.COMPLETED)
        assertEquals(Money.ofMajor(30), repo.getById(id)!!.charge)

        // Same (past) start, new rate: the class stays completed and its income follows the edit.
        repo.apply(session(id), edit(price = Money.ofMajor(50)), newStart = past, now = now)

        val after = repo.getById(id)!!
        assertEquals(ClassStatus.COMPLETED, after.status)
        assertEquals(Money.ofMajor(50), after.charge)
    }

    @Test
    fun `an edit updates the class in place, so income cannot be double-counted`() = runTest {
        val id = book()

        repo.apply(session(id), edit(price = Money.ofMajor(40)), newStart = defaultStart, now = now)

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
                price = Money.ofMajor(45),
                studentId = other,
            ),
            newStart = defaultStart,
            now = now,
        )

        val after = repo.getById(id)!!
        assertEquals("the new owner holds the class", other, after.studentId)
        assertEquals("at their own rate", Money.ofMajor(45), after.price)
    }

    @Test
    fun `autoCompleteDue completes only scheduled classes whose end has passed`() = runTest {
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
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        book()
        book(Instant.parse("2026-06-21T10:00:00Z"))
        assertEquals(2, env.calendar.upserts.size)

        repo.clearCalendar()

        assertEquals(2, env.calendar.deletes.size)
        assertTrue(repo.getAll().all { it.calendarEventId == null })
    }

    @Test
    fun `every write asks for a reminder refresh, exactly once`() = runTest {
        val id = book()                                                       // 1
        repo.apply(session(id), edit(), newStart = defaultStart, now = now)    // 2
        repo.setStatus(session(id), ClassStatus.CANCELLED)                     // 3
        repo.move(session(id), now.plusSeconds(3600), now = now)               // 4
        repo.delete(session(id))                                              // 5
        assertEquals(5, env.reminders.refreshCount)
    }

    private companion object {
        const val STUDENT = 1L
    }
}
