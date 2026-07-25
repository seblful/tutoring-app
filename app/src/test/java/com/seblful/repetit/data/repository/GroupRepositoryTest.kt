package com.seblful.repetit.data.repository

import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.StudentSubjectCrossRef
import com.seblful.repetit.data.local.SubjectEntity
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.Group
import com.seblful.repetit.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GroupRepository` is the only writer of group membership, so these cover the two things that can
 * corrupt a roster: an edit leaving stale members behind, and deleting a group that lessons refer to.
 */
class GroupRepositoryTest {

    private val env = TestEnv()
    private val repo = env.groupRepo

    private suspend fun students(vararg names: String): List<Long> =
        names.map { env.studentDao.upsert(StudentEntity(name = it)) }

    @Test
    fun `saving a group creates it with exactly the chosen members`() = runTest {
        val (ann, bob) = students("Аня", "Борис")

        val id = repo.save(Group(name = "11 «А»", defaultMode = ClassMode.REMOTE), setOf(ann, bob))

        val saved = repo.getById(id)!!
        assertEquals("11 «А»", saved.name)
        assertEquals(ClassMode.REMOTE, saved.defaultMode)
        assertEquals(setOf(ann, bob), repo.getMemberIds(id).toSet())
    }

    @Test
    fun `re-saving a group replaces its roster instead of adding to it`() = runTest {
        val (ann, bob, carl) = students("Аня", "Борис", "Вова")
        val id = repo.save(Group(name = "Группа"), setOf(ann, bob))

        repo.save(Group(id = id, name = "Группа"), setOf(bob, carl))

        // Ann is gone, Carl is in, Bob wasn't duplicated.
        assertEquals(setOf(bob, carl), repo.getMemberIds(id).toSet())
        assertEquals(2, repo.getMemberIds(id).size)
    }

    @Test
    fun `saving the same roster twice is idempotent`() = runTest {
        val (ann) = students("Аня")
        val id = repo.save(Group(name = "Группа"), setOf(ann))

        repo.save(Group(id = id, name = "Группа"), setOf(ann))

        assertEquals(listOf(ann), repo.getMemberIds(id))
    }

    @Test
    fun `editing a group returns its real id, not the upsert sentinel`() = runTest {
        val (ann, bob) = students("Аня", "Борис")
        val id = repo.save(Group(name = "Группа"), setOf(ann))

        // Room's @Upsert answers -1 when it updates instead of inserting. Passing that straight
        // through made every new member row point at group -1 and blow up on the foreign key.
        val again = repo.save(Group(id = id, name = "Группа"), setOf(ann, bob))

        assertEquals(id, again)
        assertEquals(setOf(ann, bob), repo.getMemberIds(id).toSet())
    }

    @Test
    fun `a subject-bound group keeps only the students priced for that subject`() = runTest {
        val (ann, bob) = students("Аня", "Борис")
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        env.studentDao.addSubjectLink(StudentSubjectCrossRef(ann, algebra))

        // Bob is asked for but isn't priced for algebra, so he could never be billed for one of this
        // group's lessons — the roster is narrowed rather than left to fail at booking time.
        val id = repo.save(Group(name = "11 «А»", subjectId = algebra), setOf(ann, bob))

        assertEquals(setOf(ann), repo.getMemberIds(id).toSet())
        assertEquals(algebra, repo.getById(id)?.subjectId)
    }

    @Test
    fun `a group with no subject still takes anyone`() = runTest {
        val (ann, bob) = students("Аня", "Борис")

        // Groups made before subjects existed keep the old freedom; nothing is silently dropped.
        val id = repo.save(Group(name = "Старая группа"), setOf(ann, bob))

        assertEquals(setOf(ann, bob), repo.getMemberIds(id).toSet())
        assertEquals(null, repo.getById(id)?.subjectId)
    }

    @Test
    fun `giving an existing group a subject drops the members who are not priced for it`() = runTest {
        val (ann, bob) = students("Аня", "Борис")
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        env.studentDao.addSubjectLink(StudentSubjectCrossRef(ann, algebra))
        val id = repo.save(Group(name = "Группа"), setOf(ann, bob))
        assertEquals(setOf(ann, bob), repo.getMemberIds(id).toSet())

        repo.save(Group(id = id, name = "Группа", subjectId = algebra), setOf(ann, bob))

        assertEquals(setOf(ann), repo.getMemberIds(id).toSet())
    }

    @Test
    fun `an unused group can be deleted, and its memberships go with it`() = runTest {
        val (ann) = students("Аня")
        val id = repo.save(Group(name = "Ошибка"), setOf(ann))

        assertTrue(repo.delete(repo.getById(id)!!))

        assertEquals(null, repo.getById(id))
        assertTrue(env.groupDao.members.value.isEmpty())
    }

    @Test
    fun `a group with lessons is not deletable — those lessons must keep naming it`() = runTest {
        val (ann) = students("Аня")
        val subject = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val id = repo.save(Group(name = "11 «А»"), setOf(ann))
        env.classDao.upsert(
            ClassEntity(
                studentId = ann, subjectId = subject, startMillis = 1, durationMinutes = 60,
                status = "COMPLETED", groupId = id,
            ),
        )

        assertFalse(repo.delete(repo.getById(id)!!))
        assertEquals("11 «А»", repo.getById(id)?.name)
    }

    @Test
    fun `archiving hides a group from the active list but keeps it`() = runTest {
        val (ann) = students("Аня")
        val id = repo.save(Group(name = "Выпускники"), setOf(ann))

        repo.setArchived(repo.getById(id)!!, true)

        assertTrue(repo.observeActive().first().isEmpty())
        assertEquals(listOf("Выпускники"), repo.observeAll().first().map { it.name })
        assertTrue(repo.getById(id)!!.archived)
        // Archiving never touches the roster.
        assertEquals(listOf(ann), repo.getMemberIds(id))
    }

    @Test
    fun `memberships are observable per student`() = runTest {
        val (ann, bob) = students("Аня", "Борис")
        val first = repo.save(Group(name = "Первая"), setOf(ann, bob))
        val second = repo.save(Group(name = "Вторая"), setOf(bob))

        val byStudent = repo.observeMembershipsByStudent().first()

        assertEquals(listOf(first), byStudent[ann])
        assertEquals(setOf(first, second), byStudent[bob]?.toSet())
    }
}
