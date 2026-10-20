package com.seblful.klas.data.repository

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class HomeworkCalendarTest {

    private val env = TestEnv()
    private val fixtures = env.fixtures
    private val repo = env.homeworkRepo
    private val tomorrow = env.clock.now() + 1.days
    private val later = tomorrow + 1.days

    @AfterTest
    fun closeEnvironment() = env.close()

    private fun description(classId: Long): String =
        env.calendar.calls.last { it.klass.id == classId }.description

    @Test
    fun `homework given before booking is included in the invitation`() = runTest {
        fixtures.withCalendarSync(invitesEnabled = true)
        val subject = fixtures.subject()
        val student = fixtures.pricedStudent(subjectId = subject, email = "anya@example.com")
        repo.give(Assignee.Student(student), "Read chapter 12")

        val lesson = fixtures.lesson(subject, listOf(student), tomorrow).single()

        assertEquals("Read chapter 12", description(lesson))
        assertEquals("anya@example.com", env.calendar.lastGuest?.email)
        assertEquals(true, env.calendar.lastGuest?.invited)
    }

    @Test
    fun `group invitations include shared homework and only the recipient's individual homework`() = runTest {
        fixtures.withCalendarSync(invitesEnabled = true)
        val subject = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", subject, email = "anya@example.com")
        val boris = fixtures.pricedStudent("Борис", subject, email = "boris@example.com")
        val group = fixtures.group(subjectId = subject, members = setOf(anya, boris))
        val lessons = fixtures.lesson(subject, listOf(anya, boris), tomorrow, groupId = group)
        repo.give(Assignee.Group(group), "Shared exercise")
        env.clock.advanceBy(1000)
        repo.give(Assignee.Student(anya), "Anya's exercise")
        repo.give(Assignee.Student(boris), "Boris's exercise")

        assertEquals("Shared exercise\n\nAnya's exercise", description(lessons[0]))
        assertEquals("Shared exercise\n\nBoris's exercise", description(lessons[1]))
    }

    @Test
    fun `reassigning homework clears the old invitation and updates the new one`() = runTest {
        fixtures.withCalendarSync()
        val subject = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", subject)
        val boris = fixtures.pricedStudent("Борис", subject)
        val anyasLesson = fixtures.lesson(subject, listOf(anya), tomorrow).single()
        val borisLesson = fixtures.lesson(subject, listOf(boris), later).single()
        val homework = repo.give(Assignee.Student(anya), "Exercise 12")

        repo.edit(homework, Assignee.Student(boris), "Exercise 13")

        assertEquals("", description(anyasLesson))
        assertEquals("Exercise 13", description(borisLesson))
    }

    @Test
    fun `moving the first lesson beyond the second transfers homework between existing events`() = runTest {
        fixtures.withCalendarSync()
        val subject = fixtures.subject()
        val student = fixtures.pricedStudent(subjectId = subject)
        val first = fixtures.lesson(subject, listOf(student), tomorrow).single()
        val second = fixtures.lesson(subject, listOf(student), later).single()
        repo.give(Assignee.Student(student), "Exercise 12")

        env.classRepo.move(env.classRepo.getSessionFor(first)!!, later + 1.days, env.clock.now()).written()

        assertEquals("", description(first))
        assertEquals("Exercise 12", description(second))
    }

    @Test
    fun `cancelling and restoring a lesson moves homework to and from the following invitation`() = runTest {
        fixtures.withCalendarSync()
        val subject = fixtures.subject()
        val student = fixtures.pricedStudent(subjectId = subject)
        val first = fixtures.lesson(subject, listOf(student), tomorrow).single()
        val second = fixtures.lesson(subject, listOf(student), later).single()
        repo.give(Assignee.Student(student), "Exercise 12")

        fixtures.setStatus(first, LessonStatus.CANCELLED)
        assertEquals("Exercise 12", description(second))

        fixtures.setStatus(first, LessonStatus.SCHEDULED)
        assertEquals("Exercise 12", description(first))
        assertEquals("", description(second))
    }

    @Test
    fun `deleting a lesson puts homework on the next invitation`() = runTest {
        fixtures.withCalendarSync()
        val subject = fixtures.subject()
        val student = fixtures.pricedStudent(subjectId = subject)
        val first = fixtures.lesson(subject, listOf(student), tomorrow).single()
        val second = fixtures.lesson(subject, listOf(student), later).single()
        repo.give(Assignee.Student(student), "Exercise 12")

        env.classRepo.delete(env.classRepo.getSessionFor(first)!!)

        assertEquals("Exercise 12", description(second))
    }

    @Test
    fun `booking an earlier lesson clears homework from the later invitation`() = runTest {
        fixtures.withCalendarSync()
        val subject = fixtures.subject()
        val student = fixtures.pricedStudent(subjectId = subject)
        val second = fixtures.lesson(subject, listOf(student), later).single()
        repo.give(Assignee.Student(student), "Exercise 12")

        val first = fixtures.lesson(subject, listOf(student), tomorrow).single()

        assertEquals("Exercise 12", description(first))
        assertEquals("", description(second))
    }

    @Test
    fun `homework saved with sync off is backfilled without enabling email invitations`() = runTest {
        val subject = fixtures.subject()
        val student = fixtures.pricedStudent(subjectId = subject, email = "anya@example.com")
        val lesson = fixtures.lesson(subject, listOf(student), tomorrow).single()
        repo.give(Assignee.Student(student), "Exercise 12")
        assertTrue(env.calendar.calls.isEmpty())

        fixtures.withCalendarSync(invitesEnabled = false)
        env.calendarMirror.resyncAll()

        assertEquals("Exercise 12", description(lesson))
        assertFalse(env.calendar.lastGuest!!.invited)
    }

    @Test
    fun `marking homework done clears a completed lesson's existing event`() = runTest {
        fixtures.withCalendarSync()
        val subject = fixtures.subject()
        val student = fixtures.pricedStudent(subjectId = subject)
        val lesson = fixtures.lesson(subject, listOf(student), tomorrow).single()
        val homework = repo.give(Assignee.Student(student), "Exercise 12")
        fixtures.setStatus(lesson, LessonStatus.COMPLETED)
        assertEquals("Exercise 12", env.calendar.refreshes.last().description)
        env.calendar.calls.clear()

        repo.setDone(homework, done = true)

        assertEquals("", env.calendar.refreshes.last().description)
        assertTrue(env.calendar.calls.isEmpty())
    }
}
