package com.seblful.repetit.data.repository

import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.SubjectEntity
import com.seblful.repetit.domain.ClassSession
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
        // The group's own rate — 20, below either student's individual rate — is what its lessons bill.
        groupId = env.groupRepo.save(Group(name = "11 «А»", price = groupRate), setOf(ann, bob))
    }

    private val groupRate = Money.ofMajor(20)

    private suspend fun bookSession(
        at: Instant = start,
        price: Money = groupRate,
        members: Collection<Long> = listOf(ann, bob),
    ): List<Long> = repo.book(
        groupId = groupId,
        subjectId = algebra,
        start = at,
        durationMinutes = 60,
        mode = ClassMode.IN_PERSON,
        studentIds = members,
        price = price,
    )

    @Test
    fun `booking a group lesson writes one class per member, all at the group's rate`() = runTest {
        seed()

        val ids = bookSession()

        assertEquals(2, ids.size)
        val session = repo.getSessionFor(ids.first())!!
        assertTrue(session.isGroup)
        assertEquals(groupId, session.groupId)
        assertEquals(2, session.size)
        // One rate for the whole lesson — not each member's individual price.
        assertEquals(
            mapOf(ann to groupRate, bob to groupRate),
            session.classes.associate { it.studentId to it.price },
        )
        assertEquals(groupRate, session.sharedPrice)
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
            repo.book(
                subjectId = algebra,
                start = start,
                durationMinutes = 60,
                mode = ClassMode.IN_PERSON,
                studentIds = emptyList(),
                price = groupRate,
                groupId = groupId,
            )
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("a lesson with nobody in it bills no one", threw)
        assertTrue(env.classDao.items.value.isEmpty())
    }

    @Test
    fun `with sync on, each member's class gets its own calendar event`() = runTest {
        seed()
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }

        val ids = bookSession()

        // One event per member: each student keeps their own invite and email reminder.
        assertEquals(2, env.calendar.upserts.size)
        assertTrue(ids.all { repo.getById(it)?.calendarEventId != null })
    }

    @Test
    fun `completing a group lesson bills every member the group's rate`() = runTest {
        seed()
        val ids = bookSession()

        val updated = repo.setStatus(repo.getSessionFor(ids.first())!!, ClassStatus.COMPLETED)

        assertEquals(2, updated.size)
        // One rate, but still a charge each: the group says how much, the students still owe it.
        val charged = repo.getSessionFor(ids.first())!!.classes.associate { it.studentId to it.charge }
        assertEquals(mapOf(ann to groupRate, bob to groupRate), charged)
    }

    @Test
    fun `one absentee is cancelled on their own while the lesson stays completed`() = runTest {
        seed()
        val ids = bookSession()
        val session = repo.getSessionFor(ids.first())!!
        repo.setStatus(session, ClassStatus.COMPLETED)

        // Cancel just Bob's class — one member of a group lesson, as a session of one.
        val bobsClass = repo.getSessionFor(ids.first())!!.classes.single { it.studentId == bob }
        repo.setStatus(ClassSession.of(bobsClass), ClassStatus.CANCELLED)

        val after = repo.getSessionFor(ids.first())!!
        assertNull("a corrected lesson has no single status", after.commonStatus)
        assertEquals(Money.ZERO, after.classes.single { it.studentId == bob }.charge)
        assertEquals(groupRate, after.classes.single { it.studentId == ann }.charge)
    }

    @Test
    fun `completing a partly cancelled lesson leaves the correction standing`() = runTest {
        seed()
        val ids = bookSession()
        val bobsClass = repo.getSessionFor(ids.first())!!.classes.single { it.studentId == bob }
        repo.setStatus(ClassSession.of(bobsClass), ClassStatus.CANCELLED)

        repo.setStatus(repo.getSessionFor(ids.first())!!, ClassStatus.COMPLETED)

        val after = repo.getSessionFor(ids.first())!!
        assertEquals(ClassStatus.COMPLETED, after.classes.single { it.studentId == ann }.status)
        assertEquals(ClassStatus.CANCELLED, after.classes.single { it.studentId == bob }.status)
    }

    @Test
    fun `moving a group lesson keeps its members in one session`() = runTest {
        seed()
        val ids = bookSession()
        val newStart = Instant.parse("2026-09-02T11:00:00Z")

        repo.move(repo.getSessionFor(ids.first())!!, newStart, now = start)

        val session = repo.getSessionFor(ids.first())!!
        assertEquals(2, session.size)
        assertTrue(session.classes.all { it.start == newStart })
    }

    @Test
    fun `editing a group lesson re-bills every member from the lesson's new rate`() = runTest {
        seed()
        val ids = bookSession()
        val session = repo.getSessionFor(ids.first())!!
        repo.setStatus(session, ClassStatus.COMPLETED)

        // 90 minutes at a new rate of 40/h -> 60 each.
        repo.apply(
            repo.getSessionFor(ids.first())!!,
            SessionEdit(
                subjectId = algebra,
                durationMinutes = 90,
                mode = ClassMode.REMOTE,
                price = Money.ofMajor(40),
            ),
            newStart = start,
            now = Instant.parse("2026-09-05T00:00:00Z"),
        )

        val after = repo.getSessionFor(ids.first())!!
        assertEquals(2, after.size)
        assertTrue("the lesson stays completed when it stays in the past", after.classes.all { it.status == ClassStatus.COMPLETED })
        assertEquals(
            mapOf(ann to Money.ofMajor(60), bob to Money.ofMajor(60)),
            after.classes.associate { it.studentId to it.charge },
        )
        assertTrue(after.classes.all { it.durationMinutes == 90 && it.mode == ClassMode.REMOTE })
        // An edit updates rows in place — income can't be double-counted.
        assertEquals(2, env.classDao.items.value.size)
    }

    @Test
    fun `re-pricing a legacy lesson brings its members onto one rate`() = runTest {
        seed()
        // A lesson from before groups had a rate: each member billed their own.
        val ids = repo.book(
            groupId = groupId,
            subjectId = algebra,
            start = start,
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann),
            price = Money.ofMajor(30),
        ) + repo.book(
            groupId = groupId,
            subjectId = algebra,
            start = start,
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            studentIds = listOf(bob),
            price = Money.ofMajor(25),
        )
        assertNull("members on differing rates have no single lesson rate", repo.getSessionFor(ids.first())!!.sharedPrice)

        repo.apply(
            repo.getSessionFor(ids.first())!!,
            SessionEdit(algebra, 60, ClassMode.IN_PERSON, price = groupRate),
            newStart = start,
            now = start,
        )

        assertEquals(groupRate, repo.getSessionFor(ids.first())!!.sharedPrice)
    }

    @Test
    fun `editing a completed group lesson into the future re-plans every member`() = runTest {
        seed()
        val ids = bookSession()
        repo.setStatus(repo.getSessionFor(ids.first())!!, ClassStatus.COMPLETED)

        val now = Instant.parse("2026-09-01T12:00:00Z")
        repo.apply(
            repo.getSessionFor(ids.first())!!,
            SessionEdit(algebra, 60, ClassMode.IN_PERSON, groupRate),
            newStart = now.plusSeconds(86_400),
            now = now,
        )

        val after = repo.getSessionFor(ids.first())!!
        assertTrue(after.classes.all { it.status == ClassStatus.SCHEDULED })
        assertTrue(after.classes.all { it.charge == Money.ZERO })
    }

    // --- correcting who was in a lesson (roster on apply) ---

    @Test
    fun `dropping a member from a booked lesson deletes their class and its event`() = runTest {
        seed()
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val ids = bookSession()
        val bobsEvent = repo.getSessionFor(ids.first())!!
            .classes.single { it.studentId == bob }.calendarEventId

        repo.apply(
            repo.getSessionFor(ids.first())!!,
            SessionEdit(algebra, 60, ClassMode.IN_PERSON, groupRate),
            newStart = start,
            now = start,
            roster = setOf(ann),
        )

        val after = repo.getSessionFor(ids.first())!!
        assertEquals("only Ann is left in the lesson", listOf(ann), after.classes.map { it.studentId })
        // The row is gone, not cancelled: they were never in the lesson, rather than having missed it.
        assertEquals(1, env.classDao.items.value.size)
        assertTrue(env.calendar.deletes.contains(bobsEvent))
    }

    @Test
    fun `dropping a completed member takes their charge out of the lesson`() = runTest {
        seed()
        val ids = bookSession()
        repo.setStatus(repo.getSessionFor(ids.first())!!, ClassStatus.COMPLETED)

        repo.apply(
            repo.getSessionFor(ids.first())!!,
            SessionEdit(algebra, 60, ClassMode.IN_PERSON, groupRate),
            newStart = start,
            now = start,
            roster = setOf(ann),
        )

        val after = repo.getSessionFor(ids.first())!!
        assertEquals(listOf(ann), after.classes.map { it.studentId })
        // Ann's own charge is untouched — the correction was about who was there, not about the rate.
        assertEquals(groupRate, after.classes.single().charge)
        assertTrue("Bob has no class left to bill", env.classDao.items.value.none { it.studentId == bob })
    }

    @Test
    fun `adding a member to a scheduled lesson books them at the lesson's terms`() = runTest {
        seed()
        val ids = bookSession(members = listOf(ann))
        val cate = env.studentDao.upsert(StudentEntity(name = "Вера"))

        repo.apply(
            repo.getSessionFor(ids.first())!!,
            SessionEdit(algebra, 90, ClassMode.REMOTE, Money.ofMajor(40)),
            newStart = start,
            now = start,
            roster = setOf(ann, cate),
        )

        val after = repo.getSessionFor(ids.first())!!
        assertEquals(2, after.size)
        val joined = after.classes.single { it.studentId == cate }
        assertEquals("joins the lesson, not a lesson of their own", groupId, joined.groupId)
        assertEquals(start, joined.start)
        assertEquals(90, joined.durationMinutes)
        assertEquals(Money.ofMajor(40), joined.price)
        assertEquals(ClassMode.REMOTE, joined.mode)
        assertEquals(ClassStatus.SCHEDULED, joined.status)
        assertEquals("nothing owed on a lesson that hasn't happened", Money.ZERO, joined.charge)
    }

    /** The reason attendance is editable after the fact: somebody who came but was never ticked. */
    @Test
    fun `adding a member to a completed lesson bills them like everyone else`() = runTest {
        seed()
        val ids = bookSession(members = listOf(ann))
        repo.setStatus(repo.getSessionFor(ids.first())!!, ClassStatus.COMPLETED)

        repo.apply(
            repo.getSessionFor(ids.first())!!,
            SessionEdit(algebra, 60, ClassMode.IN_PERSON, groupRate),
            newStart = start,
            now = Instant.parse("2026-09-05T00:00:00Z"),
            roster = setOf(ann, bob),
        )

        val after = repo.getSessionFor(ids.first())!!
        assertEquals(ClassStatus.COMPLETED, after.commonStatus)
        assertEquals(
            "the joiner owes what being in the lesson owed",
            mapOf(ann to groupRate, bob to groupRate),
            after.classes.associate { it.studentId to it.charge },
        )
    }

    /**
     * The whole roster replaced at once — the case that would break a session rebuilt from the class
     * that was tapped, since that very class is the one leaving.
     */
    @Test
    fun `swapping one member for another is a single write, with one reminder refresh`() = runTest {
        seed()
        val ids = bookSession(members = listOf(ann))
        // The booking itself refreshed once; the swap below must add exactly one more.
        assertEquals(1, env.reminders.refreshCount)

        val written = repo.apply(
            repo.getSessionFor(ids.first())!!,
            SessionEdit(algebra, 60, ClassMode.IN_PERSON, groupRate),
            newStart = start,
            now = start,
            roster = setOf(bob),
        )

        assertEquals(listOf(bob), written.map { it.studentId })
        assertEquals("Ann's class went, Bob's arrived", 1, env.classDao.items.value.size)
        // Still one lesson of the group's, reachable from the class that now stands for it.
        val after = repo.getSessionFor(written.single().id)!!
        assertEquals(groupId, after.groupId)
        assertEquals(listOf(bob), after.classes.map { it.studentId })
        assertEquals("one lesson, one reminder refresh", 2, env.reminders.refreshCount)
    }

    @Test
    fun `a null roster leaves the lesson's members exactly as they were`() = runTest {
        seed()
        val ids = bookSession()

        repo.apply(
            repo.getSessionFor(ids.first())!!,
            SessionEdit(algebra, 90, ClassMode.IN_PERSON, groupRate),
            newStart = start,
            now = start,
        )

        val after = repo.getSessionFor(ids.first())!!
        assertEquals(setOf(ann, bob), after.classes.map { it.studentId }.toSet())
        assertEquals(2, env.classDao.items.value.size)
    }

    @Test
    fun `deleting a group lesson removes every member's class and calendar event`() = runTest {
        seed()
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val ids = bookSession()
        val eventIds = ids.mapNotNull { repo.getById(it)?.calendarEventId }

        repo.delete(repo.getSessionFor(ids.first())!!)

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
