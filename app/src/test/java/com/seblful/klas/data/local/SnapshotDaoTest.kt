package com.seblful.klas.data.local

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * What `replace` means, against the fakes — because the word is the whole guarantee a restore rests on.
 *
 * The instrumented round-trip proves an import into an **empty** database reproduces the file. That is
 * the easy direction, and it is silent about the one that can go wrong: restoring onto a phone that
 * already has data. If `replace` only inserted, the tutor would be left holding two databases at once —
 * every subject twice, or a primary-key collision, depending on the ids in the file. Nothing in the
 * suite noticed the wipe missing, so this is here to.
 */
class SnapshotDaoTest {

    private val env = TestEnv()

    private suspend fun seedALesson(
        subject: String,
        student: String,
        start: Instant = Instant.parse("2026-05-04T10:00:00Z"),
    ) {
        val subjectId = env.fixtures.subject(subject)
        val studentId = env.fixtures.pricedStudent(student, subjectId)
        env.fixtures.completedLesson(
            subjectId = subjectId,
            students = listOf(studentId),
            start = start,
            rate = Money.ofMajor(30),
        )
    }

    @Test
    fun `replace throws away what was there rather than merging into it`() = runTest {
        seedALesson("Алгебра", "Аня")
        val taken = env.snapshotDao.read()
        // A different database entirely: same shape, none of the same rows. Its lesson takes its
        // own hour — the repository seeding goes through holds one lesson at a time.
        env.subjectDao.items.value = emptyList()
        env.studentDao.items.value = emptyList()
        seedALesson("Физика", "Борис", start = Instant.parse("2026-05-04T13:00:00Z"))

        env.snapshotDao.replace(taken)

        val after = env.snapshotDao.read()
        assertEquals(listOf("Алгебра"), after.subjects.map { it.name })
        assertEquals(listOf("Аня"), after.students.map { it.name })
        assertEquals("one lesson, not two", 1, after.lessons.size)
        assertEquals(1, after.attendance.size)
    }

    /** And a table the snapshot has nothing for comes back empty, not left as it was. */
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

    /** [SnapshotDao.read] reports every table, so nothing is quietly left out of a backup. */
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
