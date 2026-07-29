package com.seblful.klas.data.repository

import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The other two invariant owners write in **one transaction** each, for the same reason
 * `ClassRepository` does: what they hold together spans several tables.
 *
 * `ClassRepository` was made transactional first because a torn *lesson* is the loudest version of the
 * problem, but it was never the only one. `EnrollmentRepository` exists to keep a subject link and its
 * price in step across three tables, and `GroupRepository.save` is the only writer of membership — for
 * both, a write that stopped halfway would break precisely the invariant the class was written to
 * hold. The fake DAOs cannot roll back, so what is asserted is the transaction *count* and, where the
 * repository reports an outcome, that a failed write reports it as a failure rather than a success.
 */
class EnrollmentAtomicityTest {

    private class Fixture(val env: TestEnv = TestEnv()) {
        var ivan = 0L
        var algebra = 0L
        var physics = 0L

        suspend fun seed() {
            ivan = env.studentDao.upsert(StudentEntity(name = "Иван"))
            algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
            physics = env.subjectDao.upsert(SubjectEntity(name = "Физика"))
        }
    }

    // --- Enrollment: a link, a price and a log entry ---------------------------------------------

    @Test
    fun `enrolling links the subject and prices it in one transaction`() = runTest {
        val f = Fixture()
        f.seed()
        val before = f.env.transactor.count

        f.env.enrollmentRepo.enroll(f.ivan, f.algebra, Money.ofMajor(30))

        assertEquals("the link and the price are one write", 1, f.env.transactor.count - before)
        assertEquals("no nesting", 1, f.env.transactor.maxDepth)
        assertEquals("nothing left open", 0, f.env.transactor.depth)
    }

    /**
     * The widest of them: three subjects arriving, one leaving, each touching all three tables. This
     * is the save the add-student form makes, and the one where "stopped halfway" would leave the
     * tutor with a student taking a subject at no price and no way to tell which.
     */
    @Test
    fun `replacing a student's enrollments wholesale is a single transaction`() = runTest {
        val f = Fixture()
        f.seed()
        f.env.enrollmentRepo.setEnrollments(f.ivan, mapOf(f.algebra to Money.ofMajor(30)))
        val before = f.env.transactor.count

        f.env.enrollmentRepo.setEnrollments(f.ivan, mapOf(f.physics to Money.ofMajor(25)))

        assertEquals("one subject dropped, one added, one write", 1, f.env.transactor.count - before)
        assertEquals("no nesting", 1, f.env.transactor.maxDepth)
        // And it really did both halves, so the transaction isn't wrapping a no-op.
        assertEquals(listOf(f.physics), f.env.studentDao.getSubjectIds(f.ivan))
        assertEquals(listOf(f.physics), f.env.priceDao.items.value.map { it.subjectId })
    }

    @Test
    fun `re-pricing writes the price and its log entry together`() = runTest {
        val f = Fixture()
        f.seed()
        f.env.enrollmentRepo.enroll(f.ivan, f.algebra, Money.ofMajor(30))
        val before = f.env.transactor.count

        f.env.enrollmentRepo.reprice(f.ivan, f.algebra, Money.ofMajor(35))

        assertEquals("a rate never changes without its log", 1, f.env.transactor.count - before)
        assertEquals(2, f.env.rateHistoryDao.items.value.size) // the initial rate, then the change
    }

    @Test
    fun `un-enrolling unlinks, unprices and forgets the log in one transaction`() = runTest {
        val f = Fixture()
        f.seed()
        f.env.enrollmentRepo.enroll(f.ivan, f.algebra, Money.ofMajor(30))
        val before = f.env.transactor.count

        val outcome = f.env.enrollmentRepo.unenroll(f.ivan, f.algebra)

        assertEquals(EnrollmentRepository.Unenroll.Ok, outcome)
        assertEquals(1, f.env.transactor.count - before)
        assertEquals(emptyList<Long>(), f.env.studentDao.getSubjectIds(f.ivan))
        assertEquals(emptyList<Any>(), f.env.rateHistoryDao.items.value)
    }

