package com.seblful.klas.data.repository

import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * What a mirrored event *shows*, and every table that decides it.
 *
 * These exist because the mirror was reachable from exactly one direction. An event's title is built
 * from the student's, the subject's and the group's name, and its guest from the student's email — four
 * tables — but only a write to `classes` ever triggered a mirror. So renaming anybody left the tutor's
 * calendar asserting the old name indefinitely, and no test noticed, because every test that mirrored
 * anything got there by booking a lesson.
 *
 * The first four tests below are that defect, one per way in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarMirrorTest {

    private val env = TestEnv()

    private val start: Instant = Instant.parse("2026-06-20T10:00:00Z")

    /** Sync on, one named student priced for one named subject, and one scheduled lesson mirrored. */
    private suspend fun mirroredLesson(groupId: Long? = null): Long {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true, emailInvitesEnabled = true) }
        env.studentDao.upsert(StudentEntity(id = STUDENT, name = "Аня", email = "anya@example.com"))
        env.subjectDao.upsert(SubjectEntity(id = SUBJECT, name = "Алгебра"))
        if (groupId != null) env.groupDao.upsert(GroupEntity(id = groupId, name = "11 «А»", subjectId = SUBJECT))
        return env.classRepo.book(
            subjectId = SUBJECT,
            start = start,
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            studentIds = listOf(STUDENT),
            price = Money.ofMajor(30),
            groupId = groupId,
        ).written().single()
    }

    private val lastTitle: String get() = env.calendar.calls.last().title

    @Test
    fun `renaming a student re-titles their mirrored events`() = runTest {
        mirroredLesson()
        assertEquals("Аня — Алгебра", lastTitle)

        env.studentRepo.upsert(Student(id = STUDENT, name = "Анна", email = "anya@example.com"))

        assertEquals("the rename must reach the calendar", "Анна — Алгебра", lastTitle)
    }

    @Test
    fun `correcting a student's email re-invites them at the new address`() = runTest {
        mirroredLesson()
        assertEquals("anya@example.com", env.calendar.lastGuest?.email)

        env.studentRepo.upsert(Student(id = STUDENT, name = "Аня", email = "anna@example.com"))

        assertEquals("anna@example.com", env.calendar.lastGuest?.email)
    }

    @Test
    fun `renaming a subject re-titles every event carrying its name`() = runTest {
        mirroredLesson()

        env.subjectRepo.upsert(Subject(id = SUBJECT, name = "Геометрия"))

        assertEquals("Аня — Геометрия", lastTitle)
    }

    @Test
    fun `renaming a group re-titles the events of its lessons`() = runTest {
        mirroredLesson(groupId = GROUP)
        assertEquals("Аня — Алгебра (11 «А»)", lastTitle)

        env.groupRepo.save(Group(id = GROUP, name = "11 «Б»", subjectId = SUBJECT), setOf(STUDENT))

        assertEquals("Аня — Алгебра (11 «Б»)", lastTitle)
    }

    /**
     * The refresh is driven off the classes that actually carry an event id, so a rename while sync is
     * off must not start mirroring anything — turning it on is what backfills, via `resyncAll`.
     */
    @Test
    fun `a rename mirrors nothing while sync is off`() = runTest {
        env.studentDao.upsert(StudentEntity(id = STUDENT, name = "Аня"))
        env.subjectDao.upsert(SubjectEntity(id = SUBJECT, name = "Алгебра"))

        env.studentRepo.upsert(Student(id = STUDENT, name = "Анна"))

        assertTrue(env.calendar.calls.isEmpty())
    }

    /** Relocated from `ClassRepositoryTest`: turning sync off withdraws every event and forgets its id. */
    @Test
    fun `clear removes every mirrored event and nulls the stored ids`() = runTest {
        mirroredLesson()
        env.classRepo.book(
            subjectId = SUBJECT,
            start = Instant.parse("2026-06-21T10:00:00Z"),
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            studentIds = listOf(STUDENT),
            price = Money.ofMajor(30),
        )
        assertEquals(2, env.calendar.upserts.size)

        env.calendarMirror.clear()

        assertEquals(2, env.calendar.deletes.size)
        assertTrue(env.classRepo.getAll().all { it.calendarEventId == null })
    }

    private companion object {
        const val STUDENT = 1L
        const val SUBJECT = 1L
        const val GROUP = 7L
    }
}
