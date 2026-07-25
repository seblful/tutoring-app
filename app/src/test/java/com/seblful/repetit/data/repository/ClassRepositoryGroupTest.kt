package com.seblful.repetit.data.repository

import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.SubjectEntity
import com.seblful.repetit.domain.SessionEdit
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Group
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Group lessons at the repository seam: one lesson is *n* per-student classes, billed individually,
 * moved and re-billed together, with the calendar mirrored per class and reminders refreshed once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClassRepositoryGroupTest {

    private val env = TestEnv()
    private val repo = env.classRepo

    private val start = Instant.parse("2026-09-01T09:00:00Z")

    private var ann = 0L
    private var bob = 0L
    private var algebra = 0L
    private var groupId = 0L

    private suspend fun seed() {
        ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        groupId = env.groupRepo.save(Group(name = "11 «А»"), setOf(ann, bob))
    }

    private suspend fun bookSession(
        at: Instant = start,
        prices: Map<Long, Money> = mapOf(ann to Money.ofMajor(30), bob to Money.ofMajor(25)),
    ): List<Long> = repo.addGroupSession(
        groupId = groupId,
        subjectId = algebra,
        start = at,
        durationMinutes = 60,
        mode = ClassMode.IN_PERSON,
        pricesByStudent = prices,
    )

    @Test
    fun `booking a group lesson writes one class per member, each at their own rate`() = runTest {
        seed()

        val ids = bookSession()

        assertEquals(2, ids.size)
        val session = repo.getSessionFor(ids.first())!!
        assertTrue(session.isGroup)
        assertEquals(groupId, session.groupId)
        assertEquals(2, session.size)
        assertEquals(
            mapOf(ann to Money.ofMajor(30), bob to Money.ofMajor(25)),
            session.classes.associate { it.studentId to it.price },
        )
        // Every member's class shares the lesson's time, subject and mode.
        assertTrue(session.classes.all { it.start == start && it.subjectId == algebra })
        assertTrue(session.classes.all { it.status == ClassStatus.SCHEDULED })
    }

    @Test
    fun `booking a lesson refreshes reminders once, not once per member`() = runTest {
        seed()

        bookSession()

        assertEquals(1, env.reminders.refreshCount)
    }

    @Test
    fun `an empty roster cannot be booked`() = runTest {
        seed()
        var threw = false
        try {
            repo.addGroupSession(groupId, algebra, start, 60, ClassMode.IN_PERSON, emptyMap())
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("a lesson with nobody in it bills no one", threw)
        assertTrue(env.classDao.items.value.isEmpty())
    }

    @Test
    fun `with sync on, each member's class gets its own calendar event`() = runTest {
        seed()
        env.settingsRepo.setCalendarSyncEnabled(true)

        val ids = bookSession()

        // One event per member: each student keeps their own invite and email reminder.
        assertEquals(2, env.calendar.upserts.size)
        assertTrue(ids.all { repo.getById(it)?.calendarEventId != null })
    }

    @Test
    fun `completing a group lesson bills every member at their own rate`() = runTest {
        seed()
        val ids = bookSession()

        val updated = repo.setStatusForSession(repo.getSessionFor(ids.first())!!, ClassStatus.COMPLETED)

        assertEquals(2, updated.size)
        val charged = repo.getSessionFor(ids.first())!!.classes.associate { it.studentId to it.charge }
        assertEquals(mapOf(ann to Money.ofMajor(30), bob to Money.ofMajor(25)), charged)
    }

    @Test
    fun `one absentee is cancelled on their own while the lesson stays completed`() = runTest {
        seed()
        val ids = bookSession()
        val session = repo.getSessionFor(ids.first())!!
        repo.setStatusForSession(session, ClassStatus.COMPLETED)

        // Cancel just Bob's class — the individual write path, on one member of a group lesson.
        val bobsClass = repo.getSessionFor(ids.first())!!.classes.single { it.studentId == bob }
        repo.setStatus(bobsClass.id, ClassStatus.CANCELLED)

        val after = repo.getSessionFor(ids.first())!!
        assertNull("a corrected lesson has no single status", after.commonStatus)
        assertEquals(Money.ZERO, after.classes.single { it.studentId == bob }.charge)
        assertEquals(Money.ofMajor(30), after.classes.single { it.studentId == ann }.charge)
    }

    @Test
    fun `completing a partly cancelled lesson leaves the correction standing`() = runTest {
        seed()
        val ids = bookSession()
        val bobsClass = repo.getSessionFor(ids.first())!!.classes.single { it.studentId == bob }
        repo.setStatus(bobsClass.id, ClassStatus.CANCELLED)

        repo.setStatusForSession(repo.getSessionFor(ids.first())!!, ClassStatus.COMPLETED)

        val after = repo.getSessionFor(ids.first())!!
        assertEquals(ClassStatus.COMPLETED, after.classes.single { it.studentId == ann }.status)
        assertEquals(ClassStatus.CANCELLED, after.classes.single { it.studentId == bob }.status)
    }

    @Test
    fun `moving a group lesson keeps its members in one session`() = runTest {
        seed()
        val ids = bookSession()
        val newStart = Instant.parse("2026-09-02T11:00:00Z")

        repo.moveSession(repo.getSessionFor(ids.first())!!, newStart, now = start)

        val session = repo.getSessionFor(ids.first())!!
        assertEquals(2, session.size)
        assertTrue(session.classes.all { it.start == newStart })
    }

    @Test
    fun `editing a group lesson re-bills every member from their own new rate`() = runTest {
        seed()
        val ids = bookSession()
        val session = repo.getSessionFor(ids.first())!!
        repo.setStatusForSession(session, ClassStatus.COMPLETED)

        // 90 minutes at new rates: Ann 40/h -> 60, Bob 20/h -> 30.
        repo.updateGroupSession(
            repo.getSessionFor(ids.first())!!,
            SessionEdit(
                subjectId = algebra,
                durationMinutes = 90,
                mode = ClassMode.REMOTE,
                pricesByStudent = mapOf(ann to Money.ofMajor(40), bob to Money.ofMajor(20)),
            ),
            newStart = start,
            now = Instant.parse("2026-09-05T00:00:00Z"),
        )

        val after = repo.getSessionFor(ids.first())!!
        assertEquals(2, after.size)
        assertTrue("the lesson stays completed when it stays in the past", after.classes.all { it.status == ClassStatus.COMPLETED })
        assertEquals(
            mapOf(ann to Money.ofMajor(60), bob to Money.ofMajor(30)),
            after.classes.associate { it.studentId to it.charge },
        )
        assertTrue(after.classes.all { it.durationMinutes == 90 && it.mode == ClassMode.REMOTE })
        // An edit updates rows in place — income can't be double-counted.
        assertEquals(2, env.classDao.items.value.size)
    }

    @Test
    fun `editing a completed group lesson into the future re-plans every member`() = runTest {
        seed()
        val ids = bookSession()
        repo.setStatusForSession(repo.getSessionFor(ids.first())!!, ClassStatus.COMPLETED)

        val now = Instant.parse("2026-09-01T12:00:00Z")
        repo.updateGroupSession(
            repo.getSessionFor(ids.first())!!,
            SessionEdit(algebra, 60, ClassMode.IN_PERSON, mapOf(ann to Money.ofMajor(30), bob to Money.ofMajor(25))),
            newStart = now.plusSeconds(86_400),
            now = now,
        )

        val after = repo.getSessionFor(ids.first())!!
        assertTrue(after.classes.all { it.status == ClassStatus.SCHEDULED })
        assertTrue(after.classes.all { it.charge == Money.ZERO })
    }

    @Test
    fun `deleting a group lesson removes every member's class and calendar event`() = runTest {
        seed()
        env.settingsRepo.setCalendarSyncEnabled(true)
        val ids = bookSession()
        val eventIds = ids.mapNotNull { repo.getById(it)?.calendarEventId }

        repo.deleteSession(repo.getSessionFor(ids.first())!!)

        assertTrue(env.classDao.items.value.isEmpty())
        assertEquals(eventIds.toSet(), env.calendar.deletes.toSet())
    }

    @Test
    fun `an individual class loads as a one-member session, so the same actions apply`() = runTest {
        seed()
        val id = env.classDao.upsert(
            ClassEntity(
                studentId = ann, subjectId = algebra, startMillis = start.toEpochMilli(),
                durationMinutes = 60, status = "SCHEDULED",
            ),
        )

        val session = repo.getSessionFor(id)!!

        assertTrue(!session.isGroup)
        assertEquals(1, session.size)
        assertEquals(id, session.first.id)
    }

    @Test
    fun `two lessons of the same group at different times stay separate sessions`() = runTest {
        seed()
        val first = bookSession()
        val later = bookSession(at = start.plusSeconds(7200))

        assertEquals(setOf(start), repo.getSessionFor(first.first())!!.classes.map { it.start }.toSet())
        assertEquals(2, repo.getSessionFor(later.first())!!.size)
        assertEquals(4, env.classDao.items.value.size)
    }
}
