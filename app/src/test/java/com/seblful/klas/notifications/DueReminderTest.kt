package com.seblful.klas.notifications

import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.testutil.RecordingNotifier
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * What a reminder does when it comes due. All of this used to sit inside `ReminderWorker`, where it
 * could only be reached from a device — so none of it was tested at any level.
 */
class DueReminderTest {

    private val env = TestEnv()
    private val notifier = RecordingNotifier()

    private val due = DueReminder(
        classes = env.classRepo,
        students = env.studentRepo,
        subjects = env.subjectRepo,
        groups = env.groupRepo,
        notifier = notifier,
        settings = env.settingsRepo,
    )

    private val now: Instant = Instant.parse("2026-07-25T10:00:00Z")

    /** A lesson starting half an hour from [now], so it is still ahead of us. */
    private val soon = now.plusSeconds(1800)

    private suspend fun student(name: String = "Аня") = env.studentDao.upsert(StudentEntity(name = name))
    private suspend fun subject(name: String = "Математика") = env.subjectDao.upsert(SubjectEntity(name = name))

    private suspend fun scheduledClass(
        studentId: Long,
        subjectId: Long,
        start: Instant = soon,
        status: String = "SCHEDULED",
        groupId: Long? = null,
        durationMinutes: Int = 60,
    ) = env.classDao.upsert(
        ClassEntity(
            studentId = studentId,
            subjectId = subjectId,
            startMillis = start.toEpochMilli(),
            durationMinutes = durationMinutes,
            status = status,
            groupId = groupId,
        ),
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
        val id = scheduledClass(student(), subject(), status = "CANCELLED")

        assertFalse(due.post(id, now))
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `a completed class is not announced`() = runTest {
        val id = scheduledClass(student(), subject(), status = "COMPLETED")

        assertFalse(due.post(id, now))
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `a lesson that has already ended is not announced — the reminder fired too late`() = runTest {
        // Started two hours ago and ran for one, so it is over.
        val id = scheduledClass(student(), subject(), start = now.minusSeconds(7200))

        assertFalse(due.post(id, now))
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `a lesson still running is announced — it is not over until it ends`() = runTest {
        // Started ten minutes ago, runs for an hour.
        val id = scheduledClass(student(), subject(), start = now.minusSeconds(600))

        assertTrue(due.post(id, now))
        assertEquals(1, notifier.posted.size)
    }

    @Test
    fun `a class deleted since the reminder was planned posts nothing`() = runTest {
        assertFalse(due.post(classId = 404, now = now))
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `an unknown student still gets a reminder, with time and subject only`() = runTest {
        val subj = subject("Физика")
        // A class whose student row is missing — the reminder is still worth posting.
        val id = scheduledClass(studentId = 999, subjectId = subj)

        assertTrue(due.post(id, now))

        val posted = notifier.posted.single()
        assertEquals("", posted.whoName)
        assertEquals("Физика", posted.subjectName)
    }
}
