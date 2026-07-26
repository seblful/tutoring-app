package com.seblful.klas.data.repository

import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.SessionEdit
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * A lesson's rows are written as **one** transaction, and the calendar is mirrored only after it
 * commits.
 *
 * A group lesson is *n* rows, so without a transaction a failure halfway through leaves a torn
 * lesson: some members dropped, others still at the old time, joiners missing — a shape the domain
 * has no name for. These are the tests that keep the phases in [ClassRepository] in that order; the
 * fake DAOs cannot roll back, so what is asserted is the *count* of transactions and the fact that
 * the calendar and the reminder scheduler are left untouched when a write fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClassRepositoryAtomicityTest {

    private val start = Instant.parse("2026-09-01T09:00:00Z")
    private val groupRate = Money.ofMajor(20)

    /** A three-member group, so a lesson is unambiguously several rows rather than one. */
    private class Fixture(val env: TestEnv = TestEnv()) {
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
            groupId = env.groupRepo.save(Group(name = "11 «А»", price = rate), setOf(ann, bob, cleo))
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
        )

    @Test
    fun `booking a group lesson writes every member's row in one transaction`() = runTest {
        val f = Fixture()
        f.seed(groupRate)
        val transactor = f.env.transactor
        // Seeding the group is a transaction of its own now (see `GroupRepositoryAtomicityTest`), so
        // this counts what the booking added rather than what the whole test did.
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
     * The observable half of atomicity that needs no real database: when the write does not land,
     * nothing downstream of it may run. A calendar event for a lesson that was never committed would
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

        val failure = runCatching { f.env.classRepo.setStatus(session, ClassStatus.CANCELLED) }

        assertTrue(failure.isFailure)
        // Cancelling mirrors as a *delete*, so neither kind of calendar write may have happened.
        assertEquals(emptyList<Any>(), f.env.calendar.calls)
        assertEquals(emptyList<String>(), f.env.calendar.deletes)
    }
}
