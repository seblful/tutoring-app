package com.seblful.klas.data.repository

import com.seblful.klas.data.people.StudentSubjectCrossRef
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupRepositoryTest {

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    val env = TestEnv()
    private val repo = env.groupRepo

    private suspend fun students(vararg names: String): List<Long> =
        names.map { env.fixtures.student(name = it) }

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
    fun `editing a group returns its real id - not the upsert sentinel`() = runTest {
        val (ann, bob) = students("Аня", "Борис")
        val id = repo.save(Group(name = "Группа"), setOf(ann))

        val again = repo.save(Group(id = id, name = "Группа"), setOf(ann, bob))

        assertEquals(id, again)
        assertEquals(setOf(ann, bob), repo.getMemberIds(id).toSet())
    }

    @Test
    fun `a subject-bound group keeps only the students priced for that subject`() = runTest {
        val (ann, bob) = students("Аня", "Борис")
        val algebra = env.fixtures.subject(name = "Алгебра")
        env.studentDao.addSubjectLink(StudentSubjectCrossRef(ann, algebra))

        val id = repo.save(Group(name = "11 «А»", subjectId = algebra), setOf(ann, bob))

        assertEquals(setOf(ann), repo.getMemberIds(id).toSet())
        assertEquals(algebra, repo.getById(id)?.subjectId)
    }

    @Test
    fun `saving a one-member group preserves its roster`() = runTest {
        val (ann) = students("Аня")

        val id = repo.save(Group(name = "Группа"), setOf(ann))

        assertEquals(listOf(ann), repo.getMemberIds(id))
    }

    @Test
    fun `a group with no subject still takes anyone`() = runTest {
        val (ann, bob) = students("Аня", "Борис")

        val id = repo.save(Group(name = "Старая группа"), setOf(ann, bob))

        assertEquals(setOf(ann, bob), repo.getMemberIds(id).toSet())
        assertEquals(null, repo.getById(id)?.subjectId)
    }

    @Test
    fun `giving an existing group a subject drops the members who are not priced for it`() = runTest {
        val (ann, bob) = students("Аня", "Борис")
        val algebra = env.fixtures.subject(name = "Алгебра")
        env.studentDao.addSubjectLink(StudentSubjectCrossRef(ann, algebra))
        val id = repo.save(Group(name = "Группа"), setOf(ann, bob))
        assertEquals(setOf(ann, bob), repo.getMemberIds(id).toSet())

        repo.save(Group(id = id, name = "Группа", subjectId = algebra), setOf(ann, bob))

        assertEquals(setOf(ann), repo.getMemberIds(id).toSet())
    }

    @Test
    fun `an unused group can be deleted - and its memberships go with it`() = runTest {
        val (ann) = students("Аня")
        val id = repo.save(Group(name = "Ошибка"), setOf(ann))

        assertTrue(repo.delete(repo.getById(id)!!))

        assertEquals(null, repo.getById(id))
        assertTrue(env.stored().groupMembers.isEmpty())
    }

    @Test
    fun `deleting a group is one transaction that rechecks its lessons`() = runTest {
        val (ann) = students("Аня")
        val subject = env.fixtures.subject(name = "Алгебра")
        val id = repo.save(Group(name = "11 «А»"), setOf(ann))
        val group = repo.getById(id)!!
        val before = env.transactor.count

        val gate = env.transactor.pauseNextTransaction()
        val deleted = async { repo.delete(group) }
        gate.awaitEntry()
        try {
            env.fixtures.storedClass(
                studentId = ann, subjectId = subject, startMillis = 1, durationMinutes = 60,
                status = LessonStatus.COMPLETED, groupId = id,
            )
        } finally {
            gate.release()
        }

        assertFalse(deleted.await(), "a lesson booked while the delete waited keeps the group")
        assertEquals(1, env.transactor.count - before)
        assertEquals("11 «А»", repo.getById(id)?.name)
    }

    @Test
    fun `a group with lessons is not deletable — those lessons must keep naming it`() = runTest {
        val (ann) = students("Аня")
        val subject = env.fixtures.subject(name = "Алгебра")
        val id = repo.save(Group(name = "11 «А»"), setOf(ann))
        env.fixtures.storedClass(
            studentId = ann, subjectId = subject, startMillis = 1, durationMinutes = 60,
            status = LessonStatus.COMPLETED, groupId = id,
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
        assertEquals(listOf(ann), repo.getMemberIds(id))
    }

    @Test
    fun `every group's roster is observable at once`() = runTest {
        val (ann, bob) = students("Аня", "Борис")
        val first = repo.save(Group(name = "Первая"), setOf(ann, bob))
        val second = repo.save(Group(name = "Вторая"), setOf(bob))

        val rosters = repo.observeRosters().first()

        assertEquals(setOf(ann, bob), rosters[first]?.toSet())
        assertEquals(listOf(bob), rosters[second])
    }
}
