package com.seblful.klas.data.repository

import com.seblful.klas.domain.SessionEdit
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.HomeworkResult
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Homework through [ClassRepository]: the text is the lesson's and rides the lesson's write path — the
 * calendar event carries it — while a verdict is one column of one student's row and owes nothing after
 * the commit.
 */
class ClassRepositoryHomeworkTest {

    private val env = TestEnv()
    private val repo = env.classRepo
    private val fixtures = env.fixtures

    private val monday: Instant = Instant.parse("2026-09-14T15:00:00Z")
    private val nextMonday: Instant = Instant.parse("2026-09-21T15:00:00Z")

    private suspend fun session(classId: Long) = repo.getSessionFor(classId)!!

    @Test
    fun `homework set on a group lesson is every member's`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val boris = fixtures.pricedStudent("Борис", maths)
        val group = fixtures.group(subjectId = maths, members = setOf(anya, boris))
        val ids = fixtures.lesson(maths, listOf(anya, boris), monday, groupId = group)

        repo.setHomework(session(ids.first()), "  Задачи 31–35  ")

        assertEquals(listOf("Задачи 31–35", "Задачи 31–35"), ids.map { repo.getById(it)!!.homework })
    }

    /** The event carries the homework, so setting it is a change to what the calendar shows. */
    @Test
    fun `setting homework rewrites the calendar event`() = runTest {
        fixtures.withCalendarSync()
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val id = fixtures.lesson(maths, listOf(anya), nextMonday).single()
        env.calendar.calls.clear()

        repo.setHomework(session(id), "§12")

        assertEquals("§12", env.calendar.upserts.single().homework)
    }

    /**
     * The sheet hands over the lesson as it last drew it. Written back whole, that would undo whatever
     * happened since — here, the lesson being marked taught — so the write re-reads it first.
     */
    @Test
    fun `setting homework from a stale session keeps what changed since`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val id = fixtures.lesson(maths, listOf(anya), monday).single()
        val stale = session(id)
        fixtures.setStatus(id, LessonStatus.COMPLETED)

        repo.setHomework(stale, "§12")

        val klass = repo.getById(id)!!
        assertEquals(LessonStatus.COMPLETED, klass.status)
        assertEquals("§12", klass.homework)
    }

    /** Correcting a typo in the assignment is not un-checking it. */
    @Test
    fun `setting homework leaves the verdicts alone`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val id = fixtures.lesson(maths, listOf(anya), monday).single()
        repo.setHomework(session(id), "§12 упр 4")
        repo.setHomeworkResult(repo.getById(id)!!, HomeworkResult.DONE)

        repo.setHomework(session(id), "§12, упр. 4–7")

        assertEquals(HomeworkResult.DONE, repo.getById(id)!!.homeworkResult)
    }

    /** The lesson's form writes it through the ordinary edit, beside the note. */
    @Test
    fun `an edit of the lesson writes its homework`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val id = fixtures.lesson(maths, listOf(anya), monday).single()

        repo.apply(
            session(id),
            SessionEdit(
                subjectId = maths, durationMinutes = 60, mode = ClassMode.IN_PERSON, price = Money.ofMajor(30),
                notes = "Дроби", homework = "§12", studentId = anya,
            ),
            newStart = monday,
            now = monday.plusSeconds(7200),
        )

        val klass = repo.getById(id)!!
        assertEquals("Дроби", klass.notes)
        assertEquals("§12", klass.homework)
    }

    @Test
    fun `a verdict is one student's, in a group's lesson`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val boris = fixtures.pricedStudent("Борис", maths)
        val group = fixtures.group(subjectId = maths, members = setOf(anya, boris))
        val (anyaClass, borisClass) = fixtures.lesson(maths, listOf(anya, boris), monday, groupId = group)

        repo.setHomeworkResult(repo.getById(anyaClass)!!, HomeworkResult.NOT_DONE)

        assertEquals(HomeworkResult.NOT_DONE, repo.getById(anyaClass)!!.homeworkResult)
        assertNull(repo.getById(borisClass)!!.homeworkResult)
    }

    /**
     * Nothing a verdict changes is on the calendar event, and the reminder reads what is left to check
     * when it fires — so a verdict mirrors nothing and re-plans nothing.
     */
    @Test
    fun `a verdict owes the calendar and the reminders nothing`() = runTest {
        fixtures.withCalendarSync()
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val id = fixtures.lesson(maths, listOf(anya), nextMonday).single()
        env.calendar.calls.clear()
        val refreshes = env.reminders.refreshCount

        repo.setHomeworkResult(repo.getById(id)!!, HomeworkResult.DONE)

        assertTrue(env.calendar.upserts.isEmpty())
        assertEquals(refreshes, env.reminders.refreshCount)
    }

    /** A verdict tapped from a list an emission behind must not write that list's copy of the row back. */
    @Test
    fun `a verdict from a stale copy of the class touches nothing else on its row`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val id = fixtures.lesson(maths, listOf(anya), monday).single()
        val stale = repo.getById(id)!!
        fixtures.setStatus(id, LessonStatus.COMPLETED)
        val billed = repo.getById(id)!!.charge

        repo.setHomeworkResult(stale, HomeworkResult.PARTIAL)

        val klass = repo.getById(id)!!
        assertEquals("the charge the completion billed is still there", billed, klass.charge)
        assertEquals(HomeworkResult.PARTIAL, klass.homeworkResult)
    }

    @Test
    fun `a verdict can be cleared back to not checked`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val id = fixtures.lesson(maths, listOf(anya), monday).single()
        repo.setHomeworkResult(repo.getById(id)!!, HomeworkResult.DONE)

        repo.setHomeworkResult(repo.getById(id)!!, null)

        assertNull(repo.getById(id)!!.homeworkResult)
    }

    @Test
    fun `a lesson finds the homework set at the one before it`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val set = fixtures.lesson(maths, listOf(anya), monday).single()
        val next = fixtures.lesson(maths, listOf(anya), nextMonday).single()
        repo.setHomework(session(set), "§12")

        assertEquals("§12", repo.homeworkDueAt(session(next))?.text)
    }

    /** What the sheet watches: the homework the lesson checks, and the homework it set with its due day. */
    @Test
    fun `a lesson's sheet sees what it checks and what it set`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val before = fixtures.lesson(maths, listOf(anya), monday).single()
        val lesson = fixtures.lesson(maths, listOf(anya), nextMonday).single()
        val after = fixtures.lesson(maths, listOf(anya), nextMonday.plusSeconds(7 * 86_400)).single()
        repo.setHomework(session(before), "§11")
        repo.setHomework(session(lesson), "§12")

        val at = repo.observeHomeworkAt(session(lesson)).first()

        assertEquals("§11", at.checks?.text)
        assertEquals("§12", at.sets?.text)
        assertEquals(repo.getById(after)!!.lessonId, at.sets?.due?.lessonId)
    }
}
