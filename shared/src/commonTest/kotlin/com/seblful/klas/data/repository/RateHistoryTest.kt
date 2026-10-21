package com.seblful.klas.data.repository

import com.seblful.klas.data.billing.PriceEntity
import com.seblful.klas.data.billing.RateChangeEntity
import com.seblful.klas.data.people.EnrollmentRepository
import com.seblful.klas.data.people.GroupEntity
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.RateOwner
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RateHistoryTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    val env = TestEnv()
    private val history = env.rateHistoryRepo

    private suspend fun student(name: String = "Аня"): Long = env.fixtures.student(name)

    private suspend fun subject(name: String = "Алгебра"): Long = env.fixtures.subject(name)

    private suspend fun unloggedGroup(): Long = env.groupDao.upsert(GroupEntity(name = "11 «А»"))

    private suspend fun changesFor(studentId: Long, subjectId: Long) =
        history.observeFor(RateOwner.StudentSubject(studentId, subjectId)).first()

    @Test
    fun `an unchanged rate is not logged`() = runTest {
        val owner = RateOwner.GroupRate(unloggedGroup())
        assertFalse(history.record(owner, from = Money.ofMajor(25), to = Money.ofMajor(25)))
        assertTrue(history.observeFor(owner).first().isEmpty())
    }

    @Test
    fun `a first-ever rate is logged as the start of the history - not as a change`() = runTest {
        val owner = RateOwner.GroupRate(unloggedGroup())
        assertTrue(history.record(owner, from = null, to = Money.ofMajor(25)))

        val change = history.observeFor(owner).first().single()
        assertTrue(change.isInitial, "no previous rate means the history starts here")
        assertNull(change.from)
        assertEquals(Money.ofMajor(25), change.to)
    }

    @Test
    fun `changes come back newest first`() = runTest {
        val owner = RateOwner.GroupRate(unloggedGroup())
        history.record(owner, null, Money.ofMajor(20), at = Instant.fromEpochMilliseconds(1_000))
        history.record(owner, Money.ofMajor(20), Money.ofMajor(25), at = Instant.fromEpochMilliseconds(2_000))
        history.record(owner, Money.ofMajor(25), Money.ofMajor(30), at = Instant.fromEpochMilliseconds(3_000))

        assertEquals(
            listOf(Money.ofMajor(30), Money.ofMajor(25), Money.ofMajor(20)),
            history.observeFor(owner).first().map { it.to },
        )
    }

    @Test
    fun `a rate that predates the log sorts below the dated changes`() = runTest {
        val owner = RateOwner.GroupRate(unloggedGroup())
        env.rateHistoryDao.insert(
            RateChangeEntity(groupId = owner.groupId, toMinor = 2000, atMillis = null),
        )
        history.record(owner, Money.ofMajor(20), Money.ofMajor(25), at = Instant.fromEpochMilliseconds(2_000))

        val changes = history.observeFor(owner).first()
        assertEquals(Money.ofMajor(25), changes.first().to)
        assertNull(changes.last().at, "the undated row is last")
    }

    @Test
    fun `a group's log and a student's log do not bleed into each other`() = runTest {
        val sid = student()
        val subj = subject()
        val group = RateOwner.GroupRate(unloggedGroup())
        history.record(RateOwner.StudentSubject(sid, subj), null, Money.ofMajor(40))
        history.record(group, null, Money.ofMajor(25))

        assertEquals(Money.ofMajor(40), changesFor(sid, subj).single().to)
        assertEquals(Money.ofMajor(25), history.observeFor(group).first().single().to)
    }

    @Test
    fun `enrolling logs the price it was enrolled at`() = runTest {
        val sid = student()
        val subj = subject()
        env.enrollmentRepo.enroll(sid, subj, Money.ofMajor(30))

        val change = changesFor(sid, subj).single()
        assertTrue(change.isInitial)
        assertEquals(Money.ofMajor(30), change.to)
    }

    @Test
    fun `repricing logs what the price was and what it became`() = runTest {
        val sid = student()
        val subj = subject()
        env.enrollmentRepo.enroll(sid, subj, Money.ofMajor(30))
        env.enrollmentRepo.reprice(sid, subj, Money.ofMajor(35))

        val changes = changesFor(sid, subj)
        assertEquals(2, changes.size)
        assertEquals(Money.ofMajor(30), changes.first().from)
        assertEquals(Money.ofMajor(35), changes.first().to)
        assertEquals(Money.ofMajor(35), env.enrollmentRepo.priceFor(sid, subj)?.amount, "the price really did move")
    }

    @Test
    fun `repricing to the same amount logs nothing`() = runTest {
        val sid = student()
        val subj = subject()
        env.enrollmentRepo.enroll(sid, subj, Money.ofMajor(30))
        env.enrollmentRepo.reprice(sid, subj, Money.ofMajor(30))

        assertEquals(1, changesFor(sid, subj).size)
    }

    @Test
    fun `repricing subjects logs only the prices that moved`() = runTest {
        val sid = student()
        val algebra = subject("Алгебра")
        val geometry = subject("Геометрия")
        env.enrollmentRepo.enroll(sid, algebra, Money.ofMajor(30))
        env.enrollmentRepo.enroll(sid, geometry, Money.ofMajor(25))
        env.enrollmentRepo.reprice(sid, algebra, Money.ofMajor(30))
        env.enrollmentRepo.reprice(sid, geometry, Money.ofMajor(28))

        assertEquals(1, changesFor(sid, algebra).size, "untouched")
        assertEquals(2, changesFor(sid, geometry).size, "raised once")
        assertEquals(Money.ofMajor(28), changesFor(sid, geometry).first().to)
    }

    @Test
    fun `un-enrolling a subject forgets its rate history`() = runTest {
        val sid = student()
        val subj = subject()
        env.enrollmentRepo.enroll(sid, subj, Money.ofMajor(30))
        env.enrollmentRepo.reprice(sid, subj, Money.ofMajor(35))

        assertEquals(EnrollmentRepository.Unenroll.Ok, env.enrollmentRepo.unenroll(sid, subj))
        assertTrue(changesFor(sid, subj).isEmpty())
    }

    @Test
    fun `unenrolling one subject keeps the other subject's rate history`() = runTest {
        val sid = student()
        val algebra = subject("Алгебра")
        val geometry = subject("Геометрия")
        env.enrollmentRepo.enroll(sid, algebra, Money.ofMajor(30))
        env.enrollmentRepo.enroll(sid, geometry, Money.ofMajor(25))
        assertEquals(EnrollmentRepository.Unenroll.Ok, env.enrollmentRepo.unenroll(sid, geometry))

        assertTrue(changesFor(sid, geometry).isEmpty())
        assertEquals(1, changesFor(sid, algebra).size)
    }

    @Test
    fun `a blocked un-enrollment keeps the rate history`() = runTest {
        val sid = student()
        val subj = subject()
        env.enrollmentRepo.enroll(sid, subj, Money.ofMajor(30))
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60,
            status = LessonStatus.COMPLETED, chargeMinor = Money.ofMajor(30).minor,
        )

        assertEquals(EnrollmentRepository.Unenroll.HasClasses, env.enrollmentRepo.unenroll(sid, subj))
        assertEquals(1, changesFor(sid, subj).size)
    }

    @Test
    fun `a price seeded straight into the table has no log - and the next change starts one`() = runTest {
        val sid = student()
        val subj = subject()
        env.priceDao.upsert(PriceEntity(sid, subj, Money.ofMajor(30).minor))
        assertTrue(changesFor(sid, subj).isEmpty())

        env.enrollmentRepo.reprice(sid, subj, Money.ofMajor(35))
        val change = changesFor(sid, subj).single()
        assertEquals(Money.ofMajor(30), change.from, "the previous amount is known from the price row")
        assertEquals(Money.ofMajor(35), change.to)
    }

    @Test
    fun `creating a group logs the rate it was created at`() = runTest {
        val sid = student()
        val id = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(25)), setOf(sid))

        val change = history.observeFor(RateOwner.GroupRate(id)).first().single()
        assertTrue(change.isInitial)
        assertEquals(Money.ofMajor(25), change.to)
    }

    @Test
    fun `re-pricing a group logs the change`() = runTest {
        val sid = student()
        val id = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(25)), setOf(sid))
        env.groupRepo.save(Group(id = id, name = "11 «А»", price = Money.ofMajor(30)), setOf(sid))

        val changes = history.observeFor(RateOwner.GroupRate(id)).first()
        assertEquals(2, changes.size)
        assertEquals(Money.ofMajor(25), changes.first().from)
        assertEquals(Money.ofMajor(30), changes.first().to)
    }

    @Test
    fun `renaming or re-staffing a group logs nothing`() = runTest {
        val anya = student("Аня")
        val boris = student("Борис")
        val id = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(25)), setOf(anya))

        env.groupRepo.save(Group(id = id, name = "11 «Б»", price = Money.ofMajor(25)), setOf(anya))
        env.groupRepo.save(Group(id = id, name = "11 «Б»", price = Money.ofMajor(25)), setOf(anya, boris))

        assertEquals(1, history.observeFor(RateOwner.GroupRate(id)).first().size)
    }

    @Test
    fun `archiving a group logs nothing`() = runTest {
        val sid = student()
        val id = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(25)), setOf(sid))
        env.groupRepo.setArchived(env.groupRepo.getById(id)!!, true)

        assertEquals(1, history.observeFor(RateOwner.GroupRate(id)).first().size)
    }

    @Test
    fun `currentRate reads a group's rate from the group row`() = runTest {
        val sid = student()
        val id = env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(25)), setOf(sid))

        assertEquals(Money.ofMajor(25), history.currentRate(RateOwner.GroupRate(id)))
    }

    @Test
    fun `currentRate reads a student's subject price from the price row`() = runTest {
        val sid = student()
        val subj = subject()
        env.enrollmentRepo.enroll(sid, subj, Money.ofMajor(40))

        assertEquals(Money.ofMajor(40), history.currentRate(RateOwner.StudentSubject(sid, subj)))
    }

    @Test
    fun `currentRate is null when there is no rate yet - which is what starts a history`() = runTest {
        assertNull(history.currentRate(RateOwner.GroupRate(999)))
        assertNull(history.currentRate(RateOwner.StudentSubject(999, 999)))
    }
}
