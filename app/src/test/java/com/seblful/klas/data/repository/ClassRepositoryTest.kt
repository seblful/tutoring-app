package com.seblful.klas.data.repository

import com.seblful.klas.calendar.Guest
import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.domain.lesson.SessionEdit
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ClassRepositoryTest {

    @get:Rule
    val env = TestEnv()
    private val repo = env.classRepo

    private val defaultStart: Instant = Instant.parse("2026-06-20T10:00:00Z")

    /**
     * The subject (id 1) and the students ([STUDENT], [OTHER]) the writes below name by id. They have to
     * exist: the database refuses a lesson for a subject or a student it does not hold.
     */
    @Before
    fun seed() = runTest {
        check(env.fixtures.subject() == 1L)
        check(env.fixtures.student() == STUDENT)
        check(env.fixtures.student("Боря") == OTHER)
    }

    private val now: Instant = Instant.parse("2026-06-20T12:00:00Z")

    private suspend fun book(start: Instant = defaultStart): Long = repo.book(
        subjectId = 1,
        start = start,
        durationMinutes = 60,
        mode = ClassMode.IN_PERSON,
        studentIds = listOf(STUDENT),
        price = Money.ofMajor(30),
    ).written().single()

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
     * correction made after the lesson — the usual moment — from stopping at the database.
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
     * A lesson with no event — booked while sync was off, or simply never mirrored — gains none by
     * being edited after it is over. The fake enforces the adapter's own guard, so this is the rule
     * "history is kept true, never written" reaching all the way down.
     */
    @Test
    fun `editing a completed lesson that was never mirrored touches nothing`() = runTest {
        val id = book() // sync off: nothing mirrored
        repo.setStatus(session(id), LessonStatus.COMPLETED)
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }

        repo.apply(session(id), edit(price = Money.ofMajor(35)), defaultStart, now)

        assertTrue(env.calendar.upserts.isEmpty())
        assertTrue(env.calendar.refreshes.isEmpty())
        assertTrue(env.calendar.deletes.isEmpty())
    }

    @Test
    fun `correcting a completed lesson's hour carries its event`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val id = book()
        repo.setStatus(session(id), LessonStatus.COMPLETED)
        env.calendar.refreshes.clear()
        val corrected = defaultStart.plus(3600.seconds)

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
        val id = book(now.minus(3600.seconds))
        repo.setStatus(session(id), LessonStatus.COMPLETED)

        val moved = repo.move(session(id), now.plus((86_400).seconds), now = now).written().single()

        assertEquals(LessonStatus.SCHEDULED, moved.status)
        assertTrue(moved.charge.isZero)
    }

    @Test
    fun `an edit re-bills a completed class instead of keeping its old charge`() = runTest {
        val past = now.minus(7200.seconds)
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
        val other = OTHER

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
        val due = book(now.minus(7200.seconds)) // ended an hour ago
        val future = book(now.plus(7200.seconds)) // not yet started

        val completed = repo.autoCompleteDue(now)

        assertEquals(listOf(due), completed.map { it.id })
        assertEquals(LessonStatus.COMPLETED, repo.getById(due)?.status)
        assertEquals(LessonStatus.SCHEDULED, repo.getById(future)?.status)
        assertEquals(Money.ofMajor(30), repo.getById(due)?.charge)
    }

    @Test
    fun `every write asks for a reminder refresh, exactly once`() = runTest {
        val id = book() // 1
        repo.apply(session(id), edit(), newStart = defaultStart, now = now) // 2
        repo.setStatus(session(id), LessonStatus.CANCELLED) // 3
        repo.move(session(id), now.plus(3600.seconds), now = now) // 4
        repo.delete(session(id)) // 5
        assertEquals(5, env.reminders.refreshCount)
    }

    // --- One tutor, one lesson at a time: the writes refuse a taken hour themselves -----------

    @Test
    fun `booking over a lesson that is already there is refused, with the clash named`() = runTest {
        val first = book()

        val outcome = repo.book(
            subjectId = 1,
            start = defaultStart.plus((30 * 60).seconds),
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
        assertEquals("nothing was written", 1, env.classes().size)
    }

    @Test
    fun `moving a lesson onto an occupied hour is refused and moves nothing`() = runTest {
        val standing = book()
        val moving = book(defaultStart.plus((2 * 3600).seconds))

        val outcome = repo.move(session(moving), defaultStart.plus((15 * 60).seconds), now = now)

        assertTrue(outcome is LessonWrite.HourTaken)
        assertEquals(standing, (outcome as LessonWrite.HourTaken).clash.first.id)
        assertEquals("the lesson stayed put", defaultStart.plus((2 * 3600).seconds), repo.getById(moving)?.start)
    }

    @Test
    fun `an edit is refused when its new time is another lesson's`() = runTest {
        book() // holds 10:00–11:00
        val edited = book(defaultStart.plus((2 * 3600).seconds))

        val outcome = repo.apply(session(edited), edit(), newStart = defaultStart, now = now)

        assertTrue(outcome is LessonWrite.HourTaken)
        assertEquals(defaultStart.plus((2 * 3600).seconds), repo.getById(edited)?.start)
    }

    @Test
    fun `a lesson never stands in its own way`() = runTest {
        val id = book()

        val outcome = repo.move(session(id), defaultStart.plus((10 * 60).seconds), now = now)

        assertTrue("overlapping its own old span is not a clash", outcome is LessonWrite.Written<*>)
        assertEquals(defaultStart.plus((10 * 60).seconds), repo.getById(id)?.start)
    }

    @Test
    fun `a status change on a zero-length stored row is an answer, not a crash`() = runTest {
        val id = env.fixtures.storedClass(
            studentId = STUDENT,
            subjectId = 1,
            startMillis = defaultStart.toEpochMilliseconds(),
            durationMinutes = 0,
            status = LessonStatus.CANCELLED,
        )

        val outcome = repo.setStatus(session(id), LessonStatus.SCHEDULED)

        assertTrue("answered, not thrown", outcome is LessonWrite.Written<*>)
        assertEquals(LessonStatus.SCHEDULED, repo.getById(id)?.status)
    }

    private companion object {
        const val STUDENT = 1L
        const val OTHER = 2L
    }
}
