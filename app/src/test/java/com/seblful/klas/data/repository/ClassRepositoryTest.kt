package com.seblful.klas.data.repository

import com.seblful.klas.calendar.Guest
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.domain.SessionEdit
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
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
    ).written().single()

    /** The lesson class [id] belongs to — a session of one, for an individual class. */
    private suspend fun session(id: Long) = repo.getSessionFor(id)!!

    private fun edit(price: Money = Money.ofMajor(30), notes: String = "") = SessionEdit(
        subjectId = 1,
        durationMinutes = 60,
        mode = ClassMode.IN_PERSON,
        price = price,
        notes = notes,
        studentId = STUDENT,
    )

    // --- The lesson's note --------------------------------------------------------------------------

    @Test
    fun `a note typed while booking is stored with the lesson`() = runTest {
        val id = repo.book(
            subjectId = 1,
            start = defaultStart,
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            studentIds = listOf(STUDENT),
            price = Money.ofMajor(30),
            notes = "§12, упр. 4–7",
        ).written().single()

        assertEquals("§12, упр. 4–7", repo.getById(id)!!.notes)
    }

    /** A lesson booked without one has no note, rather than a null the readers would have to handle. */
    @Test
    fun `a lesson booked without a note has a blank one`() = runTest {
        assertEquals("", repo.getById(book())!!.notes)
    }

    /**
     * The commonest way a note is written: after the lesson, on a row that already exists. It rides the
     * ordinary edit rather than a writer of its own, so it cannot bypass the rebill that edit owes.
     */
    @Test
    fun `a note can be added to a lesson that is already booked`() = runTest {
        val id = book()

        repo.apply(session(id), edit(notes = "Прошли квадратные уравнения"), defaultStart, now)

        assertEquals("Прошли квадратные уравнения", repo.getById(id)!!.notes)
    }

    @Test
    fun `an edit can clear a note back to nothing`() = runTest {
        val id = book()
        repo.apply(session(id), edit(notes = "черновик"), defaultStart, now)

        repo.apply(session(id), edit(notes = ""), defaultStart, now)

        assertEquals("", repo.getById(id)!!.notes)
    }

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

        repo.setStatus(session(id), LessonStatus.CANCELLED)

        assertEquals(listOf(eventId), env.calendar.deletes)
        assertNull(repo.getById(id)!!.calendarEventId)
    }

    /**
     * Completing bills the class and **creates nothing** in the calendar. The event it already has is
     * refreshed rather than left frozen (`CalendarSyncPolicy.Action.REFRESH`), which is what keeps a
     * note written after the lesson — the usual moment — from stopping at the database.
     */
    @Test
    fun `completing a class captures the charge and creates no calendar event`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val id = book() // 1 upsert so far
        env.calendar.calls.clear()

        val completed = repo.setStatus(session(id), LessonStatus.COMPLETED).written().single()

        assertEquals(Money.ofMajor(30), completed.charge)
        assertTrue("a past lesson never earns a new event", env.calendar.upserts.isEmpty())
        assertTrue(env.calendar.deletes.isEmpty())
        assertEquals("the one it has is kept in step", 1, env.calendar.refreshes.size)
    }

    /**
     * **A note written after the lesson reaches the calendar.** The sequence is the everyday one and
     * every step of it used to end here: the hour passes, auto-completion marks it taught, and only
     * then does the tutor write down what was set for home.
     */
    @Test
    fun `a note added to a completed lesson refreshes its event`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val id = book()
        repo.setStatus(session(id), LessonStatus.COMPLETED)
        env.calendar.refreshes.clear()
        env.calendar.calls.clear() // the booking's own upsert, which is not what this is about

        repo.apply(session(id), edit(notes = "§12, упр. 4–7"), defaultStart, now)

        assertEquals(
            "the note the tutor typed is what the event is refreshed with",
            "§12, упр. 4–7",
            env.calendar.refreshes.last().klass.notes,
        )
        assertTrue("and no second event was invented for a past hour", env.calendar.upserts.isEmpty())
    }

    /**
     * A lesson with no event — booked while sync was off, or simply never mirrored — gains none by
     * being edited after it is over. The fake enforces the adapter's own guard, so this is the rule
     * "history is kept true, never written" reaching all the way down.
     */
    @Test
    fun `editing a completed lesson that was never mirrored touches nothing`() = runTest {
        val id = book() // sync off: nothing mirrored
        repo.setStatus(session(id), LessonStatus.COMPLETED)
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }

        repo.apply(session(id), edit(notes = "написано после"), defaultStart, now)

        assertTrue(env.calendar.upserts.isEmpty())
        assertTrue(env.calendar.refreshes.isEmpty())
        assertTrue(env.calendar.deletes.isEmpty())
    }

    /**
     * Moving a lesson that has already been taught — correcting the hour it was actually at — carries
     * its event with it. Under the old rule the database moved and the calendar stayed where it was.
     */
    @Test
    fun `correcting a completed lesson's hour carries its event`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val id = book()
        repo.setStatus(session(id), LessonStatus.COMPLETED)
        env.calendar.refreshes.clear()
        val corrected = defaultStart.plusSeconds(3600)

        repo.move(session(id), corrected, now).written()

        assertEquals(corrected, env.calendar.refreshes.last().klass.start)
    }

    @Test
    fun `deleting a completed class removes it and deletes its lingering calendar event`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val id = book()
        // Completing keeps the (past) event; its id stays on the class.
        repo.setStatus(session(id), LessonStatus.COMPLETED)
        val eventId = repo.getById(id)!!.calendarEventId!!
        env.calendar.deletes.clear()

        repo.delete(session(id))

        assertNull("the completed class is gone — its recognized income disappears with it", repo.getById(id))
        assertEquals("its calendar event is cleaned up", listOf(eventId), env.calendar.deletes)
    }

    @Test
    fun `moving a class to the future re-plans it back to scheduled with no charge`() = runTest {
        val id = book(now.minusSeconds(3600))
        repo.setStatus(session(id), LessonStatus.COMPLETED)

        val moved = repo.move(session(id), now.plusSeconds(86_400), now = now).written().single()

        assertEquals(LessonStatus.SCHEDULED, moved.status)
        assertTrue(moved.charge.isZero)
    }

    @Test
    fun `an edit re-bills a completed class instead of keeping its old charge`() = runTest {
        val past = now.minusSeconds(7200)
        val id = book(past)
        repo.setStatus(session(id), LessonStatus.COMPLETED)
        assertEquals(Money.ofMajor(30), repo.getById(id)!!.charge)

        // Same (past) start, new rate: the class stays completed and its income follows the edit.
        repo.apply(session(id), edit(price = Money.ofMajor(50)), newStart = past, now = now)

        val after = repo.getById(id)!!
        assertEquals(LessonStatus.COMPLETED, after.status)
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
        assertEquals(LessonStatus.COMPLETED, repo.getById(due)?.status)
        assertEquals(LessonStatus.SCHEDULED, repo.getById(future)?.status)
        assertEquals(Money.ofMajor(30), repo.getById(due)?.charge)
    }

    @Test
    fun `every write asks for a reminder refresh, exactly once`() = runTest {
        val id = book()                                                       // 1
        repo.apply(session(id), edit(), newStart = defaultStart, now = now)    // 2
        repo.setStatus(session(id), LessonStatus.CANCELLED)                     // 3
        repo.move(session(id), now.plusSeconds(3600), now = now)               // 4
        repo.delete(session(id))                                              // 5
        assertEquals(5, env.reminders.refreshCount)
    }

    // --- One tutor, one lesson at a time: the writes refuse a taken hour themselves -----------

    @Test
    fun `booking over a lesson that is already there is refused, with the clash named`() = runTest {
        val first = book()

        val outcome = repo.book(
            subjectId = 1,
            start = defaultStart.plusSeconds(30 * 60),
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            studentIds = listOf(STUDENT),
            price = Money.ofMajor(30),
        )

        assertTrue(outcome is LessonWrite.HourTaken)
        assertEquals(
            "the clash is the lesson in the way, as data",
            first,
            (outcome as LessonWrite.HourTaken).clash.first.id,
        )
        assertEquals("nothing was written", 1, env.classDao.items.value.size)
    }

    @Test
    fun `moving a lesson onto an occupied hour is refused and moves nothing`() = runTest {
        val standing = book()
        val moving = book(defaultStart.plusSeconds(2 * 3600))

        val outcome = repo.move(session(moving), defaultStart.plusSeconds(15 * 60), now = now)

        assertTrue(outcome is LessonWrite.HourTaken)
        assertEquals(standing, (outcome as LessonWrite.HourTaken).clash.first.id)
        assertEquals("the lesson stayed put", defaultStart.plusSeconds(2 * 3600), repo.getById(moving)?.start)
    }

    @Test
    fun `an edit is refused when its new time is another lesson's`() = runTest {
        book() // holds 10:00–11:00
        val edited = book(defaultStart.plusSeconds(2 * 3600))

        val outcome = repo.apply(session(edited), edit(), newStart = defaultStart, now = now)

        assertTrue(outcome is LessonWrite.HourTaken)
        assertEquals(defaultStart.plusSeconds(2 * 3600), repo.getById(edited)?.start)
    }

    @Test
    fun `a lesson never stands in its own way`() = runTest {
        val id = book()

        val outcome = repo.move(session(id), defaultStart.plusSeconds(10 * 60), now = now)

        assertTrue("overlapping its own old span is not a clash", outcome is LessonWrite.Written<*>)
        assertEquals(defaultStart.plusSeconds(10 * 60), repo.getById(id)?.start)
    }

    /**
     * A stored class with no positive duration occupies no time by its own arithmetic, so it can
     * stand in nobody's way — and `Scheduling.Slot` refuses to exist for it, so asking would throw.
     * Only a hand-made row can hold one (the backup gate refuses them), which is why it is seeded
     * through the DAO: the repositories cannot create the state this guards against.
     */
    @Test
    fun `a status change on a zero-length stored row is an answer, not a crash`() = runTest {
        val id = env.fixtures.storedClass(
                studentId = STUDENT,
                subjectId = 1,
                startMillis = defaultStart.toEpochMilli(),
                durationMinutes = 0,
                status = LessonStatus.CANCELLED.name,
            )

        val outcome = repo.setStatus(session(id), LessonStatus.SCHEDULED)

        assertTrue("answered, not thrown", outcome is LessonWrite.Written<*>)
        assertEquals(LessonStatus.SCHEDULED, repo.getById(id)?.status)
    }

    private companion object {
        const val STUDENT = 1L
    }
}
