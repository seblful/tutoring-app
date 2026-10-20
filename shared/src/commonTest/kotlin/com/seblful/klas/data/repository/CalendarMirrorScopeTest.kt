package com.seblful.klas.data.repository

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/** What a write re-mirrors: the events whose content it changes, and no others. */
class CalendarMirrorScopeTest {

    private val env = TestEnv()
    private val fixtures = env.fixtures
    private val tomorrow = env.clock.now() + 1.days

    @AfterTest
    fun closeEnvironment() = env.close()

    @Test
    fun `saving a student without changing their name or address leaves their events alone`() = runTest {
        fixtures.withCalendarSync()
        val subject = fixtures.subject()
        val student = fixtures.pricedStudent(subjectId = subject, email = "anya@example.com")
        fixtures.lesson(subject, listOf(student), tomorrow)
        env.calendar.calls.clear()

        env.studentRepo.upsert(env.studentRepo.getById(student)!!)

        assertTrue(env.calendar.calls.isEmpty())
    }

    @Test
    fun `saving a group without renaming it leaves its events alone`() = runTest {
        fixtures.withCalendarSync()
        val subject = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", subject)
        val boris = fixtures.pricedStudent("Борис", subject)
        val group = fixtures.group(subjectId = subject, members = setOf(anya, boris))
        fixtures.lesson(subject, listOf(anya, boris), tomorrow, groupId = group)
        env.calendar.calls.clear()

        env.groupRepo.save(env.groupRepo.getById(group)!!, memberIds = setOf(anya))

        assertTrue(env.calendar.calls.isEmpty())
    }

    @Test
    fun `ticking homework re-mirrors only the lesson it is on`() = runTest {
        fixtures.withCalendarSync()
        val subject = fixtures.subject()
        val student = fixtures.pricedStudent(subjectId = subject)
        val first = fixtures.lesson(subject, listOf(student), tomorrow).single()
        fixtures.lesson(subject, listOf(student), tomorrow + 1.days)
        fixtures.lesson(subject, listOf(student), tomorrow + 2.days)
        val homework = env.homeworkRepo.give(Assignee.Student(student), "Exercise 12")
        env.calendar.calls.clear()

        env.homeworkRepo.setDone(homework, done = true)

        assertEquals(listOf(first), env.calendar.upserts.map { it.id })
    }

    @Test
    fun `a settings resync re-mirrors only the lessons not yet over`() = runTest {
        val subject = fixtures.subject()
        val student = fixtures.pricedStudent(subjectId = subject)
        fixtures.lesson(subject, listOf(student), env.clock.now() - 2.days)
        val ahead = fixtures.lesson(subject, listOf(student), tomorrow).single()
        fixtures.withCalendarSync()
        env.calendar.calls.clear()

        env.calendarMirror.resyncUpcoming()

        assertEquals(listOf(ahead), env.calendar.upserts.map { it.id })
    }

    @Test
    fun `a backfill stores its event ids in one transaction`() = runTest {
        val subject = fixtures.subject()
        val student = fixtures.pricedStudent(subjectId = subject)
        repeat(3) { fixtures.lesson(subject, listOf(student), tomorrow + it.days) }
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val before = env.transactor.count

        env.calendarMirror.resyncAll()

        assertEquals(1, env.transactor.count - before)
        assertTrue(env.classes().all { it.calendarEventId != null })
    }
}
