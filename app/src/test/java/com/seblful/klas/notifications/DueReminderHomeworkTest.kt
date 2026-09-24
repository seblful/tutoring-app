package com.seblful.klas.notifications

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.testutil.RecordingNotifier
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * The reminder's second line: the current homework attached to the lesson. Read as the reminder fires,
 * which is why setting homework or ticking it done does not have to re-plan anything.
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
        homework = env.homeworkRepo,
        notifier = notifier,
        settings = env.settingsRepo,
        clock = env.clock,
    )

    // The homework is given at the environment's clock, 2026-06-01; the lesson is the next one after it.
    private val now: Instant = Instant.parse("2026-07-25T10:00:00Z")
    private val soon: Instant = now.plusSeconds(1800)

    @Test
    fun `the reminder says what homework the lesson is for`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val lesson = fixtures.lesson(maths, listOf(anya), soon).single()
        fixtures.homework(Assignee.Student(anya), text = "§12, упр. 4–7")

        due.post(lesson, now)

        assertEquals("§12, упр. 4–7", notifier.posted.single().homeworkToCheck)
    }

    /** Done already — sent in early, say — so there is nothing for the lesson to bring. */
    @Test
    fun `homework already done is not mentioned`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val lesson = fixtures.lesson(maths, listOf(anya), soon).single()
        fixtures.homework(Assignee.Student(anya), done = true)

        due.post(lesson, now)

        assertNull(notifier.posted.single().homeworkToCheck)
    }

    /** Homework whose lesson was an earlier one is that lesson's, not this one's. */
    @Test
    fun `homework attached to an earlier lesson is not this one's`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        fixtures.lesson(maths, listOf(anya), Instant.parse("2026-07-18T10:30:00Z"))
        val lesson = fixtures.lesson(maths, listOf(anya), soon).single()
        fixtures.homework(Assignee.Student(anya))

        due.post(lesson, now)

        assertNull(notifier.posted.single().homeworkToCheck)
    }

    @Test
    fun `a group lesson's reminder names the group's homework and a member's own`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val boris = fixtures.pricedStudent("Борис", maths)
        val group = fixtures.group(subjectId = maths, members = setOf(anya, boris))
        val lesson = fixtures.lesson(maths, listOf(anya, boris), soon, groupId = group).first()
        fixtures.homework(Assignee.Group(group), text = "Задачи 31–35")
        fixtures.homework(Assignee.Student(boris), text = "Доделать 30", at = Instant.parse("2026-06-02T12:00:00Z"))

        due.post(lesson, now)

        assertEquals("Задачи 31–35; Доделать 30", notifier.posted.single().homeworkToCheck)
    }

    @Test
    fun `a lesson with no homework is announced as before`() = runTest {
        val maths = fixtures.subject()
        val anya = fixtures.pricedStudent("Аня", maths)
        val lesson = fixtures.lesson(maths, listOf(anya), soon).single()

        due.post(lesson, now)

        assertNull(notifier.posted.single().homeworkToCheck)
    }
}
