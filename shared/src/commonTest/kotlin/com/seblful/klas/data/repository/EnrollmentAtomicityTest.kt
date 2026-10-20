package com.seblful.klas.data.repository

import com.seblful.klas.data.people.EnrollmentRepository
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class EnrollmentAtomicityTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    val env = TestEnv()

    private inner class Fixture {
        val env: TestEnv get() = this@EnrollmentAtomicityTest.env

        var ivan = 0L
        var algebra = 0L
        var physics = 0L

        suspend fun seed() {
            ivan = env.studentDao.upsert(StudentEntity(name = "Иван"))
            algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
            physics = env.subjectDao.upsert(SubjectEntity(name = "Физика"))
        }
    }

    @Test
    fun `enrolling links the subject and prices it in one transaction`() = runTest {
        val f = Fixture()
        f.seed()
        val before = f.env.transactor.count

        f.env.enrollmentRepo.enroll(f.ivan, f.algebra, Money.ofMajor(30))

        assertEquals(1, f.env.transactor.count - before, "the link and the price are one write")
        assertEquals(1, f.env.transactor.maxDepth, "no nesting")
        assertEquals(0, f.env.transactor.depth, "nothing left open")
    }

    @Test
    fun `replacing a student's enrollments wholesale is a single transaction`() = runTest {
        val f = Fixture()
        f.seed()
        f.env.enrollmentRepo.setEnrollments(f.ivan, mapOf(f.algebra to Money.ofMajor(30)))
        val before = f.env.transactor.count

        f.env.enrollmentRepo.setEnrollments(f.ivan, mapOf(f.physics to Money.ofMajor(25)))

        assertEquals(1, f.env.transactor.count - before, "one subject dropped, one added, one write")
        assertEquals(1, f.env.transactor.maxDepth, "no nesting")
        // And it really did both halves, so the transaction isn't wrapping a no-op.
        assertEquals(listOf(f.physics), f.env.studentDao.getSubjectIds(f.ivan))
        assertEquals(listOf(f.physics), f.env.stored().prices.map { it.subjectId })
    }

    @Test
    fun `re-pricing writes the price and its log entry together`() = runTest {
        val f = Fixture()
        f.seed()
        f.env.enrollmentRepo.enroll(f.ivan, f.algebra, Money.ofMajor(30))
        val before = f.env.transactor.count

        f.env.enrollmentRepo.reprice(f.ivan, f.algebra, Money.ofMajor(35))

        assertEquals(1, f.env.transactor.count - before, "a rate never changes without its log")
        assertEquals(2, f.env.stored().rateHistory.size) // the initial rate, then the change
    }

    @Test
    fun `un-enrolling unlinks - unprices and forgets the log in one transaction`() = runTest {
        val f = Fixture()
        f.seed()
        f.env.enrollmentRepo.enroll(f.ivan, f.algebra, Money.ofMajor(30))
        val before = f.env.transactor.count

        val outcome = f.env.enrollmentRepo.unenroll(f.ivan, f.algebra)

        assertEquals(EnrollmentRepository.Unenroll.Ok, outcome)
        assertEquals(1, f.env.transactor.count - before)
        assertEquals(emptyList<Long>(), f.env.studentDao.getSubjectIds(f.ivan))
        assertEquals(emptyList<Any>(), f.env.stored().rateHistory)
    }

    @Test
    fun `a refused un-enrollment opens no transaction at all`() = runTest {
        val f = Fixture()
        f.seed()
        f.env.enrollmentRepo.enroll(f.ivan, f.algebra, Money.ofMajor(30))
        f.env.fixtures.completedLesson(
            subjectId = f.algebra,
            students = listOf(f.ivan),
            start = Instant.parse("2026-05-04T10:00:00Z"),
            rate = Money.ofMajor(30),
        )
        val before = f.env.transactor.count

        val outcome = f.env.enrollmentRepo.unenroll(f.ivan, f.algebra)

        assertEquals(EnrollmentRepository.Unenroll.HasClasses, outcome)
        assertEquals(0, f.env.transactor.count - before, "nothing to roll back means nothing to open")
        assertEquals(listOf(f.algebra), f.env.studentDao.getSubjectIds(f.ivan), "and the enrollment stands")
    }

    @Test
    fun `an enrollment that fails to commit is reported as a failure`() = runTest {
        val f = Fixture()
        f.seed()
        f.env.transactor.failAt = f.env.transactor.count + 1

        val failure = runCatching { f.env.enrollmentRepo.enroll(f.ivan, f.algebra, Money.ofMajor(30)) }

        assertTrue(failure.isFailure, "the caller has to hear about it")
        val stored = f.env.stored()
        assertEquals(emptyList<Any>(), stored.studentSubjects, "no link without its price")
        assertEquals(emptyList<Any>(), stored.prices, "no price without its link")
    }

    @Test
    fun `saving a group writes it with its roster in one transaction`() = runTest {
        val f = Fixture()
        f.seed()
        val ann = f.env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = f.env.studentDao.upsert(StudentEntity(name = "Борис"))
        val before = f.env.transactor.count

        val id = f.env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), setOf(ann, bob)).id

        assertEquals(1, f.env.transactor.count - before, "the group, its rate log and both links")
        assertEquals(1, f.env.transactor.maxDepth, "no nesting")
        assertEquals(setOf(ann, bob), f.env.groupDao.getMemberIds(id).toSet())
    }

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

        assertEquals(1, f.env.transactor.count - before, "one out, one in, one write")
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
        assertEquals(2, f.env.stored().rateHistory.size, "the rise is logged")
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

        assertTrue(failure.isFailure, "a half-saved group must not read as a saved one")
        val stored = f.env.stored()
        assertEquals(emptyList<Any>(), stored.groups, "no group row")
        assertEquals(emptyList<Any>(), stored.groupMembers, "no roster")
        assertEquals(emptyList<Any>(), stored.rateHistory, "no opening rate")
    }
}
