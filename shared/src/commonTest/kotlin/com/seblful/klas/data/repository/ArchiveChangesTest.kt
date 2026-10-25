package com.seblful.klas.data.repository

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.lessons.UndoWindow
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveChangesTest {
    private val env = TestEnv()
    private val start = Instant.parse("2026-06-20T10:00:00Z")

    @AfterTest
    fun closeEnvironment() = env.close()

    private fun TestScope.writer() = env.lessonChanges.inScope(this) { throw it }

    private suspend fun status(id: Long) = env.classRepo.getById(id)!!.status

    /** Ann with two lessons of her own and a place in a group lesson, all mirrored to the calendar. */
    private inner class Seeded {
        lateinit var own: List<Long>
        lateinit var groupLesson: List<Long>
        var ann = 0L
        var group = 0L

        suspend fun seed(): Seeded {
            env.settingsRepo.update { it.copy(calendarSyncEnabled = true) }
            val subject = env.fixtures.subject()
            ann = env.fixtures.pricedStudent("Ann", subject)
            val bob = env.fixtures.pricedStudent("Bob", subject)
            group = env.fixtures.group(subjectId = subject, members = setOf(ann, bob))
            own = listOf(start, start + 1.days).map { env.fixtures.lesson(subject, listOf(ann), it).single() }
            groupLesson = env.fixtures.lesson(subject, listOf(ann, bob), start + 2.days, groupId = group)
            return this
        }
    }

    @Test
    fun `taking an archive back re-plans the student's lessons and group place without touching the calendar`() =
        runTest {
            val changes = writer()
            val s = Seeded().seed()
            val before = env.stored()

            val cancelled = changes.archive(env.studentRepo.getById(s.ann)!!)
            assertEquals(3, cancelled.cancelledLessons, "two own lessons and one group lesson")
            assertTrue(s.own.all { status(it) == LessonStatus.CANCELLED })
            assertEquals(Attended.ABSENT, env.classRepo.getById(s.groupLesson.first())!!.attended)

            assertEquals(0, changes.undoArchive(cancelled))

            assertFalse(env.studentRepo.getById(s.ann)!!.archived)
            assertEquals(before, env.stored())
            assertTrue(env.calendar.deletes.isEmpty(), "nobody's invitation was withdrawn")
        }

    @Test
    fun `a lesson whose hour was booked since stays cancelled and is counted`() = runTest {
        val changes = writer()
        val s = Seeded().seed()
        val cancelled = changes.archive(env.studentRepo.getById(s.ann)!!)
        val subject = env.classRepo.getById(s.own.first())!!.subjectId
        env.fixtures.lesson(subject, listOf(env.fixtures.pricedStudent("Cleo", subject)), start)

        assertEquals(1, changes.undoArchive(cancelled))

        assertEquals(LessonStatus.CANCELLED, status(s.own[0]))
        assertEquals(LessonStatus.SCHEDULED, status(s.own[1]))
        assertEquals(1, env.calendar.deletes.size, "the refused one's event is withdrawn now")
    }

    @Test
    fun `a settled archive withdraws the cancelled lessons' events`() = runTest {
        val changes = writer()
        val s = Seeded().seed()
        val events = s.own.map { env.classRepo.getById(it)!!.calendarEventId }

        changes.archive(env.studentRepo.getById(s.ann)!!)
        assertTrue(env.calendar.deletes.isEmpty())
        advanceTimeBy(UndoWindow)
        runCurrent()

        assertTrue(env.calendar.deletes.containsAll(events.filterNotNull()))
    }

    @Test
    fun `taking a group's archive back re-plans its lessons`() = runTest {
        val changes = writer()
        val s = Seeded().seed()
        val before = env.stored()

        val cancelled = changes.archive(env.groupRepo.getById(s.group)!!)
        assertTrue(s.groupLesson.all { status(it) == LessonStatus.CANCELLED })

        assertEquals(0, changes.undoArchive(cancelled))
        assertEquals(before, env.stored())
    }
}
