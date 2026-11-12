package com.seblful.klas.queries.people

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class PeopleDirectoryTest {
    private val env = TestEnv()
    private val start = Instant.parse("2026-05-01T10:00:00Z")

    @AfterTest
    fun close() = env.close()

    @Test
    fun `names a group lesson by its group, subject and members`() = runTest {
        val subject = env.fixtures.subject("Биология")
        val members = listOf(env.fixtures.pricedStudent("Аня", subject), env.fixtures.pricedStudent("Борис", subject))
        val group = env.fixtures.group("11 «А»", subject, members.toSet())
        val id = env.fixtures.lesson(subject, members, start, groupId = group).first()
        val session = env.classRepo.getSessionFor(id)!!

        val captions = env.peopleDirectory.observe().first()

        assertEquals("11 «А»", captions.titleOf(session))
        assertEquals("Биология", captions.subjectOf(session))
        assertEquals(listOf("Аня", "Борис"), captions.membersOf(session))
    }

    @Test
    fun `still names an archived student's lessons and homework`() = runTest {
        val subject = env.fixtures.subject("Химия")
        val student = env.fixtures.pricedStudent("Вера", subject)
        val id = env.fixtures.lesson(subject, listOf(student), start).single()
        env.lessonChanges.archiveWithoutUndo(env.studentRepo.getById(student)!!)

        val captions = env.peopleDirectory.observe().first()

        assertEquals("Вера", captions.titleOf(env.classRepo.getSessionFor(id)!!))
        assertEquals("Химия", captions.subjectOf(env.classRepo.getById(id)!!))
        assertEquals("Вера", captions.titleOf(Assignee.Student(student)))
    }
}
