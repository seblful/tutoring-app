package com.seblful.klas.data.repository

import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
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

        assertTrue(env.subjectRepo.delete(Subject(id = id, name = "Химия")))
        assertTrue(env.subjectDao.items.value.isEmpty())
    }

    @Test
    fun `a subject with classes is refused`() = runTest {
        val student = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val id = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        env.classDao.upsert(
            ClassEntity(studentId = student, subjectId = id, startMillis = 0, durationMinutes = 60, status = "COMPLETED"),
        )

        assertFalse(env.subjectRepo.delete(Subject(id = id, name = "Химия")))
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

        assertFalse(env.subjectRepo.delete(Subject(id = id, name = "Химия")))
        assertEquals("the subject is still there", 1, env.subjectDao.items.value.size)
    }

    /** A group with no subject of its own constrains nothing. */
    @Test
    fun `a subject no group is bound to is deleted even while groups exist`() = runTest {
        val id = env.subjectDao.upsert(SubjectEntity(name = "Химия"))
        env.groupRepo.save(Group(name = "11 «А»", price = Money.ofMajor(20)), emptySet())

        assertTrue(env.subjectRepo.delete(Subject(id = id, name = "Химия")))
        assertTrue(env.subjectDao.items.value.isEmpty())
    }
}
