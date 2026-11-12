package com.seblful.klas.queries.reports

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.reports.ReportPeriod
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.MutableClock
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.store
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsQueryTest {
    private val zone = TimeZone.of("Europe/Minsk")
    private val clock = MutableClock(Instant.parse("2026-06-15T09:00:00Z"), zone)
    private val env = TestEnv(clock = clock)
    private val query = env.store().reports
    private val thisYear = clock.today().year

    @AfterTest
    fun close() = env.close()

    private suspend fun report() = query.observe(MutableStateFlow(ReportPeriod.currentYear(clock.today()))).first()

    private fun at(year: Int, month: Int, day: Int = 10): Long =
        LocalDate(year, month, day).atStartOfDayIn(zone).toEpochMilliseconds()

    private suspend fun completed(
        studentId: Long,
        subjectId: Long,
        millis: Long,
        major: Long,
        groupId: Long? = null,
    ) = env.fixtures.storedClass(
        studentId = studentId, subjectId = subjectId, startMillis = millis, durationMinutes = 60,
        status = LessonStatus.COMPLETED, chargeMinor = Money.ofMajor(major).minor, groupId = groupId,
    )

    @Test
    fun `debts ignore the period`() = runTest {
        val sid = env.fixtures.student(name = "Аня")
        val subj = env.fixtures.subject(name = "Математика")
        completed(sid, subj, at(thisYear - 2, 4), 200) // billed long ago, never paid
        completed(sid, subj, at(thisYear, 4), 50)
        env.paymentRepo.restore(
            Payment(studentId = sid, date = Instant.fromEpochMilliseconds(at(thisYear, 4)), amount = Money.ofMajor(50)),
        )

        val state = report()
        assertEquals(Money.ofMajor(50), state.totalIncome, "this year's income only")
        assertEquals(Money.ofMajor(200), state.totalOutstanding, "but the whole debt")
        assertEquals(listOf("Аня"), state.debts.map { it.label })
    }

    @Test
    fun `income by group names each group and leaves individual lessons out`() = runTest {
        val a = env.fixtures.student(name = "Аня")
        val b = env.fixtures.student(name = "Борис")
        val subj = env.fixtures.subject(name = "Математика")
        val evening = env.fixtures.group(name = "Вечерняя", rate = Money.ZERO)
        val morning = env.fixtures.group(name = "Утренняя", rate = Money.ZERO)
        completed(a, subj, at(thisYear, 2), 25, groupId = evening)
        completed(b, subj, at(thisYear, 2), 25, groupId = evening)
        completed(a, subj, at(thisYear, 3), 30, groupId = morning)
        completed(a, subj, at(thisYear, 4), 90) // individual: not a group's income

        val byGroup = report().byGroup
        assertEquals(listOf("Вечерняя", "Утренняя"), byGroup.map { it.label })
        assertEquals(listOf(Money.ofMajor(50), Money.ofMajor(30)), byGroup.map { it.amount })
        // No share: the groups do not add up to the income, so a percentage of it is not theirs to
        // claim — the kind split is what says how much of everything came from groups.
        assertEquals(listOf(null, null), byGroup.map { it.percent })
    }

    @Test
    fun `an archived group keeps its name on the income it earned`() = runTest {
        val sid = env.fixtures.student(name = "Аня")
        val subj = env.fixtures.subject(name = "Математика")
        val old = env.fixtures.group(name = "Выпускники", archived = true, rate = Money.ZERO)
        completed(sid, subj, at(thisYear, 2), 40, groupId = old)

        assertEquals(listOf("Выпускники"), report().byGroup.map { it.label })
    }

    @Test
    fun `lines that partition the income carry their share of it debts do not`() = runTest {
        val a = env.fixtures.student(name = "Аня")
        val b = env.fixtures.student(name = "Борис")
        val subj = env.fixtures.subject(name = "Математика")
        completed(a, subj, at(thisYear, 2), 75)
        completed(b, subj, at(thisYear, 3), 25)

        assertEquals(listOf(75, 25), report().byStudent.map { it.percent })
        assertEquals(listOf(100), report().bySubject.map { it.percent })
        // A debt is money to collect, not a proportion: nothing to print a percentage of.
        assertEquals(listOf(null, null), report().debts.map { it.percent })
    }

    @Test
    fun `archived students retain their income breakdowns and debts`() = runTest {
        val active = env.fixtures.student(name = "Аня")
        val archived = env.fixtures.student(name = "Старый", archived = true)
        val subj = env.fixtures.subject(name = "Математика")
        // Archiving ends teaching, not the financial history of work already done.
        completed(active, subj, at(thisYear, 3), 30)
        completed(archived, subj, at(thisYear, 3), 100)

        val state = report()
        assertEquals(Money.ofMajor(130), state.totalIncome)
        assertEquals(listOf("Старый", "Аня"), state.byStudent.map { it.label })
        assertEquals(Money.ofMajor(130), state.totalOutstanding)
        assertEquals(listOf("Старый", "Аня"), state.debts.map { it.label })
    }

    @Test
    fun `reports remain available when the only student with history is archived`() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        completed(student, subject, at(thisYear, 3), 30)
        env.lessonChanges.archiveWithoutUndo(env.studentRepo.getById(student)!!)

        assertTrue(report().hasData)
        assertEquals(Money.ofMajor(30), report().totalIncome)
        assertEquals(Money.ofMajor(30), report().totalOutstanding)
    }

    @Test
    fun `breakdowns drop zero entries and order by amount largest first`() = runTest {
        val a = env.fixtures.student(name = "Аня")
        val b = env.fixtures.student(name = "Борис")
        val subj = env.fixtures.subject(name = "Математика")
        completed(a, subj, at(thisYear, 1), 20)
        completed(b, subj, at(thisYear, 2), 50)

        assertEquals(listOf("Борис", "Аня"), report().byStudent.map { it.label })
    }

    @Test
    fun `a fully paid student adds no debt`() = runTest {
        val sid = env.fixtures.student(name = "Аня")
        val subj = env.fixtures.subject(name = "Математика")
        completed(sid, subj, at(thisYear, 3), 30)
        env.paymentRepo.restore(
            Payment(studentId = sid, date = Instant.fromEpochMilliseconds(at(thisYear, 3)), amount = Money.ofMajor(30)),
        )

        assertTrue(report().debts.isEmpty())
        assertEquals(Money.ZERO, report().totalOutstanding)
        assertEquals(Money.ofMajor(30), report().totalIncome)
    }

    @Test
    fun `a year is charted as its twelve months newest amounts read from the bars`() = runTest {
        val sid = env.fixtures.student(name = "Аня")
        val subj = env.fixtures.subject(name = "Математика")
        completed(sid, subj, at(thisYear, 1), 10)
        completed(sid, subj, at(thisYear, 3), 20)

        val trend = report().trend
        assertEquals(Reports.Trend.Bucket.MONTHS, trend.bucket)
        assertEquals(12, trend.bars.size)
        assertEquals(Money.ofMajor(20), trend.peak?.total)
        assertFalse(trend.isEmpty)
    }

    @Test
    fun `the tutor's own lesson count treats a group evening as one lesson`() = runTest {
        val a = env.fixtures.student(name = "Аня")
        val b = env.fixtures.student(name = "Борис")
        val subj = env.fixtures.subject(name = "Математика")
        val group = env.fixtures.group(name = "Вечерняя", rate = Money.ZERO)
        val evening = at(thisYear, 2)
        completed(a, subj, evening, 25, groupId = group)
        completed(b, subj, evening, 25, groupId = group)
        completed(a, subj, at(thisYear, 2, day = 11), 90)

        val taught = report().taught
        assertEquals(2, taught.lessons, "one group lesson plus one individual")
        assertEquals(120, taught.minutes)
        assertEquals(Money.ofMajor(140), taught.earned)
    }

    @Test
    fun `a period with no income still knows the database is not empty`() = runTest {
        val sid = env.fixtures.student(name = "Аня")
        val subj = env.fixtures.subject(name = "Математика")
        completed(sid, subj, at(thisYear - 3, 5), 60)

        val state = report()
        assertTrue(state.noIncome, "this year earned nothing")
        // ...but the screen must still draw the picker that gets the tutor out of the empty year.
        assertTrue(state.hasData)
        assertTrue(state.byStudent.isEmpty())
    }

    @Test
    fun `an empty database reports as empty`() = runTest {
        assertFalse(report().hasData)
        assertTrue(report().noIncome)
    }

    @Test
    fun `every breakdown line carries a name in all four sections at once`() = runTest {
        val ann = env.fixtures.student(name = "Аня")
        val bob = env.fixtures.student(name = "Борис")
        val algebra = env.fixtures.subject(name = "Алгебра")
        val group = env.fixtures.group(name = "11 «А»", rate = Money.ZERO)
        // An individual lesson, a group lesson for two, and a debt — so all four sections have rows.
        completed(ann, algebra, at(thisYear, 5), 60)
        completed(ann, algebra, at(thisYear, 6), 20, groupId = group)
        completed(bob, algebra, at(thisYear, 6), 20, groupId = group)
        env.paymentRepo.restore(
            Payment(studentId = ann, date = Instant.fromEpochMilliseconds(at(thisYear, 7)), amount = Money(1000)),
        )

        val state = report()

        assertTrue(state.byStudent.isNotEmpty(), "the fixture fills every section")
        assertTrue(state.bySubject.isNotEmpty())
        assertTrue(state.byGroup.isNotEmpty())
        assertTrue(state.debts.isNotEmpty())
        val unlabelled = (state.byStudent + state.bySubject + state.byGroup + state.debts)
            .filter { it.label.isBlank() }
        assertEquals(
            emptyList<Any>(), unlabelled, "a blank label means a breakdown met the wrong names",
        )
        // And the names really are the right ones, not merely non-blank.
        assertEquals(listOf("Алгебра"), state.bySubject.map { it.label })
        assertEquals(listOf("11 «А»"), state.byGroup.map { it.label })
        assertEquals(setOf("Аня", "Борис"), state.byStudent.map { it.label }.toSet())
    }
}
