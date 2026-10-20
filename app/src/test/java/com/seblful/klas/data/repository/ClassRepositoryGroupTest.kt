package com.seblful.klas.data.repository

import com.seblful.klas.data.local.AttendanceEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.lesson.SessionEdit
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ClassRepositoryGroupTest {

    @get:Rule
    val env = TestEnv()
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
        groupId = env.groupRepo.save(Group(name = "11 «А»", price = groupRate), setOf(ann, bob)).id
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
    ).written()

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
        assertTrue(session.classes.all { it.start == start && it.subjectId == algebra })
        assertTrue(session.classes.all { it.status == LessonStatus.SCHEDULED })
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

        val failure = runCatching {
            repo.book(
                subjectId = algebra,
                start = start,
                durationMinutes = 60,
                mode = ClassMode.IN_PERSON,
                studentIds = emptyList(),
                price = groupRate,
                groupId = groupId,
            )
        }.exceptionOrNull()

        assertTrue(
            "a lesson with nobody in it bills no one, got $failure",
            failure is IllegalArgumentException,
        )
        assertTrue(env.classes().isEmpty())
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

        val updated = repo.setStatus(repo.getSessionFor(ids.first())!!, LessonStatus.COMPLETED).written()

        assertEquals(2, updated.size)
        val charged = repo.getSessionFor(ids.first())!!.classes.associate { it.studentId to it.charge }
        assertEquals(mapOf(ann to groupRate, bob to groupRate), charged)
    }

    @Test
    fun `one absentee is marked absent while the lesson stays taught`() = runTest {
        seed()
        val ids = bookSession()
        repo.setStatus(repo.getSessionFor(ids.first())!!, LessonStatus.COMPLETED)

        val bobsClass = repo.getSessionFor(ids.first())!!.classes.single { it.studentId == bob }
        repo.setAttendance(bobsClass, Attended.ABSENT)

        val after = repo.getSessionFor(ids.first())!!
        assertEquals("the lesson was still taught", LessonStatus.COMPLETED, after.status)
        assertEquals("and taught to one of the two", 1, after.present)
        assertEquals(Money.ZERO, after.classes.single { it.studentId == bob }.charge)
        assertEquals(groupRate, after.classes.single { it.studentId == ann }.charge)
    }

    @Test
    fun `an absentee can be charged anyway`() = runTest {
        seed()
        val ids = bookSession()
        repo.setStatus(repo.getSessionFor(ids.first())!!, LessonStatus.COMPLETED)

        val bobsClass = repo.getSessionFor(ids.first())!!.classes.single { it.studentId == bob }
        repo.setAttendance(bobsClass, Attended.ABSENT_BILLED)

        val after = repo.getSessionFor(ids.first())!!
        assertEquals("he was not there", 1, after.present)
        assertEquals("but still owes for it", groupRate, after.classes.single { it.studentId == bob }.charge)
    }

    @Test
    fun `completing a lesson with an absentee leaves the absence standing`() = runTest {
        seed()
        val ids = bookSession()
        val bobsClass = repo.getSessionFor(ids.first())!!.classes.single { it.studentId == bob }
        repo.setAttendance(bobsClass, Attended.ABSENT)

        repo.setStatus(repo.getSessionFor(ids.first())!!, LessonStatus.COMPLETED)

        val after = repo.getSessionFor(ids.first())!!
        assertEquals(LessonStatus.COMPLETED, after.status)
        assertEquals(Attended.PRESENT, after.classes.single { it.studentId == ann }.attended)
        assertEquals(Attended.ABSENT, after.classes.single { it.studentId == bob }.attended)
        assertEquals("and bills only the one who came", groupRate, after.classes.single { it.studentId == ann }.charge)
        assertEquals(Money.ZERO, after.classes.single { it.studentId == bob }.charge)
    }

    @Test
    fun `archiving a member withdraws their event from a lesson that still goes ahead`() = runTest {
        seed()
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val ids = bookSession()
        val session = repo.getSessionFor(ids.first())!!
        val bobEvent = session.classes.single { it.studentId == bob }.calendarEventId
        val annEvent = session.classes.single { it.studentId == ann }.calendarEventId
        assertNotNull(bobEvent)

        env.studentRepo.setArchived(env.studentRepo.getById(bob)!!, archived = true)

        val after = repo.getSessionFor(ids.first())!!
        val away = after.classes.single { it.studentId == bob }
        val coming = after.classes.single { it.studentId == ann }
        assertEquals("the lesson was not called off", LessonStatus.SCHEDULED, after.status)
        assertEquals("they are simply not coming", Attended.ABSENT, away.attended)
        assertEquals("their event is gone from the calendar", listOf(bobEvent), env.calendar.deletes)
        assertNull("and the id is forgotten with it", repo.getById(away.id)!!.calendarEventId)
        assertEquals("while the member who is coming keeps theirs", annEvent, repo.getById(coming.id)!!.calendarEventId)
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
        repo.setStatus(session, LessonStatus.COMPLETED)

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
        assertTrue(
            "the lesson stays completed when it stays in the past",
            after.classes.all {
                it.status ==
                    LessonStatus.COMPLETED
            },
        )
        assertEquals(
            mapOf(ann to Money.ofMajor(60), bob to Money.ofMajor(60)),
            after.classes.associate { it.studentId to it.charge },
        )
        assertTrue(after.classes.all { it.durationMinutes == 90 && it.mode == ClassMode.REMOTE })
        // An edit updates rows in place — income can't be double-counted.
        assertEquals(2, env.classes().size)
    }

    @Test
    fun `re-pricing a legacy lesson brings its members onto one rate`() = runTest {
        seed()
        val annIds = repo.book(
            groupId = groupId,
            subjectId = algebra,
            start = start,
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            studentIds = listOf(ann),
            price = Money.ofMajor(30),
        ).written()
        val annRow = env.classDao.getById(annIds.single())!!
        val ids = annIds + env.attendanceDao.upsert(
            AttendanceEntity(
                lessonId = annRow.lessonId,
                studentId = bob,
                state = annRow.state,
                priceMinor = Money.ofMajor(25).minor,
            ),
        )
        assertNull(
            "members on differing rates have no single lesson rate",
            repo.getSessionFor(ids.first())!!.sharedPrice,
        )

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
        repo.setStatus(repo.getSessionFor(ids.first())!!, LessonStatus.COMPLETED)

        val now = Instant.parse("2026-09-01T12:00:00Z")
        repo.apply(
            repo.getSessionFor(ids.first())!!,
            SessionEdit(algebra, 60, ClassMode.IN_PERSON, groupRate),
            newStart = now.plus((86_400).seconds),
            now = now,
        )

        val after = repo.getSessionFor(ids.first())!!
        assertTrue(after.classes.all { it.status == LessonStatus.SCHEDULED })
        assertTrue(after.classes.all { it.charge == Money.ZERO })
    }

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
        assertEquals(1, env.classes().size)
        assertTrue(env.calendar.deletes.contains(bobsEvent))
    }

    @Test
    fun `dropping a completed member takes their charge out of the lesson`() = runTest {
        seed()
        val ids = bookSession()
        repo.setStatus(repo.getSessionFor(ids.first())!!, LessonStatus.COMPLETED)

        repo.apply(
            repo.getSessionFor(ids.first())!!,
            SessionEdit(algebra, 60, ClassMode.IN_PERSON, groupRate),
            newStart = start,
            now = start,
            roster = setOf(ann),
        )

        val after = repo.getSessionFor(ids.first())!!
        assertEquals(listOf(ann), after.classes.map { it.studentId })
        assertEquals(groupRate, after.classes.single().charge)
        assertTrue("Bob has no class left to bill", env.classes().none { it.studentId == bob })
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
        assertEquals(LessonStatus.SCHEDULED, joined.status)
        assertEquals("nothing owed on a lesson that hasn't happened", Money.ZERO, joined.charge)
    }

    /** The reason attendance is editable after the fact: somebody who came but was never ticked. */
    @Test
    fun `adding a member to a completed lesson bills them like everyone else`() = runTest {
        seed()
        val ids = bookSession(members = listOf(ann))
        repo.setStatus(repo.getSessionFor(ids.first())!!, LessonStatus.COMPLETED)

        repo.apply(
            repo.getSessionFor(ids.first())!!,
            SessionEdit(algebra, 60, ClassMode.IN_PERSON, groupRate),
            newStart = start,
            now = Instant.parse("2026-09-05T00:00:00Z"),
            roster = setOf(ann, bob),
        )

        val after = repo.getSessionFor(ids.first())!!
        assertEquals(LessonStatus.COMPLETED, after.status)
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
        ).written()

        assertEquals(listOf(bob), written.map { it.studentId })
        assertEquals("Ann's class went, Bob's arrived", 1, env.classes().size)
        // Still one lesson of the group's, reachable from the class that now stands for it.
        val after = repo.getSessionFor(written.single().id)!!
        assertEquals(groupId, after.groupId)
        assertEquals(listOf(bob), after.classes.map { it.studentId })
        assertEquals("one lesson, one reminder refresh", 2, env.reminders.refreshCount)
    }

    /**
     * The whole roster replaced **and** the lesson moved, in one save.
     *
     * The regression the sibling test above could not see, because it passed the lesson's current start
     * and duration back unchanged. The lesson's own row used to be written from the classes that were
     * *staying*, and a total swap leaves none — so the roster changed and the hour did not, while
     * `holdingHour` had already reserved the new one and the calendar had already mirrored it there. The
     * joiner was then re-billed from the new length against the stored old one, which is how a day's
     * takings and the same lesson in a report came to disagree.
     */
    @Test
    fun `swapping the whole roster still moves the lesson and re-times it`() = runTest {
        seed()
        val ids = bookSession(members = listOf(ann))
        // Taught, so the charge below is a real one: this is the case where a wrong stored duration
        // becomes wrong income rather than a zero either way.
        repo.setStatus(repo.getSessionFor(ids.first())!!, LessonStatus.COMPLETED).written()
        val moved = start.plus(3600.seconds)

        val written = repo.apply(
            repo.getSessionFor(ids.first())!!,
            SessionEdit(algebra, 30, ClassMode.REMOTE, groupRate),
            newStart = moved,
            // After the new start, so the correction keeps the lesson taught instead of re-planning it.
            now = moved.plus(3600.seconds),
            roster = setOf(bob),
        ).written()

        // Read back through the session, so this is the lesson row as stored rather than as returned.
        val after = repo.getSessionFor(written.single().id)!!
        assertEquals("the lesson moved", moved, after.start)
        assertEquals("and took its new length", 30, after.durationMinutes)
        assertEquals(ClassMode.REMOTE, after.first.mode)
        assertEquals(LessonStatus.COMPLETED, after.status)
        assertEquals(listOf(bob), after.classes.map { it.studentId })
        // 20/h for half an hour. This is only 10 if the duration the lesson *stored* is 30.
        assertEquals(
            "the joiner is billed from the length the lesson actually has",
            Money.ofMajor(10),
            after.classes.single().charge,
        )
        // The hour it left is free again, and the hour it took is not.
        assertTrue(repo.conflictsWith(Scheduling.Slot(start, 60)).isEmpty())
        assertEquals(1, repo.conflictsWith(Scheduling.Slot(moved, 30)).size)
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
        assertEquals(2, env.classes().size)
    }

    @Test
    fun `deleting a group lesson removes every member's class and calendar event`() = runTest {
        seed()
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val ids = bookSession()
        val eventIds = ids.mapNotNull { repo.getById(it)?.calendarEventId }

        repo.delete(repo.getSessionFor(ids.first())!!)

        assertTrue(env.classes().isEmpty())
        assertEquals(eventIds.toSet(), env.calendar.deletes.toSet())
    }

    @Test
    fun `an individual class loads as a one-member session, so the same actions apply`() = runTest {
        seed()
        val id = env.fixtures.storedClass(
            studentId = ann, subjectId = algebra, startMillis = start.toEpochMilliseconds(),
            durationMinutes = 60, status = LessonStatus.SCHEDULED,
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
        val later = bookSession(at = start.plus(7200.seconds))

        assertEquals(setOf(start), repo.getSessionFor(first.first())!!.classes.map { it.start }.toSet())
        assertEquals(2, repo.getSessionFor(later.first())!!.size)
        assertEquals(4, env.classes().size)
    }
}