    /**
     * A refusal is not a rolled-back write — it is a decision made before any write. The count check
     * is what says the guard still sits outside the transaction, so refusing costs nothing.
     */
    @Test
    fun `a refused un-enrollment opens no transaction at all`() = runTest {
        val f = Fixture()
        f.seed()
        f.env.enrollmentRepo.enroll(f.ivan, f.algebra, Money.ofMajor(30))
        // Booked and completed the way the app does it, so the class this guard refuses over is one
        // the app could actually have produced.
        f.env.fixtures.completedLesson(
            subjectId = f.algebra,
            students = listOf(f.ivan),
            start = Instant.parse("2026-05-04T10:00:00Z"),
            rate = Money.ofMajor(30),
        )
        val before = f.env.transactor.count

        val outcome = f.env.enrollmentRepo.unenroll(f.ivan, f.algebra)

        assertEquals(EnrollmentRepository.Unenroll.HasClasses, outcome)
        assertEquals("nothing to roll back means nothing to open", 0, f.env.transactor.count - before)
        assertEquals("and the enrollment stands", listOf(f.algebra), f.env.studentDao.getSubjectIds(f.ivan))
    }

    @Test
    fun `an enrollment that fails to commit is reported as a failure`() = runTest {
        val f = Fixture()
        f.seed()
        f.env.transactor.failAt = f.env.transactor.count + 1

        val failure = runCatching { f.env.enrollmentRepo.enroll(f.ivan, f.algebra, Money.ofMajor(30)) }

        assertTrue("the caller has to hear about it", failure.isFailure)
    }

    // --- Groups: the row, its rate log and the whole roster --------------------------------------

    @Test
    fun `saving a group writes it with its roster in one transaction`() = runTest {
        val f = Fixture()
        f.seed()
        val ann = f.env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = f.env.studentDao.upsert(StudentEntity(name = "Борис"))
        val before = f.env.transactor.count

        val id = f.env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(ann, bob)).id

        assertEquals("the group, its rate log and both links", 1, f.env.transactor.count - before)
        assertEquals("no nesting", 1, f.env.transactor.maxDepth)
        assertEquals(setOf(ann, bob), f.env.groupDao.getMemberIds(id).toSet())
    }

    /**
     * The case the transaction is really for: an edit that both adds and removes members. Half of it
     * landing is a roster the tutor never asked for, and it is the roster that decides who a lesson
     * bills.
     */
    @Test
    fun `re-staffing a group adds and removes members in the same transaction`() = runTest {
        val f = Fixture()
        f.seed()
        val ann = f.env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = f.env.studentDao.upsert(StudentEntity(name = "Борис"))
        val cleo = f.env.studentDao.upsert(StudentEntity(name = "Вера"))
        val id = f.env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(ann, bob)).id
        val before = f.env.transactor.count

        f.env.groupRepo.save(
            Group(id = id, name = "11 «А»", price = Money.ofMajor(20)),
            setOf(bob, cleo),
        )

        assertEquals("one out, one in, one write", 1, f.env.transactor.count - before)
        assertEquals(setOf(bob, cleo), f.env.groupDao.getMemberIds(id).toSet())
    }

    @Test
    fun `a group's rate and its log entry are one write`() = runTest {
        val f = Fixture()
        f.seed()
        val id = f.env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), emptySet()).id
        val before = f.env.transactor.count

        f.env.groupRepo.save(Group(id = id, name = "11 «А»", price = Money.ofMajor(25)), emptySet())

        assertEquals(1, f.env.transactor.count - before)
        assertEquals("the rise is logged", 2, f.env.rateHistoryDao.items.value.size)
    }

    @Test
    fun `a group save that fails to commit is reported as a failure`() = runTest {
        val f = Fixture()
        f.seed()
        val ann = f.env.studentDao.upsert(StudentEntity(name = "Аня"))
        f.env.transactor.failAt = f.env.transactor.count + 1

        val failure = runCatching {
            f.env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(ann))
        }

        assertTrue("a half-saved group must not read as a saved one", failure.isFailure)
    }
}
