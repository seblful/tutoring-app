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
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private suspend fun priceGroup(id: Long, to: Money) =
        env.groupRepo.save(env.groupRepo.getById(id)!!.copy(price = to), memberIds = emptySet())

    private suspend fun newPricedGroup(rate: Money): Long = env.fixtures.group(rate = rate)

    private suspend fun groupChanges(id: Long) = history.observeFor(RateOwner.GroupRate(id)).first()

    @Test
    fun `an unchanged rate is not logged`() = runTest {
        val id = newPricedGroup(Money.ofMajor(25))

        priceGroup(id, Money.ofMajor(25))

        assertEquals(1, groupChanges(id).size, "only the first rate")
    }

    @Test
    fun `a first-ever rate is logged as the start of the history - not as a change`() = runTest {
        val id = newPricedGroup(Money.ofMajor(25))

        val change = groupChanges(id).single()
        assertNull(change.from, "no previous rate means the history starts here")
        assertEquals(Money.ofMajor(25), change.to)
    }

    @Test
    fun `a change is measured against the stored rate as read before the write`() = runTest {
        val id = newPricedGroup(Money.ofMajor(20))

        priceGroup(id, Money.ofMajor(25))

        assertEquals(Money.ofMajor(20), groupChanges(id).first().from)
    }

    @Test
    fun `changes come back newest first`() = runTest {
        val id = newPricedGroup(Money.ofMajor(20))
        env.clock.advanceBy(1_000)
        priceGroup(id, Money.ofMajor(25))
        env.clock.advanceBy(1_000)
        priceGroup(id, Money.ofMajor(30))

        assertEquals(
            listOf(Money.ofMajor(30), Money.ofMajor(25), Money.ofMajor(20)),
            groupChanges(id).map { it.to },
        )
    }

    @Test
    fun `a rate that predates the log sorts below the dated changes`() = runTest {
        val id = unloggedGroup()
        env.rateHistoryDao.insert(RateChangeEntity(groupId = id, toMinor = 2000, atMillis = null))
        priceGroup(id, Money.ofMajor(25))

        val changes = groupChanges(id)
        assertEquals(Money.ofMajor(25), changes.first().to)
        assertNull(changes.last().at, "the undated row is last")
    }

    @Test
    fun `a group's log and a student's log do not bleed into each other`() = runTest {
        val sid = student()
        val subj = subject()
        env.enrollmentRepo.enroll(sid, subj, Money.ofMajor(40))
        val id = newPricedGroup(Money.ofMajor(25))

        assertEquals(Money.ofMajor(40), changesFor(sid, subj).single().to)
        assertEquals(Money.ofMajor(25), groupChanges(id).single().to)
    }

    @Test
    fun `enrolling logs the price it was enrolled at`() = runTest {
        val sid = student()
        val subj = subject()
        env.enrollmentRepo.enroll(sid, subj, Money.ofMajor(30))

        val change = changesFor(sid, subj).single()
        assertTrue(change.from == null)
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
        assertTrue(change.from == null)
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
        env.lessonChanges.archiveWithoutUndo(env.groupRepo.getById(id)!!)

        assertEquals(1, history.observeFor(RateOwner.GroupRate(id)).first().size)
    }
}
