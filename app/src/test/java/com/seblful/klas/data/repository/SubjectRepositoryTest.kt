package com.seblful.klas.data.repository

import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What stops a subject being deleted. Both refusals exist because the database would otherwise decide
 * the matter itself — `classes.subjectId` cascades and `student_groups.subjectId` restricts — and
 * neither of those answers reaches the tutor as anything but lost history or a crash.
 */
class SubjectRepositoryTest {

    private val env = TestEnv()

    @Test
    fun `an unreferenced subject is deleted`() = runTest {
        val id = env.subjectDao.upsert(SubjectEntity(name = "Химия"))

        assertEquals(SubjectRepository.Delete.Ok, env.subjectRepo.delete(Subject(id = id, name = "Химия")))
        assertTrue(env.subjectDao.items.value.isEmpty())
    }

    @Test
    fun `a subject with classes is refused`() = runTest {
        val student = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val id = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        env.fixtures.storedClass(studentId = student, subjectId = id, startMillis = 0, durationMinutes = 60, status = "COMPLETED")

        assertEquals(SubjectRepository.Delete.HasClasses, env.subjectRepo.delete(Subject(id = id, name = "Химия")))
        assertEquals("the subject is still there", 1, env.subjectDao.items.value.size)
    }

    /**
     * A group that is *taught* the subject is enough, with no lesson of its own yet. The foreign key
     * is `RESTRICT`, so before this was checked the delete reached SQLite and threw a constraint
     * violation out of the `launch` it was called in — a crash where the tutor should have been told
     * the subject is still in use.
     */
    @Test
    fun `a subject a group is taught is refused`() = runTest {
        val id = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        env.groupRepo.save(Group(name = "Химия, 11 класс", subjectId = id, price = Money.ofMajor(20)), emptySet())

        assertEquals(
            "a group is taught it, which is a different fix from removing lessons",
            SubjectRepository.Delete.TaughtToGroup,
            env.subjectRepo.delete(Subject(id = id, name = "Химия")),
        )
        assertEquals("the subject is still there", 1, env.subjectDao.items.value.size)
    }

    /**
     * A student priced for it is enough, with no lesson yet: the price cascades with the subject, so the
     * delete used to go through and take the subject off the student without a word.
     */
    @Test
    fun `a subject a student takes is refused`() = runTest {
        val id = env.fixtures.subject("Физика")
        env.enrollmentRepo.createStudent(Student(name = "Аня"), mapOf(id to Money.ofMajor(30)))

        assertEquals(
            SubjectRepository.Delete.TakenByStudents,
            env.subjectRepo.delete(Subject(id = id, name = "Физика")),
        )
        assertEquals("the subject is still there", 1, env.subjectDao.items.value.size)
        assertEquals("and still priced", 1, env.priceDao.items.value.size)
    }

    /**
     * A subject with **both** blockers names the classes, not the group. Either message is true; the
     * classes are the one the tutor has to deal with first, since changing the group's subject would
     * leave the lessons still pointing at it.
     */
    @Test
    fun `classes are named ahead of a group when both block`() = runTest {
        val student = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val id = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        env.fixtures.storedClass(studentId = student, subjectId = id, startMillis = 0, durationMinutes = 60, status = "COMPLETED")
        env.groupRepo.save(Group(name = "Химия, 11 класс", subjectId = id, price = Money.ofMajor(20)), emptySet())

        assertEquals(
            SubjectRepository.Delete.HasClasses,
            env.subjectRepo.delete(Subject(id = id, name = "Химия")),
        )
    }

    /** A group with no subject of its own constrains nothing. */
    @Test
    fun `a subject no group is bound to is deleted even while groups exist`() = runTest {
        val id = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), emptySet())

        assertEquals(SubjectRepository.Delete.Ok, env.subjectRepo.delete(Subject(id = id, name = "Химия")))
        assertTrue(env.subjectDao.items.value.isEmpty())
    }

    /** The check and the write share one transaction, so a double tap cannot run two checks first. */
    @Test
    fun `a name already taken is refused, in the same transaction as the write`() = runTest {
        assertTrue(env.subjectRepo.saveUnlessTaken(Subject(name = "Математика")))
        val before = env.transactor.count

        assertFalse(env.subjectRepo.saveUnlessTaken(Subject(name = " математика ")))
        assertEquals(1, env.subjectDao.items.value.size)
        assertEquals(before + 1, env.transactor.count)
    }

    @Test
    fun `a subject may keep its own name when renamed`() = runTest {
        val id = env.fixtures.subject("Химия")

        assertTrue(env.subjectRepo.saveUnlessTaken(Subject(id = id, name = "химия")))
        assertEquals("химия", env.subjectDao.items.value.single().name)
    }
}
