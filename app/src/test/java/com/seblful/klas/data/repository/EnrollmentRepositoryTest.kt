package com.seblful.klas.data.repository

import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EnrollmentRepositoryTest {

    private val env = TestEnv()
    private val repo = env.enrollmentRepo

    private fun seedClass(studentId: Long, subjectId: Long) {
        env.classDao.items.value = env.classDao.items.value + ClassEntity(
            id = 1, studentId = studentId, subjectId = subjectId,
            startMillis = 0, durationMinutes = 60, status = "COMPLETED",
        )
    }

    @Test
    fun `enroll links the subject and sets its price together`() = runTest {
        repo.enroll(studentId = 1, subjectId = 2, price = Money.ofMajor(30))

        assertEquals(listOf(2L), env.studentDao.getSubjectIds(1))
        assertEquals(Money.ofMajor(30), env.priceDao.get(1, 2)?.amountMinor?.let { Money(it) })
    }

    @Test
    fun `unenroll removes both the link and the price when the student has no classes`() = runTest {
        repo.enroll(studentId = 1, subjectId = 2, price = Money.ofMajor(30))

        val result = repo.unenroll(studentId = 1, subjectId = 2)

        assertEquals(EnrollmentRepository.Unenroll.Ok, result)
        assertEquals(emptyList<Long>(), env.studentDao.getSubjectIds(1))
        assertNull(env.priceDao.get(1, 2))
    }

    @Test
    fun `unenroll is refused and changes nothing when the student has classes for the subject`() = runTest {
        repo.enroll(studentId = 1, subjectId = 2, price = Money.ofMajor(30))
        seedClass(studentId = 1, subjectId = 2)

        val result = repo.unenroll(studentId = 1, subjectId = 2)

        assertEquals(EnrollmentRepository.Unenroll.HasClasses, result)
        // The link and price must survive — the class is billed history that needs a priced subject.
        assertEquals(listOf(2L), env.studentDao.getSubjectIds(1))
        assertEquals(Money.ofMajor(30), env.priceDao.get(1, 2)?.amountMinor?.let { Money(it) })
    }

    @Test
    fun `a class for a different subject does not block unenrolling`() = runTest {
        repo.enroll(studentId = 1, subjectId = 2, price = Money.ofMajor(30))
        seedClass(studentId = 1, subjectId = 99) // class is for another subject

        assertEquals(EnrollmentRepository.Unenroll.Ok, repo.unenroll(studentId = 1, subjectId = 2))
    }

    @Test
    fun `reprice updates the price without touching the link`() = runTest {
        repo.enroll(studentId = 1, subjectId = 2, price = Money.ofMajor(30))

        repo.reprice(studentId = 1, subjectId = 2, price = Money.ofMajor(45))

        assertEquals(listOf(2L), env.studentDao.getSubjectIds(1))
        assertEquals(Money.ofMajor(45), env.priceDao.get(1, 2)?.amountMinor?.let { Money(it) })
    }

    @Test
    fun `setEnrollments links and prices every chosen subject`() = runTest {
        repo.setEnrollments(studentId = 1, pricedSubjects = mapOf(2L to Money.ofMajor(30), 3L to Money.ofMajor(40)))

        assertEquals(setOf(2L, 3L), env.studentDao.getSubjectIds(1).toSet())
        assertEquals(Money.ofMajor(30), env.priceDao.get(1, 2)?.amountMinor?.let { Money(it) })
        assertEquals(Money.ofMajor(40), env.priceDao.get(1, 3)?.amountMinor?.let { Money(it) })
    }

    @Test
    fun `setEnrollments drops a subject's link and price together when it is no longer present`() = runTest {
        repo.setEnrollments(studentId = 1, pricedSubjects = mapOf(2L to Money.ofMajor(30), 3L to Money.ofMajor(40)))

        repo.setEnrollments(studentId = 1, pricedSubjects = mapOf(2L to Money.ofMajor(30)))

        assertEquals(listOf(2L), env.studentDao.getSubjectIds(1))
        assertNull("the dropped subject's price must go with its link", env.priceDao.get(1, 3))
    }

    // --- The refusal the wide write used to lack ---------------------------------------------------

    /**
     * `unenroll` refuses to drop a subject the student has classes for; `setEnrollments` used to drop
     * any number of them without asking. The same subject was therefore undroppable one way and
     * droppable the other, and the wide write was the weaker one.
     */
    @Test
    fun `setEnrollments keeps a subject the student has classes for, and says which`() = runTest {
        repo.setEnrollments(studentId = 1, pricedSubjects = mapOf(2L to Money.ofMajor(30), 3L to Money.ofMajor(40)))
        seedClass(studentId = 1, subjectId = 3)

        val kept = repo.setEnrollments(studentId = 1, pricedSubjects = mapOf(2L to Money.ofMajor(30)))

        assertEquals("the subject with billed history is named", setOf(3L), kept)
        assertEquals(
            "and kept, link and price both",
            listOf(2L, 3L),
            env.studentDao.getSubjectIds(1).sorted(),
        )
        assertEquals(Money.ofMajor(40), env.priceDao.get(1, 3)?.amountMinor?.let { Money(it) })
    }

    @Test
    fun `setEnrollments still drops a subject with no classes, and names nothing`() = runTest {
        repo.setEnrollments(studentId = 1, pricedSubjects = mapOf(2L to Money.ofMajor(30), 3L to Money.ofMajor(40)))

        val kept = repo.setEnrollments(studentId = 1, pricedSubjects = mapOf(2L to Money.ofMajor(30)))

        assertEquals(emptySet<Long>(), kept)
        assertEquals(listOf(2L), env.studentDao.getSubjectIds(1))
    }

    // --- A student and their prices are one act ---------------------------------------------------

    @Test
    fun `createStudent writes the student and every priced subject in one transaction`() = runTest {
        val before = env.transactor.count

        val id = repo.createStudent(
            Student(name = "Аня"),
            mapOf(2L to Money.ofMajor(30), 3L to Money.ofMajor(25)),
        )

        assertEquals("one flat transaction, not one per subject", 1, env.transactor.count - before)
        assertEquals("Аня", env.studentDao.items.value.single().name)
        assertEquals(listOf(2L, 3L), env.studentDao.getSubjectIds(id).sorted())
        assertEquals(Money.ofMajor(30), env.priceDao.get(id, 2)?.amountMinor?.let { Money(it) })
        assertEquals(Money.ofMajor(25), env.priceDao.get(id, 3)?.amountMinor?.let { Money(it) })
    }

    /** The rate log starts at creation, so a first price is history's beginning rather than a change. */
    @Test
    fun `createStudent logs each opening rate`() = runTest {
        val id = repo.createStudent(Student(name = "Аня"), mapOf(2L to Money.ofMajor(30)))

        val logged = env.rateHistoryDao.items.value.single()
        assertEquals(id, logged.studentId)
        assertNull("no previous rate: the history starts here", logged.fromMinor)
        assertEquals(Money.ofMajor(30).minor, logged.toMinor)
    }
}
