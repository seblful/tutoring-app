package com.seblful.klas.data.local

import com.seblful.klas.data.billing.PriceEntity
import com.seblful.klas.data.billing.RateChangeEntity
import com.seblful.klas.data.lessons.AttendanceEntity
import com.seblful.klas.data.lessons.LessonEntity
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class SnapshotDaoTest {

    @get:Rule
    val env = TestEnv()

    private suspend fun seedALesson(subject: String, student: String, into: TestEnv = env) {
        val subjectId = into.fixtures.subject(subject)
        val studentId = into.fixtures.pricedStudent(student, subjectId)
        into.fixtures.completedLesson(
            subjectId = subjectId,
            students = listOf(studentId),
            start = Instant.parse("2026-05-04T10:00:00Z"),
            rate = Money.ofMajor(30),
        )
    }

    @Test
    fun `replace throws away what was there rather than merging into it`() = runTest {
        val taken = TestEnv().use { other ->
            seedALesson("Алгебра", "Аня", into = other)
            other.snapshotDao.read()
        }
        seedALesson("Физика", "Борис")

        env.snapshotDao.replace(taken)

        val after = env.snapshotDao.read()
        assertEquals(listOf("Алгебра"), after.subjects.map { it.name })
        assertEquals(listOf("Аня"), after.students.map { it.name })
        assertEquals("one lesson, not two", 1, after.lessons.size)
        assertEquals(1, after.attendance.size)
    }

    @Test
    fun `replace empties a table the snapshot has no rows for`() = runTest {
        seedALesson("Алгебра", "Аня")

        env.snapshotDao.replace(DatabaseSnapshot())

        val after = env.snapshotDao.read()
        assertEquals(emptyList<SubjectEntity>(), after.subjects)
        assertEquals(emptyList<StudentEntity>(), after.students)
        assertEquals(emptyList<LessonEntity>(), after.lessons)
        assertEquals(emptyList<AttendanceEntity>(), after.attendance)
        assertEquals(emptyList<PriceEntity>(), after.prices)
        assertEquals(emptyList<RateChangeEntity>(), after.rateHistory)
    }

    @Test
    fun `read carries every table the snapshot names`() = runTest {
        val subjectId = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subjectId)
        val group = env.fixtures.group("11 «А»", subjectId = subjectId, members = setOf(anya))
        env.fixtures.completedLesson(subjectId, listOf(anya), Instant.parse("2026-05-04T10:00:00Z"), groupId = group)
        env.paymentRepo.restore(
            Payment(studentId = anya, date = Instant.parse("2026-05-05T10:00:00Z"), amount = Money.ofMajor(10)),
        )

        val snapshot = env.snapshotDao.read()

        assertEquals(1, snapshot.subjects.size)
        assertEquals(1, snapshot.students.size)
        assertEquals("the enrollment link", 1, snapshot.studentSubjects.size)
        assertEquals(1, snapshot.groups.size)
        assertEquals("the roster", 1, snapshot.groupMembers.size)
        assertEquals(1, snapshot.prices.size)
        assertEquals("the student's rate, and the group's", 2, snapshot.rateHistory.size)
        assertEquals(1, snapshot.lessons.size)
        assertEquals("the student in it", 1, snapshot.attendance.size)
        assertEquals(1, snapshot.payments.size)
    }
}
