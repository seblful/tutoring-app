package com.seblful.klas.notifications

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.RecordingNotifier
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class ReminderActionsTest {

    @get:Rule(order = 0)
    val env = TestEnv()
    private val notifier = RecordingNotifier()

    private val marks = RecentMarks()
    private val actions =
        ReminderActions(completions = env.lessonCompletions, notifier = notifier, marks = marks, clock = env.clock)

    private val start: Instant = Instant.parse("2026-07-25T10:00:00Z")

    private suspend fun lesson(students: Int = 1, asGroup: Boolean = false): List<Long> {
        val subject = env.subjectRepo.createOrReuse("Алгебра")
        val people = (1..students).map { env.fixtures.student(name = "Ученик $it") }
        return env.fixtures.lesson(
            subjectId = subject,
            students = people,
            start = start,
            groupId = if (asGroup) env.fixtures.group(subjectId = subject, members = people.toSet()) else null,
        )
    }

    private suspend fun statusOf(classId: Long) = env.classRepo.getById(classId)?.status

    private suspend fun chargeOf(classId: Long) = env.classRepo.getById(classId)?.charge

    @Test
    fun `«Проведено» marks the lesson taught and bills it`() = runTest {
        val id = lesson().single()

        assertTrue(actions.complete(id))

        assertEquals(LessonStatus.COMPLETED, statusOf(id))
        assertEquals(Money.ofMajor(30), chargeOf(id))
    }

    @Test
    fun `a lesson marked from outside the app is kept for Today's undo, and a refused one is not`() = runTest {
        // Cancelled first, which frees the hour for the other.
        val cancelled = lesson().single()
        env.fixtures.setStatus(cancelled, LessonStatus.CANCELLED)
        val taught = lesson().single()

        actions.complete(taught)
        actions.complete(cancelled)

        val kept = marks.marks.value.single()
        assertEquals(taught, kept.session.first.id)
        assertEquals(LessonStatus.COMPLETED, kept.session.status)
        assertEquals(env.clock.now(), kept.at)
    }

    @Test
    fun `a group lesson is settled as the whole room`() = runTest {
        val ids = lesson(students = 3, asGroup = true)

        assertTrue(actions.complete(ids.first()))

        assertEquals(3, ids.size)
        ids.forEach { id ->
            assertEquals(LessonStatus.COMPLETED, statusOf(id))
            assertEquals(Money.ofMajor(30), chargeOf(id))
        }
    }

    @Test
    fun `the reminder leaves the tray`() = runTest {
        val id = lesson().single()

        actions.complete(id)

        assertEquals(listOf(id), notifier.cancelled)
    }

    @Test
    fun `a lesson cancelled since the reminder arrived is not resurrected as a taught one`() = runTest {
        val subject = env.fixtures.subject()
        val id = env.fixtures.cancelledLesson(
            subjectId = subject,
            students = listOf(env.fixtures.student()),
            start = start,
        ).single()

        assertFalse(actions.complete(id))

        assertEquals(LessonStatus.CANCELLED, statusOf(id))
        assertEquals(Money.ZERO, chargeOf(id))
        assertEquals(listOf(id), notifier.cancelled)
    }

    @Test
    fun `a lesson already completed is left exactly as it is`() = runTest {
        val id = lesson().single()
        assertTrue(actions.complete(id))

        assertFalse(actions.complete(id))

        assertEquals(LessonStatus.COMPLETED, statusOf(id))
        assertEquals(Money.ofMajor(30), chargeOf(id))
    }

    @Test
    fun `a lesson deleted since the reminder arrived does nothing but clear it`() = runTest {
        assertFalse(actions.complete(classId = 404))

        assertEquals(listOf(404L), notifier.cancelled)
    }
}
