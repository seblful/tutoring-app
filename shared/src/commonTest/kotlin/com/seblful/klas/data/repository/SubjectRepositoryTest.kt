package com.seblful.klas.data.repository

import com.seblful.klas.data.billing.PriceEntity
import com.seblful.klas.data.billing.RateChangeEntity
import com.seblful.klas.data.local.DatabaseSnapshot
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.data.people.SubjectRepository.Save
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.store
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SubjectRepositoryTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    val env = TestEnv()

    @Test
    fun `an unreferenced subject is deleted`() = runTest {
        val id = env.fixtures.subject(name = "Химия")

        assertEquals(SubjectRepository.Delete.Ok, env.subjectRepo.delete(Subject(id = id, name = "Химия")))
        assertTrue(env.stored().subjects.isEmpty())
    }

    @Test
    fun `a subject with classes is refused`() = runTest {
        val student = env.fixtures.student(name = "Аня")
        val id = env.fixtures.subject(name = "Химия")
        env.fixtures.storedClass(
            studentId = student,
            subjectId = id,
            startMillis = 0,
            durationMinutes = 60,
            status = LessonStatus.COMPLETED,
            chargeMinor = 5000,
        )

        env.paymentRepo.restore(Payment(studentId = student, date = env.clock.now(), amount = Money(2000)))

        assertEquals(SubjectRepository.Delete.HasClasses, env.subjectRepo.delete(Subject(id = id, name = "Химия")))
        assertEquals(1, env.stored().subjects.size, "the subject is still there")
        assertEquals(1, env.stored().lessons.size)
        assertEquals(1, env.stored().attendance.size)
        assertEquals(Balance.Owed(Money(3000)), env.store().studentDetails.observe(student).first().balance)
    }

    @Test
    fun `a subject a group is taught is refused`() = runTest {
        val id = env.fixtures.subject(name = "Химия")
        env.groupRepo.save(Group(name = "Химия, 11 класс", subjectId = id, price = Money.ofMajor(20)), emptySet())

        assertEquals(
            SubjectRepository.Delete.TaughtToGroup,
            env.subjectRepo.delete(Subject(id = id, name = "Химия")),
            "a group is taught it, which is a different fix from removing lessons",
        )
        assertEquals(1, env.stored().subjects.size, "the subject is still there")
    }

    @Test
    fun `a subject a student takes is refused`() = runTest {
        val id = env.fixtures.subject("Физика")
        env.enrollmentRepo.createStudent(Student(name = "Аня"), mapOf(id to Money.ofMajor(30)))

        assertEquals(
            SubjectRepository.Delete.TakenByStudents,
            env.subjectRepo.delete(Subject(id = id, name = "Физика")),
        )
        assertEquals(1, env.stored().subjects.size, "the subject is still there")
        assertEquals(1, env.stored().prices.size, "and still priced")
    }

    @Test
    fun `classes are named ahead of a group when both block`() = runTest {
        val student = env.fixtures.student(name = "Аня")
        val id = env.fixtures.subject(name = "Химия")
        env.fixtures.storedClass(
            studentId = student,
            subjectId = id,
            startMillis = 0,
            durationMinutes = 60,
            status = LessonStatus.COMPLETED,
        )
        env.groupRepo.save(Group(name = "Химия, 11 класс", subjectId = id, price = Money.ofMajor(20)), emptySet())

        assertEquals(
            SubjectRepository.Delete.HasClasses,
            env.subjectRepo.delete(Subject(id = id, name = "Химия")),
        )
    }

    @Test
    fun `a subject no group is bound to is deleted even while groups exist`() = runTest {
        val id = env.fixtures.subject(name = "Химия")
        env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), emptySet())

        assertEquals(SubjectRepository.Delete.Ok, env.subjectRepo.delete(Subject(id = id, name = "Химия")))
        assertTrue(env.stored().subjects.isEmpty())
    }

    @Test
    fun `a name already taken is refused - in the same transaction as the write`() = runTest {
        val first = assertIs<Save.Saved>(env.subjectRepo.save(Subject(name = "Математика")))
        val before = env.transactor.count

        val taken = assertIs<Save.NameTaken>(env.subjectRepo.save(Subject(name = " математика ")))
        assertEquals(first.id, taken.existing.id)
        assertEquals(1, env.stored().subjects.size)
        assertEquals(before + 1, env.transactor.count)
    }

    @Test
    fun `a subject may keep its own name when renamed`() = runTest {
        val id = env.fixtures.subject("Химия")

        assertEquals(Save.Saved(id), env.subjectRepo.save(Subject(id = id, name = "химия")))
        assertEquals("химия", env.stored().subjects.single().name)
    }

    @Test
    fun `create or reuse returns one identity and keeps the existing spelling`() = runTest {
        val id = env.subjectRepo.createOrReuse("  Математика  ")

        assertEquals(id, env.subjectRepo.createOrReuse(" математика "))
        assertEquals(listOf(Subject(id, "Математика")), env.subjectRepo.observeAll().first())
    }

    @Test
    fun `concurrent creation resolves a name inside the write transaction`() = runTest {
        val gate = env.transactor.pauseNextTransaction()
        val first = async { env.subjectRepo.createOrReuse(" Math ") }
        gate.awaitEntry()
        val second = try {
            env.subjectRepo.createOrReuse("math")
        } finally {
            gate.release()
        }

        assertEquals(second, first.await())
        assertEquals(listOf(Subject(second, "math")), env.subjectRepo.observeAll().first())
    }

    @Test
    fun `a refused rename returns the existing identity and changes neither subject`() = runTest {
        val math = env.subjectRepo.createOrReuse("Math")
        val physics = env.subjectRepo.createOrReuse("Physics")
        val before = env.stored()

        assertEquals(Save.NameTaken(Subject(math, "Math")), env.subjectRepo.save(Subject(physics, " math ")))

        assertEquals(before, env.stored())
    }

    @Test
    fun `saved names are trimmed and blank names cannot be created`() = runTest {
        assertFailsWith<IllegalArgumentException> { env.subjectRepo.createOrReuse("  ") }
        assertFailsWith<IllegalArgumentException> { env.subjectRepo.save(Subject(name = "  ")) }
        assertTrue(env.stored().subjects.isEmpty())

        val saved = assertIs<Save.Saved>(env.subjectRepo.save(Subject(name = "  Physics  ")))
        assertEquals("Physics", env.subjectRepo.getById(saved.id)!!.name)
    }

    @Test
    fun `deleting a legacy subject removes its unlinked price and rate history`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.student()
        // Old imports can contain a price and its log without a student-subject enrollment.
        env.snapshotDao.insert(
            DatabaseSnapshot(
                prices = listOf(PriceEntity(student, subject, 4000)),
                rateHistory = listOf(
                    RateChangeEntity(
                        studentId = student, subjectId = subject, fromMinor = 3000, toMinor = 4000,
                        atMillis = env.clock.now().toEpochMilliseconds(),
                    ),
                ),
            ),
        )

        assertEquals(SubjectRepository.Delete.Ok, env.subjectRepo.delete(Subject(subject, "Subject")))
        assertTrue(env.stored().subjects.isEmpty())
        assertTrue(env.stored().prices.isEmpty())
        assertTrue(env.stored().rateHistory.isEmpty())
    }
}
