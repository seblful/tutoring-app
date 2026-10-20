package com.seblful.klas.data.repository

import com.seblful.klas.domain.lesson.SessionEdit
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class AttendanceCorrectionTest {
    private val env = TestEnv()
    private val start = Instant.parse("2026-05-01T10:00:00Z")

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    private suspend fun lesson(): List<Long> {
        val subject = env.fixtures.subject()
        val first = env.fixtures.pricedStudent("First", subject)
        val second = env.fixtures.pricedStudent("Second", subject)
        val group = env.fixtures.group(subjectId = subject, members = setOf(first, second))
        return env.fixtures.lesson(subject, listOf(first, second), start, groupId = group)
    }

    @Test
    fun `attendance correction uses current lesson facts and preserves every member`() = runTest {
        val ids = lesson()
        val stale = env.classRepo.getById(ids.last())!!
        env.fixtures.setStatus(ids.first(), LessonStatus.COMPLETED)
        env.classRepo.apply(
            env.classRepo.getSessionFor(ids.first())!!,
            SessionEdit(stale.subjectId, 90, ClassMode.REMOTE, Money.ofMajor(40)),
            start,
            now = env.clock.now(),
        ).written()
        val before = env.stored()

        env.classRepo.setAttendance(stale, Attended.ABSENT_BILLED)

        val after = env.stored()
        assertEquals(before.lessons, after.lessons)
        assertEquals(before.attendance.first(), after.attendance.first())
        assertEquals(before.attendance.size, after.attendance.size)
        val corrected = env.classRepo.getById(ids.last())!!
        assertEquals(Attended.ABSENT_BILLED, corrected.attended)
        assertEquals(Money.ofMajor(60), corrected.charge)
        assertEquals(ClassMode.REMOTE, corrected.mode)
    }

    @Test
    fun `a stale attendance correction cannot reactivate a cancelled lesson`() = runTest {
        val ids = lesson()
        env.fixtures.setStatus(ids.first(), LessonStatus.COMPLETED)
        val stale = env.classRepo.getById(ids.last())!!
        env.fixtures.setStatus(ids.first(), LessonStatus.CANCELLED)

        env.classRepo.setAttendance(stale, Attended.ABSENT_BILLED)

        val session = env.classRepo.getSessionFor(ids.first())!!
        assertEquals(LessonStatus.CANCELLED, session.status)
        assertTrue(session.classes.all { it.charge == Money.ZERO })
        assertEquals(2, session.size)
    }

    @Test
    fun `a deleted class cannot be restored by an attendance correction`() = runTest {
        val ids = lesson()
        val stale = env.classRepo.getById(ids.last())!!
        env.classRepo.delete(env.classRepo.getSessionFor(ids.first())!!)

        val updated = env.classRepo.setAttendance(stale, Attended.ABSENT)

        assertTrue(updated.isEmpty())
        assertTrue(env.classRepo.getAll().isEmpty())
    }
}
