package com.seblful.klas.data.repository

import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.StudentSubjectCrossRef
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `GroupRepository` is the only writer of group membership, so these cover the two things that can
 * corrupt a roster: an edit leaving stale members behind, and deleting a group that lessons refer to.
 */
@RunWith(RobolectricTestRunner::class)
class GroupRepositoryTest {

    @get:Rule
    val env = TestEnv()
    private val repo = env.groupRepo

    private suspend fun students(vararg names: String): List<Long> =
        names.map { env.studentDao.upsert(StudentEntity(name = it)) }

    @Test
    fun `saving a group creates it with exactly the chosen members`() = runTest {
        val (ann, bob) = students("Аня", "Борис")

        val id = repo.save(Group(name = "11 «А»", defaultMode = ClassMode.REMOTE), setOf(ann, bob)).id

        val saved = repo.getById(id)!!
        assertEquals("11 «А»", saved.name)
        assertEquals(ClassMode.REMOTE, saved.defaultMode)
        assertEquals(setOf(ann, bob), repo.getMemberIds(id).toSet())
    }

    @Test
    fun `re-saving a group replaces its roster instead of adding to it`() = runTest {
        val (ann, bob, carl) = students("Аня", "Борис", "Вова")
        val id = repo.save(Group(name = "Группа"), setOf(ann, bob)).id

        repo.save(Group(id = id, name = "Группа"), setOf(bob, carl))

        // Ann is gone, Carl is in, Bob wasn't duplicated.
        assertEquals(setOf(bob, carl), repo.getMemberIds(id).toSet())
        assertEquals(2, repo.getMemberIds(id).size)
    }

    @Test
    fun `saving the same roster twice is idempotent`() = runTest {
        val (ann) = students("Аня")
        val id = repo.save(Group(name = "Группа"), setOf(ann)).id

        repo.save(Group(id = id, name = "Группа"), setOf(ann))

        assertEquals(listOf(ann), repo.getMemberIds(id))
    }

    @Test
    fun `editing a group returns its real id, not the upsert sentinel`() = runTest {
        val (ann, bob) = students("Аня", "Борис")
        val id = repo.save(Group(name = "Группа"), setOf(ann)).id

        // Room's @Upsert answers -1 when it updates instead of inserting. Passing that straight
        // through made every new member row point at group -1 and blow up on the foreign key.
        val again = repo.save(Group(id = id, name = "Группа"), setOf(ann, bob)).id

        assertEquals(id, again)
        assertEquals(setOf(ann, bob), repo.getMemberIds(id).toSet())
    }

    @Test
    fun `a subject-bound group keeps only the students priced for that subject, and says who it dropped`() = runTest {
        val (ann, bob) = students("Аня", "Борис")
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        env.studentDao.addSubjectLink(StudentSubjectCrossRef(ann, algebra))

        // Bob is asked for but isn't priced for algebra, so he could never be billed for one of this
        // group's lessons — the roster is narrowed rather than left to fail at booking time, and the
        // save hands back who it left out so the screen can say so without a rule of its own.
        val saved = repo.save(Group(name = "11 «А»", subjectId = algebra), setOf(ann, bob))

        assertEquals(setOf(bob), saved.dropped)
        assertEquals(setOf(ann), repo.getMemberIds(saved.id).toSet())
        assertEquals(algebra, repo.getById(saved.id)?.subjectId)
    }

    @Test
    fun `a save that keeps everybody reports nobody dropped`() = runTest {
        val (ann) = students("Аня")

        val saved = repo.save(Group(name = "Группа"), setOf(ann))

        assertEquals(emptySet<Long>(), saved.dropped)
    }

    @Test
    fun `a group with no subject still takes anyone`() = runTest {
        val (ann, bob) = students("Аня", "Борис")

        // Groups made before subjects existed keep the old freedom; nothing is silently dropped.
        val id = repo.save(Group(name = "Старая группа"), setOf(ann, bob)).id

        assertEquals(setOf(ann, bob), repo.getMemberIds(id).toSet())
        assertEquals(null, repo.getById(id)?.subjectId)
    }

    @Test
    fun `giving an existing group a subject drops the members who are not priced for it`() = runTest {
        val (ann, bob) = students("Аня", "Борис")
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        env.studentDao.addSubjectLink(StudentSubjectCrossRef(ann, algebra))
        val id = repo.save(Group(name = "Группа"), setOf(ann, bob)).id
        assertEquals(setOf(ann, bob), repo.getMemberIds(id).toSet())

        repo.save(Group(id = id, name = "Группа", subjectId = algebra), setOf(ann, bob))

        assertEquals(setOf(ann), repo.getMemberIds(id).toSet())
    }

    @Test
    fun `an unused group can be deleted, and its memberships go with it`() = runTest {
        val (ann) = students("Аня")
        val id = repo.save(Group(name = "Ошибка"), setOf(ann)).id

        assertTrue(repo.delete(repo.getById(id)!!))

        assertEquals(null, repo.getById(id))
        assertTrue(env.stored().groupMembers.isEmpty())
    }

    @Test
    fun `a group with lessons is not deletable — those lessons must keep naming it`() = runTest {
        val (ann) = students("Аня")
        val subject = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val id = repo.save(Group(name = "11 «А»"), setOf(ann)).id
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
        val id = repo.save(Group(name = "Выпускники"), setOf(ann)).id

        repo.setArchived(repo.getById(id)!!, true)

        assertTrue(repo.observeActive().first().isEmpty())
        assertEquals(listOf("Выпускники"), repo.observeAll().first().map { it.name })
        assertTrue(repo.getById(id)!!.archived)
        // Archiving never touches the roster.
        assertEquals(listOf(ann), repo.getMemberIds(id))
    }

    @Test
    fun `every group's roster is observable at once`() = runTest {
        val (ann, bob) = students("Аня", "Борис")
        val first = repo.save(Group(name = "Первая"), setOf(ann, bob)).id
        val second = repo.save(Group(name = "Вторая"), setOf(bob)).id

        val rosters = repo.observeRosters().first()

        assertEquals(setOf(ann, bob), rosters[first]?.toSet())
        assertEquals(listOf(bob), rosters[second])
    }
}
