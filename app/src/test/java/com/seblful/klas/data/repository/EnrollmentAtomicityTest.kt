package com.seblful.klas.data.repository

import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class EnrollmentAtomicityTest {

    @get:Rule
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

        assertEquals("the link and the price are one write", 1, f.env.transactor.count - before)
        assertEquals("no nesting", 1, f.env.transactor.maxDepth)
        assertEquals("nothing left open", 0, f.env.transactor.depth)
    }

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
        assertEquals(listOf(f.physics), f.env.stored().prices.map { it.subjectId })
    }

    @Test
    fun `re-pricing writes the price and its log entry together`() = runTest {
        val f = Fixture()
        f.seed()
        f.env.enrollmentRepo.enroll(f.ivan, f.algebra, Money.ofMajor(30))
        val before = f.env.transactor.count

        f.env.enrollmentRepo.reprice(f.ivan, f.algebra, Money.ofMajor(35))

        assertEquals("a rate never changes without its log", 1, f.env.transactor.count - before)
        assertEquals(2, f.env.stored().rateHistory.size) // the initial rate, then the change
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
        val stored = f.env.stored()
        assertEquals("no link without its price", emptyList<Any>(), stored.studentSubjects)
        assertEquals("no price without its link", emptyList<Any>(), stored.prices)
    }

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
        assertEquals("the rise is logged", 2, f.env.stored().rateHistory.size)
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
        val stored = f.env.stored()
        assertEquals("no group row", emptyList<Any>(), stored.groups)
        assertEquals("no roster", emptyList<Any>(), stored.groupMembers)
        assertEquals("no opening rate", emptyList<Any>(), stored.rateHistory)
    }
}
