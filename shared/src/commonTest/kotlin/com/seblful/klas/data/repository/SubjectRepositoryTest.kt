package com.seblful.klas.data.repository

import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubjectRepositoryTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    val env = TestEnv()

    @Test
    fun `an unreferenced subject is deleted`() = runTest {
        val id = env.subjectDao.upsert(SubjectEntity(name = "Химия"))

        assertEquals(SubjectRepository.Delete.Ok, env.subjectRepo.delete(Subject(id = id, name = "Химия")))
        assertTrue(env.stored().subjects.isEmpty())
    }

    @Test
    fun `a subject with classes is refused`() = runTest {
        val student = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val id = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        env.fixtures.storedClass(
            studentId = student,
            subjectId = id,
            startMillis = 0,
            durationMinutes = 60,
            status = LessonStatus.COMPLETED,
        )

        assertEquals(SubjectRepository.Delete.HasClasses, env.subjectRepo.delete(Subject(id = id, name = "Химия")))
        assertEquals(1, env.stored().subjects.size, "the subject is still there")
    }

    @Test
    fun `a subject a group is taught is refused`() = runTest {
        val id = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
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
        val student = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val id = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
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
        val id = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), emptySet())

        assertEquals(SubjectRepository.Delete.Ok, env.subjectRepo.delete(Subject(id = id, name = "Химия")))
        assertTrue(env.stored().subjects.isEmpty())
    }

    @Test
    fun `a name already taken is refused - in the same transaction as the write`() = runTest {
        assertTrue(env.subjectRepo.saveUnlessTaken(Subject(name = "Математика")))
        val before = env.transactor.count

        assertFalse(env.subjectRepo.saveUnlessTaken(Subject(name = " математика ")))
        assertEquals(1, env.stored().subjects.size)
        assertEquals(before + 1, env.transactor.count)
    }

    @Test
    fun `a subject may keep its own name when renamed`() = runTest {
        val id = env.fixtures.subject("Химия")

        assertTrue(env.subjectRepo.saveUnlessTaken(Subject(id = id, name = "химия")))
        assertEquals("химия", env.stored().subjects.single().name)
    }
}
