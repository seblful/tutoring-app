package com.seblful.klas.notifications

import com.seblful.klas.data.people.GroupEntity
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.domain.model.LessonStatus
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class DueReminderTest {

    @get:Rule
    val env = TestEnv()
    private val notifier = RecordingNotifier()

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

    private val now: Instant = Instant.parse("2026-07-25T10:00:00Z")

    private val soon = now.plus(1800.seconds)

    private suspend fun student(name: String = "Аня") = env.studentDao.upsert(StudentEntity(name = name))
    private suspend fun subject(name: String = "Математика") = env.subjectDao.upsert(SubjectEntity(name = name))

    private suspend fun scheduledClass(
        studentId: Long,
        subjectId: Long,
        start: Instant = soon,
        status: LessonStatus = LessonStatus.SCHEDULED,
        groupId: Long? = null,
        durationMinutes: Int = 60,
    ) = env.fixtures.storedClass(
        studentId = studentId,
        subjectId = subjectId,
        startMillis = start.toEpochMilliseconds(),
        durationMinutes = durationMinutes,
        status = status,
        groupId = groupId,
    )

    @Test
    fun `an upcoming class is announced by the student, subject and time`() = runTest {
        val s = student("Аня")
        val subj = subject("Математика")
        val id = scheduledClass(s, subj)

        assertTrue(due.post(id, now))

        val posted = notifier.posted.single()
        assertEquals(id, posted.klass.id)
        assertEquals("Аня", posted.whoName)
        assertEquals("Математика", posted.subjectName)
    }

    @Test
    fun `a group lesson is announced by the group, not by whichever member it points at`() = runTest {
        val s = student("Аня")
        val subj = subject("Математика")
        val group = env.groupDao.upsert(GroupEntity(name = "11 «А»"))
        val id = scheduledClass(s, subj, groupId = group)

        assertTrue(due.post(id, now))

        assertEquals("11 «А»", notifier.posted.single().whoName)
    }

    @Test
    fun `nothing is posted while reminders are switched off`() = runTest {
        val id = scheduledClass(student(), subject())
        env.settingsRepo.update { it.copy(notificationsEnabled = false) }

        assertFalse(due.post(id, now))
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `a cancelled class is not announced`() = runTest {
        val id = scheduledClass(student(), subject(), status = LessonStatus.CANCELLED)

        assertFalse(due.post(id, now))
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `a completed class is not announced`() = runTest {
        val id = scheduledClass(student(), subject(), status = LessonStatus.COMPLETED)

        assertFalse(due.post(id, now))
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `a lesson that has already ended is not announced — the reminder fired too late`() = runTest {
        val id = scheduledClass(student(), subject(), start = now.minus(7200.seconds))

        assertFalse(due.post(id, now))
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `a lesson still running is announced — it is not over until it ends`() = runTest {
        val id = scheduledClass(student(), subject(), start = now.minus(600.seconds))

        assertTrue(due.post(id, now))
        assertEquals(1, notifier.posted.size)
    }

    @Test
    fun `a class deleted since the reminder was planned posts nothing`() = runTest {
        assertFalse(due.post(classId = 404, now = now))
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `a class cannot name a student who does not exist, so a reminder always has someone to name`() = runTest {
        val subj = subject("Физика")

        val refused = runCatching { scheduledClass(studentId = 999, subjectId = subj) }.exceptionOrNull()

        assertTrue("refused by the foreign key: $refused", refused?.message.orEmpty().contains("FOREIGN KEY"))
        assertTrue("no class to remind about", env.classes().isEmpty())
    }
}
