package com.seblful.klas.queries.students

import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.store
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StudentDetailsQueryTest {
    private val env = TestEnv()
    private val query = env.store().studentDetails

    @AfterTest
    fun close() = env.close()

    @Test
    fun `the last payment is the latest one and there is none before the first`() {
        val early = Payment(studentId = 1, date = Instant.fromEpochMilliseconds(10), amount = Money.ofMajor(50))
        val late = Payment(studentId = 1, date = Instant.fromEpochMilliseconds(20), amount = Money.ofMajor(10))

        assertNull(StudentDetails().lastPaymentAt)
        assertEquals(late.date, StudentDetails(payments = listOf(late, early)).lastPaymentAt)
    }

    @Test
    fun `balance is completed charges minus payments`() = runTest {
        val sid = env.fixtures.student()
        val subj = env.fixtures.subject(name = "Математика")
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60,
            status = LessonStatus.COMPLETED, chargeMinor = Money.ofMajor(30).minor,
        )
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = 2, durationMinutes = 90,
            status = LessonStatus.COMPLETED, chargeMinor = Money.ofMajor(45).minor,
        )
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = 3, durationMinutes = 60,
            status = LessonStatus.CANCELLED, chargeMinor = 0,
        )
        env.paymentRepo.restore(
            Payment(studentId = sid, date = Instant.fromEpochMilliseconds(10), amount = Money.ofMajor(50)),
        )

        assertEquals(Balance.Owed(Money.ofMajor(25)), query.observe(sid).first().balance)
    }

    @Test
    fun `subjects shown are exactly the priced ones sorted by name`() = runTest {
        val sid = env.fixtures.student()
        val math = env.fixtures.subject(name = "Математика")
        val algebra = env.fixtures.subject(name = "Алгебра")
        env.fixtures.subject(name = "Физика") // exists but unpriced for this student
        env.enrollmentRepo.enroll(sid, math, Money.ofMajor(30))
        env.enrollmentRepo.enroll(sid, algebra, Money.ofMajor(20))

        assertEquals(listOf("Алгебра", "Математика"), query.observe(sid).first().subjects.map { it.subject.name })
    }

    @Test
    fun `the stat row counts completed lessons their hours and their income`() = runTest {
        val sid = env.fixtures.student()
        val subj = env.fixtures.subject(name = "Математика")
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60,
            status = LessonStatus.COMPLETED, chargeMinor = Money.ofMajor(30).minor,
        )
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = 2, durationMinutes = 90,
            status = LessonStatus.COMPLETED, chargeMinor = Money.ofMajor(45).minor,
        )
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = 3, durationMinutes = 60,
            status = LessonStatus.SCHEDULED, chargeMinor = 0,
        )

        val totals = query.observe(sid).first().totals
        assertEquals(2, totals.lessons)
        assertEquals(1, totals.upcoming)
        assertEquals(150, totals.minutes, "a scheduled lesson adds no hours yet")
        assertEquals(Money.ofMajor(75), totals.earned)
    }

    @Test
    fun `a lesson says whether it was individual or which group it was with`() = runTest {
        val sid = env.fixtures.student()
        val subj = env.fixtures.subject(name = "Биология")
        val groupId = env.fixtures.group(name = "11 «А»", subjectId = subj, rate = Money.ZERO)
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60,
            status = LessonStatus.COMPLETED, chargeMinor = Money.ofMajor(30).minor,
        )
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = 2, durationMinutes = 60,
            status = LessonStatus.COMPLETED, chargeMinor = Money.ofMajor(20).minor, groupId = groupId,
        )

        val rows = query.observe(sid).first().classes.sortedBy { it.klass.start }
        assertEquals(LessonKind.INDIVIDUAL, rows[0].kind)
        assertNull(rows[0].groupName, "an individual lesson has no group to name")
        assertEquals(LessonKind.GROUP, rows[1].kind)
        assertEquals("11 «А»", rows[1].groupName)
    }

    @Test
    fun `the kind tally counts both kinds and one kind alone offers no narrowing`() = runTest {
        val sid = env.fixtures.student()
        val subj = env.fixtures.subject(name = "Биология")
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = 1, durationMinutes = 60,
            status = LessonStatus.COMPLETED, chargeMinor = Money.ofMajor(30).minor,
        )
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = 2, durationMinutes = 60,
            status = LessonStatus.SCHEDULED, chargeMinor = 0,
        )

        assertEquals(mapOf(LessonKind.INDIVIDUAL to 2), query.observe(sid).first().lessonKinds)
        assertFalse(query.observe(sid).first().hasBothKinds, "nothing to narrow")

        val groupId = env.fixtures.group(name = "11 «А»", subjectId = subj, rate = Money.ZERO)
        env.fixtures.storedClass(
            studentId = sid, subjectId = subj, startMillis = 3, durationMinutes = 60,
            status = LessonStatus.SCHEDULED, chargeMinor = 0, groupId = groupId,
        )

        assertEquals(
            mapOf(LessonKind.INDIVIDUAL to 2, LessonKind.GROUP to 1),
            query.observe(sid).first().lessonKinds,
        )
        assertTrue(query.observe(sid).first().hasBothKinds)
    }

    @Test
    fun `each subject carries its own rate history newest first`() = runTest {
        val sid = env.fixtures.student()
        val algebra = env.fixtures.subject(name = "Алгебра")
        val geometry = env.fixtures.subject(name = "Геометрия")
        env.enrollmentRepo.enroll(sid, algebra, Money.ofMajor(30))
        env.enrollmentRepo.enroll(sid, geometry, Money.ofMajor(25))
        env.enrollmentRepo.reprice(sid, algebra, Money.ofMajor(35))

        val bySubject = query.observe(sid).first().subjects.associateBy { it.subject.name }
        assertEquals(2, bySubject.getValue("Алгебра").history.size)
        assertEquals(Money.ofMajor(35), bySubject.getValue("Алгебра").history.first().to)
        assertEquals(Money.ofMajor(30), bySubject.getValue("Алгебра").history.first().from)
        assertEquals(1, bySubject.getValue("Геометрия").history.size, "geometry never moved")
        assertTrue(bySubject.getValue("Геометрия").history.single().from == null)
    }
}
