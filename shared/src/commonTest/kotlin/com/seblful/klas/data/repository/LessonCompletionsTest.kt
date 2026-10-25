package com.seblful.klas.data.repository

import com.seblful.klas.domain.lesson.CompletionTiming
import com.seblful.klas.domain.lesson.SessionEdit
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.store
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class LessonCompletionsTest {
    private val env = TestEnv()
    private val start = Instant.parse("2026-05-01T10:00:00Z")

    @AfterTest
    fun closeEnvironment() = env.close()

    private suspend fun completeGroup(): ClassSession {
        val subject = env.fixtures.subject()
        val members = (1..3).map { env.fixtures.pricedStudent("Student $it", subject) }
        val group = env.fixtures.group(subjectId = subject, members = members.toSet())
        val ids = env.fixtures.lesson(subject, members, start, groupId = group)
        env.classRepo.setAttendance(env.classRepo.getById(ids[1])!!, Attended.ABSENT)
        env.classRepo.setAttendance(env.classRepo.getById(ids[2])!!, Attended.ABSENT_BILLED)
        return env.store().lessonCompletions.complete(ids.first())!!
    }

    @Test
    fun `undo removes charges and preserves absences and the whole booked roster`() = runTest {
        val completed = completeGroup()
        assertTrue(completed.classes.any { it.charge > Money.ZERO })

        assertTrue(env.lessonCompletions.undo(completed))

        val corrected = env.classRepo.getSessionFor(completed.first.id)!!
        assertEquals(completed.ids, corrected.ids)
        assertEquals(LessonStatus.SCHEDULED, corrected.status)
        assertEquals(
            listOf(Attended.EXPECTED, Attended.ABSENT, Attended.ABSENT_BILLED),
            corrected.classes.map {
                it.attended
            },
        )
        assertTrue(corrected.classes.all { it.charge == Money.ZERO })
        assertTrue(env.stored().payments.isEmpty())
    }

    @Test
    fun `an old undo receipt cannot overwrite a later rate or mode correction`() = runTest {
        val receipt = completeGroup()
        env.classRepo.apply(
            receipt, SessionEdit(receipt.first.subjectId, 90, ClassMode.REMOTE, Money.ofMajor(40)),
            start, now = env.clock.now(),
        ).written()
        val before = env.stored()

        assertFalse(env.lessonCompletions.undo(receipt))
        assertEquals(before, env.stored())
        // The later explicit action works on current facts without restoring the receipt's old rate.
        assertTrue(env.lessonCompletions.correct(receipt))
        val corrected = env.classRepo.getSessionFor(receipt.first.id)!!
        assertTrue(corrected.classes.all { it.price == Money.ofMajor(40) && it.mode == ClassMode.REMOTE })
    }

    @Test
    fun `undo cannot revive a subsequently cancelled or deleted lesson`() = runTest {
        val receipt = completeGroup()
        env.classRepo.setStatus(receipt, LessonStatus.CANCELLED).written()
        assertFalse(env.lessonCompletions.undo(receipt))
        assertEquals(LessonStatus.CANCELLED, env.classRepo.getSessionFor(receipt.first.id)!!.status)
        env.classRepo.delete(receipt)
        assertFalse(env.lessonCompletions.undo(receipt))
        assertTrue(env.classRepo.getAll().isEmpty())
    }

    @Test
    fun `a batch skips ineligible and missing lessons and completes each group once`() = runTest {
        val subject = env.fixtures.subject()
        val members = (1..2).map { env.fixtures.pricedStudent("Student $it", subject) }
        val group = env.fixtures.group(subjectId = subject, members = members.toSet())
        val ids = env.fixtures.lesson(subject, members, start, groupId = group)
        val cancelled = env.fixtures.lesson(subject, listOf(members.first()), start + 1.hours).single()
        val taught = env.fixtures.lesson(subject, listOf(members.first()), start + 2.hours).single()
        val gone = env.fixtures.lesson(subject, listOf(members.first()), start + 3.hours).single()
        env.fixtures.setStatus(cancelled, LessonStatus.CANCELLED)
        env.fixtures.setStatus(taught, LessonStatus.COMPLETED)
        env.classRepo.delete(env.classRepo.getSessionFor(gone)!!)
        val transactions = env.transactor.count
        val refreshes = env.reminders.refreshCount

        val completed = env.lessonCompletions.complete(ids + ids + listOf(cancelled, taught, gone))

        assertEquals(listOf(ids.toSet()), completed.map { it.ids })
        assertTrue(completed.single().classes.all { it.status == LessonStatus.COMPLETED && it.charge > Money.ZERO })
        assertEquals(transactions + 1, env.transactor.count)
        assertEquals(refreshes + 1, env.reminders.refreshCount)
        assertEquals(LessonStatus.CANCELLED, env.classRepo.getById(cancelled)!!.status)
        assertTrue(env.lessonCompletions.complete(taught) == null)
    }

    @Test
    fun `a failed batch commits no completion or mirror work`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val ids = (0..1).map { env.fixtures.lesson(subject, listOf(student), start + it.hours).single() }
        val before = env.stored()
        val refreshes = env.reminders.refreshCount
        env.transactor.failAt = env.transactor.count + 1

        assertFailsWith<IllegalStateException> { env.lessonCompletions.complete(ids) }

        assertEquals(before, env.stored())
        assertEquals(refreshes, env.reminders.refreshCount)
    }

    @Test
    fun `timing keeps early marks explicit and includes exact start and end boundaries`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val now = env.clock.now()
        val future = env.fixtures.lesson(subject, listOf(student), now + 1.hours).single()
        val starting = env.fixtures.lesson(subject, listOf(student), now).single()
        val ending = env.fixtures.lesson(subject, listOf(student), now - 1.hours).single()

        assertEquals(
            listOf(ending),
            env.lessonCompletions.complete(listOf(starting, ending), CompletionTiming.FINISHED)
                .map { it.first.id },
        )
        assertTrue(env.lessonCompletions.complete(listOf(future), CompletionTiming.STARTED).isEmpty())
        assertEquals(
            starting,
            env.lessonCompletions.complete(listOf(starting), CompletionTiming.STARTED).single().first.id,
        )
        assertEquals(future, env.lessonCompletions.complete(future)!!.first.id)
    }

    @Test
    fun `a batch undo leaves only the lesson changed since completion`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val ids = (0..1).map { env.fixtures.lesson(subject, listOf(student), start + it.hours).single() }
        val marked = env.lessonCompletions.complete(ids)
        env.classRepo.setAttendance(marked.last().first, Attended.ABSENT)

        assertEquals(1, env.lessonCompletions.undoAll(marked))
        assertEquals(LessonStatus.SCHEDULED, env.classRepo.getById(ids.first())!!.status)
        assertEquals(LessonStatus.COMPLETED, env.classRepo.getById(ids.last())!!.status)
        assertEquals(Attended.ABSENT, env.classRepo.getById(ids.last())!!.attended)
    }

    @Test
    fun `a lesson moved into the future before commit is no longer eligible on Today`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val now = env.clock.now()
        val id = env.fixtures.lesson(subject, listOf(student), now - 1.hours).single()
        val gate = env.transactor.pauseNextTransaction()
        val pending = async { env.lessonCompletions.complete(listOf(id), CompletionTiming.STARTED) }
        gate.awaitEntry()
        try {
            env.classRepo.move(env.classRepo.getSessionFor(id)!!, now + 1.hours, now).written()
        } finally {
            gate.release()
        }

        assertTrue(pending.await().isEmpty())
        assertEquals(LessonStatus.SCHEDULED, env.classRepo.getById(id)!!.status)
    }

    @Test
    fun `completion mirrors immediately without an undo settlement timer`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val id = env.fixtures.lesson(subject, listOf(student), env.clock.now() + 1.hours).single()

        val marked = env.lessonCompletions.complete(id)!!

        assertEquals(LessonStatus.COMPLETED, env.calendar.refreshes.last().klass.status)
        assertTrue(env.lessonCompletions.undo(marked))
        assertEquals(LessonStatus.SCHEDULED, env.calendar.upserts.last().status)
        assertTrue(env.calendar.deletes.isEmpty())
    }
}
