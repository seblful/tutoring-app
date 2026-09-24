package com.seblful.klas.notifications

import com.seblful.klas.domain.model.HomeworkResult
import com.seblful.klas.testutil.RecordingNotifier
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * The reminder's second line: the homework this lesson checks, while it is still unmarked. Read as the
 * reminder fires, which is why a verdict does not have to re-plan it.
 */
class DueReminderHomeworkTest {

    private val env = TestEnv()
    private val notifier = RecordingNotifier()
    private val fixtures = env.fixtures

    private val due = DueReminder(
        classes = env.classRepo,
        students = env.studentRepo,
        subjects = env.subjectRepo,
        groups = env.groupRepo,
        notifier = notifier,
        settings = env.settingsRepo,
        clock = env.clock,
    )

    private val lastWeek: Instant = Instant.parse("2026-07-18T10:30:00Z")
    private val now: Instant = Instant.parse("2026-07-25T10:00:00Z")
    private val soon: Instant = now.plusSeconds(1800)

    @Test
    fun `the reminder says what to check`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val set = fixtures.lesson(maths, listOf(anya), lastWeek).single()
        val lesson = fixtures.lesson(maths, listOf(anya), soon).single()
        env.classRepo.setHomework(env.classRepo.getSessionFor(set)!!, "§12, упр. 4–7")

        due.post(lesson, now)

        assertEquals("§12, упр. 4–7", notifier.posted.single().homeworkToCheck)
    }

    /** Marked already — sent in early, say — so there is nothing left for the lesson to check. */
    @Test
    fun `homework already marked is not mentioned`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val set = fixtures.lesson(maths, listOf(anya), lastWeek).single()
        val lesson = fixtures.lesson(maths, listOf(anya), soon).single()
        env.classRepo.setHomework(env.classRepo.getSessionFor(set)!!, "§12")
        env.classRepo.setHomeworkResult(env.classRepo.getById(set)!!, HomeworkResult.DONE)

        due.post(lesson, now)

        assertNull(notifier.posted.single().homeworkToCheck)
    }

    @Test
    fun `a lesson with nothing to check is announced as before`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val lesson = fixtures.lesson(maths, listOf(anya), soon).single()

        due.post(lesson, now)

        assertNull(notifier.posted.single().homeworkToCheck)
    }
}
