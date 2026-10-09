package com.seblful.klas.data.repository

import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.calendar.Guest
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.lesson.SessionEdit
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * A lesson's rows are written as **one** transaction, and the calendar is mirrored only after it
 * commits.
 *
 * A group lesson is *n* rows, so without a transaction a failure halfway through leaves a torn
 * lesson: some members dropped, others still at the old time, joiners missing — a shape the domain
 * has no name for. These are the tests that keep the phases in [ClassRepository] in that order: the
 * *count* of transactions, that a write which fails to commit leaves every row as it was, and that the
 * calendar and the reminder scheduler are left untouched when it does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ClassRepositoryAtomicityTest {

    @get:Rule
    val env = TestEnv()

    private val start = Instant.parse("2026-09-01T09:00:00Z")
    private val groupRate = Money.ofMajor(20)

    /** A three-member group, so a lesson is unambiguously several rows rather than one. */
    private inner class Fixture {
        val env: TestEnv get() = this@ClassRepositoryAtomicityTest.env

        var ann = 0L
        var bob = 0L
        var cleo = 0L
        var algebra = 0L
        var groupId = 0L

        suspend fun seed(rate: Money) {
            ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
            bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
            cleo = env.studentDao.upsert(StudentEntity(name = "Вера"))
            algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
            groupId = env.groupRepo.save(Group(name = "11 «А»", price = rate), setOf(ann, bob, cleo)).id
        }
    }

    private suspend fun Fixture.book(members: Collection<Long>, at: Instant = start): List<Long> =
        env.classRepo.book(
            groupId = groupId,
            subjectId = algebra,
            start = at,
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            studentIds = members,
            price = groupRate,
        ).written()

    @Test
    fun `booking a group lesson writes every member's row in one transaction`() = runTest {
        val f = Fixture()
        f.seed(groupRate)
        val transactor = f.env.transactor
        // Seeding the group is a transaction of its own now (see `EnrollmentAtomicityTest`, which
        // covers the group and enrollment writes), so this counts what the booking added rather than
        // what the whole test did.
        val before = transactor.count

        f.book(listOf(f.ann, f.bob, f.cleo))

        assertEquals("three rows, one transaction", 1, transactor.count - before)
        assertEquals("no nesting", 1, transactor.maxDepth)
        assertEquals("nothing left open", 0, transactor.depth)
    }

    @Test
    fun `reconciling a roster and editing the lesson is a single transaction`() = runTest {
        val f = Fixture()
        f.seed(groupRate)
        // Booked for two; saved for two others — one leaves, one joins, one stays and is edited. All
        // three kinds of row change in one save, which is the case that used to be three writes.
        val ids = f.book(listOf(f.ann, f.bob))
        val session = f.env.classRepo.getSessionFor(ids.first())!!
        val transactor = f.env.transactor
        val before = transactor.count

        f.env.classRepo.apply(
            session = session,
            changes = SessionEdit(
                subjectId = f.algebra,
                durationMinutes = 90,
                mode = ClassMode.REMOTE,
                price = groupRate,
            ),
            newStart = start,
            now = start,
            roster = setOf(f.bob, f.cleo),
        )

        assertEquals("a drop, an update and an insert in one transaction", 1, transactor.count - before)
        assertEquals("no nesting", 1, transactor.maxDepth)
    }

    @Test
    fun `deleting a lesson removes every row in one transaction`() = runTest {
        val f = Fixture()
        f.seed(groupRate)
        val ids = f.book(listOf(f.ann, f.bob, f.cleo))
        val session = f.env.classRepo.getSessionFor(ids.first())!!
        val before = f.env.transactor.count

        f.env.classRepo.delete(session)

        assertEquals(1, f.env.transactor.count - before)
        assertEquals(emptyList<Long>(), f.env.classDao.getAll().map { it.id })
    }

    @Test
    fun `auto-completing a batch of due classes is one transaction`() = runTest {
        val f = Fixture()
        f.seed(groupRate)
        f.book(listOf(f.ann, f.bob, f.cleo))
        val before = f.env.transactor.count

        val completed = f.env.classRepo.autoCompleteDue(start.plusSeconds(7200))

        assertEquals(3, completed.size)
        assertEquals("one pass, one transaction", 1, f.env.transactor.count - before)
    }

    // --- What a failed write must not do ------------------------------------------------------

    /**
     * Both halves of atomicity: when the write does not land, none of its rows do, and nothing
     * downstream of it may run. A calendar event for a lesson that was never committed would
     * be an event with no lesson behind it, and a reminder plan built from rows that do not exist.
     */
    @Test
    fun `a booking that fails to commit mirrors nothing and refreshes no reminders`() = runTest {
        val f = Fixture()
        f.seed(groupRate)
        f.env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val transactor = f.env.transactor
        val remindersBefore = f.env.reminders.refreshCount
        // Arm the *next* transaction to fail, now that seeding is done.
        transactor.failAt = transactor.count + 1

        val failure = runCatching { f.book(listOf(f.ann, f.bob, f.cleo)) }

        assertTrue("the failure is reported, not swallowed", failure.isFailure)
        assertEquals("not one member's row was kept", emptyList<Any>(), f.env.classes())
        assertEquals("no event for an uncommitted lesson", emptyList<Any>(), f.env.calendar.calls)
        assertEquals("no reminder refresh either", remindersBefore, f.env.reminders.refreshCount)
    }

    @Test
    fun `a status change that fails to commit leaves the calendar alone`() = runTest {
        val f = Fixture()
        f.seed(groupRate)
        f.env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val ids = f.book(listOf(f.ann, f.bob))
        val session = f.env.classRepo.getSessionFor(ids.first())!!
        val transactor = f.env.transactor
        f.env.calendar.calls.clear()
        f.env.calendar.deletes.clear()
        transactor.failAt = transactor.count + 1

        val failure = runCatching { f.env.classRepo.setStatus(session, LessonStatus.CANCELLED) }

        assertTrue(failure.isFailure)
        assertEquals(
            "both members' rows rolled back together",
            listOf(LessonStatus.SCHEDULED, LessonStatus.SCHEDULED),
            f.env.classes().map { it.status },
        )
        // Cancelling mirrors as a *delete*, so neither kind of calendar write may have happened.
        assertEquals(emptyList<Any>(), f.env.calendar.calls)
        assertEquals(emptyList<String>(), f.env.calendar.deletes)
    }

    /**
     * The order itself, not just its consequences.
     *
     * The other tests here assert that a failed commit leaves the calendar alone, which catches phase 3
     * running *before* phase 2. This catches it running *inside* — an IPC call to a `ContentProvider`
     * while SQLite's write lock is held, which is an ANR rather than a wrong answer, so no assertion
     * about the resulting data would ever notice. Every lesson write goes through
     * `ClassRepository.write`, so pinning it once pins it for all five.
     */
    @Test
    fun `the calendar is never written while a transaction is open`() = runTest {
        val depths = mutableListOf<Int>()
        val spying = object : CalendarSync {
            override suspend fun upsert(
                klass: TutoringClass,
                title: String,
                guest: Guest?,
                reminderLeadMinutes: Int,
            ): String? {
                depths += env.transactor.depth
                return "event-${depths.size}"
            }

            override suspend fun refresh(klass: TutoringClass, title: String) {
                depths += env.transactor.depth
            }

            override suspend fun delete(eventId: String) {
                depths += env.transactor.depth
            }
        }
        val mirror = CalendarMirror(
            env.classDao, env.attendanceDao, env.studentDao, env.subjectDao, env.groupDao, spying,
            env.settingsRepo,
        )
        val repo = ClassRepository(
            env.classDao, env.lessonDao, env.attendanceDao, mirror, env.reminders, env.transactor,
        )
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        env.studentDao.upsert(StudentEntity(id = 1, name = "Аня"))
        env.subjectDao.upsert(SubjectEntity(id = 1, name = "Алгебра"))

        // One of each shape of write: an insert, an in-place update, and a delete.
        val id = repo.book(
            subjectId = 1,
            start = start,
            durationMinutes = 60,
            mode = ClassMode.IN_PERSON,
            studentIds = listOf(1L),
            price = Money.ofMajor(30),
        ).written().single()
        val session = repo.getSessionFor(id)!!
        repo.setStatus(session, LessonStatus.CANCELLED)
        repo.delete(repo.getSessionFor(id) ?: session)

        assertTrue("no calendar write was observed at all", depths.isNotEmpty())
        assertEquals("every calendar write must happen after the commit", listOf(0), depths.distinct())
    }
}
