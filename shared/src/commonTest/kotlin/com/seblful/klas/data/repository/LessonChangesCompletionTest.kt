package com.seblful.klas.data.repository

import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.data.lessons.UndoResult
import com.seblful.klas.domain.lesson.CompletionTiming
import com.seblful.klas.domain.lesson.SessionEdit
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.lessons.LessonChange
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.store
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class LessonChangesCompletionTest {
    private val env = TestEnv()
    private val start = Instant.parse("2026-05-01T10:00:00Z")

    @AfterTest
    fun closeEnvironment() = env.close()

    private fun TestScope.writer() = env.lessonChanges.inScope(backgroundScope) { throw it }

    private suspend fun completeGroup(): LessonChange {
        val subject = env.fixtures.subject()
        val members = (1..3).map { env.fixtures.pricedStudent("Student $it", subject) }
        val group = env.fixtures.group(subjectId = subject, members = members.toSet())
        val ids = env.fixtures.lesson(subject, members, start, groupId = group)
        env.classRepo.setAttendance(env.classRepo.getById(ids[1])!!, Attended.ABSENT)
        env.classRepo.setAttendance(env.classRepo.getById(ids[2])!!, Attended.ABSENT_BILLED)
        return env.store().lessonChanges.complete(ids.first())!!
    }

    @Test
    fun `undo removes charges and preserves absences and the whole booked roster`() = runTest {
        val completed = completeGroup()
        assertTrue(completed.lesson.classes.any { it.charge > Money.ZERO })

        assertEquals(UndoResult.Restored, writer().undo(completed))

        val corrected = env.classRepo.getSessionFor(completed.lesson.first.id)!!
        assertEquals(completed.lesson.ids, corrected.ids)
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
        val lesson = receipt.lesson
        env.classRepo.apply(
            lesson, SessionEdit(lesson.first.subjectId, 90, ClassMode.REMOTE, Money.ofMajor(40)),
            start, now = env.clock.now(),
        ).written()
        val before = env.stored()

        assertEquals(UndoResult.ChangedSince, writer().undo(receipt))
        assertEquals(before, env.stored())
        // The later explicit action works on current facts without restoring the receipt's old rate.
        assertTrue(env.lessonChanges.correctCompletion(lesson))
        val corrected = env.classRepo.getSessionFor(lesson.first.id)!!
        assertTrue(corrected.classes.all { it.price == Money.ofMajor(40) && it.mode == ClassMode.REMOTE })
    }

    @Test
    fun `undo cannot revive a subsequently cancelled or deleted lesson`() = runTest {
        val receipt = completeGroup()
        env.classRepo.setStatus(receipt.lesson, LessonStatus.CANCELLED).written()
        assertEquals(UndoResult.ChangedSince, writer().undo(receipt))
        assertEquals(LessonStatus.CANCELLED, env.classRepo.getSessionFor(receipt.lesson.first.id)!!.status)
        env.classRepo.delete(receipt.lesson)
        assertEquals(UndoResult.Gone, writer().undo(receipt))
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

        val completed = env.lessonChanges.complete(ids + ids + listOf(cancelled, taught, gone))

        assertEquals(listOf(ids.toSet()), completed.map { it.lesson.ids })
        assertTrue(
            completed.single().lesson.classes.all { it.status == LessonStatus.COMPLETED && it.charge > Money.ZERO },
        )
        assertEquals(transactions + 1, env.transactor.count)
        assertEquals(refreshes + 1, env.reminders.refreshCount)
        assertEquals(LessonStatus.CANCELLED, env.classRepo.getById(cancelled)!!.status)
        assertNull(env.lessonChanges.complete(taught))
    }

    @Test
    fun `a failed batch commits no completion or mirror work`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val ids = (0..1).map { env.fixtures.lesson(subject, listOf(student), start + it.hours).single() }
        val before = env.stored()
        val refreshes = env.reminders.refreshCount
        env.transactor.failAt = env.transactor.count + 1

        assertFailsWith<IllegalStateException> { env.lessonChanges.complete(ids) }

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
            env.lessonChanges.complete(listOf(starting, ending), CompletionTiming.FINISHED)
                .map { it.lesson.first.id },
        )
        assertTrue(env.lessonChanges.complete(listOf(future), CompletionTiming.STARTED).isEmpty())
        assertEquals(
            starting,
            env.lessonChanges.complete(listOf(starting), CompletionTiming.STARTED).single().lesson.first.id,
        )
        assertEquals(future, env.lessonChanges.complete(future)!!.lesson.first.id)
    }

    @Test
    fun `a batch undo leaves only the lesson changed since completion`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val ids = (0..1).map { env.fixtures.lesson(subject, listOf(student), start + it.hours).single() }
        val marked = env.lessonChanges.complete(ids)
        env.classRepo.setAttendance(marked.last().lesson.first, Attended.ABSENT)

        assertEquals(listOf(UndoResult.Restored, UndoResult.ChangedSince), writer().undoAll(marked))
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
        val pending = async { env.lessonChanges.complete(listOf(id), CompletionTiming.STARTED) }
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

        val marked = env.lessonChanges.complete(id)!!

        assertEquals(LessonStatus.COMPLETED, env.calendar.refreshes.last().klass.status)
        assertEquals(UndoResult.Restored, writer().undo(marked))
        assertEquals(LessonStatus.SCHEDULED, env.calendar.upserts.last().status)
        assertTrue(env.calendar.deletes.isEmpty())
    }

    @Test
    fun `a writer's status change to completed is a completion, undone with the other changes`() = runTest {
        env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val taught = env.fixtures.lesson(subject, listOf(student), start).single()
        val cancelled = env.fixtures.lesson(subject, listOf(student), start + 1.hours).single()
        val writer = writer()

        val marked = writer.setStatus(env.classRepo.getSessionFor(taught)!!, LessonStatus.COMPLETED).written()!!
        // Mirrored at once, like every completion: no undo window holds its calendar event.
        assertEquals(LessonStatus.COMPLETED, env.calendar.refreshes.last().klass.status)
        val off = writer.setStatus(env.classRepo.getSessionFor(cancelled)!!, LessonStatus.CANCELLED).written()!!

        assertEquals(listOf(UndoResult.Restored, UndoResult.Restored), writer.undoAll(listOf(marked, off)))
        assertEquals(LessonStatus.SCHEDULED, env.classRepo.getById(taught)!!.status)
        assertEquals(LessonStatus.SCHEDULED, env.classRepo.getById(cancelled)!!.status)
    }

    @Test
    fun `a writer reports an ineligible completion as no change, and a deleted lesson as gone`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val id = env.fixtures.lesson(subject, listOf(student), start).single()
        val session = env.classRepo.getSessionFor(id)!!
        env.fixtures.setStatus(id, LessonStatus.CANCELLED)

        assertEquals(LessonWrite.Written(null), writer().setStatus(session, LessonStatus.COMPLETED))
        env.classRepo.delete(session)
        assertEquals(LessonWrite.Gone, writer().setStatus(session, LessonStatus.COMPLETED))
    }
}
