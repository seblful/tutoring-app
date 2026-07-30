package com.seblful.klas.ui.reports

import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.PaymentEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.ReportPeriod
import com.seblful.klas.domain.Reports
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.testutil.CountingDispatcher
import com.seblful.klas.testutil.FakeDocumentStore
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val env = TestEnv()
    private val zone: ZoneId = ZoneId.systemDefault()

    /**
     * The year the screen opens on. Income is filtered by period now, so a fixture dated at the epoch
     * would be invisible in every default-period assertion — the dates below are anchored to *this*
     * year on purpose, and the day of the month is fixed so nothing depends on the time of day.
     */
    private val thisYear = LocalDate.now(zone).year

    /** The report's CSV export writes here; the report assertions below never touch it. */
    private val documents = FakeDocumentStore()

    private fun vm() =
        ReportsViewModel(
            env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, env.paymentRepo,
            documents, mainRule.dispatcher,
        )

    /** Local midnight on a date, as the millis a class entity stores. */
    private fun at(year: Int, month: Int, day: Int = 10): Long =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()

    private suspend fun completed(
        studentId: Long,
        subjectId: Long,
        millis: Long,
        major: Long,
        groupId: Long? = null,
    ) = env.fixtures.storedClass(
            studentId = studentId, subjectId = subjectId, startMillis = millis, durationMinutes = 60,
            status = "COMPLETED", chargeMinor = Money.ofMajor(major).minor, groupId = groupId,
        )

    @Test
    fun `the screen opens on the year in progress`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(ReportPeriod.currentYear(LocalDate.now(zone)), vm.state.current.period)
        assertEquals(ReportPeriod.Kind.YEAR, vm.period.value.kind)
    }

    @Test
    fun `only the chosen period's lessons are counted`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        completed(sid, subj, at(thisYear, 3), 30)
        completed(sid, subj, at(thisYear - 1, 3), 100)

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals("last year's income is not this year's", Money.ofMajor(30), vm.state.current.totalIncome)

        vm.setKind(ReportPeriod.Kind.ALL)
        advanceUntilIdle()
        assertEquals(Money.ofMajor(130), vm.state.current.totalIncome)

        // Back to a year, one step behind: the year that only holds the older lesson.
        vm.setKind(ReportPeriod.Kind.YEAR)
        vm.shift(-1)
        advanceUntilIdle()
        assertEquals(Money.ofMajor(100), vm.state.current.totalIncome)
    }

    @Test
    fun `narrowing to a month keeps the month the tutor stepped to`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        completed(sid, subj, at(thisYear, 5), 40)
        completed(sid, subj, at(thisYear, 6), 70)

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        vm.setKind(ReportPeriod.Kind.MONTH)
        advanceUntilIdle()
        // The anchor was this year's current month; step to May and only May's lesson counts.
        val toMay = 5 - LocalDate.now(zone).monthValue
        vm.shift(toMay)
        advanceUntilIdle()

        assertEquals(5, vm.state.current.period.anchor.monthValue)
        assertEquals(Money.ofMajor(40), vm.state.current.totalIncome)
    }

    /** A balance is charges minus payments over a whole history — clipping it to a year invents one. */
    @Test
    fun `debts ignore the period`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        completed(sid, subj, at(thisYear - 2, 4), 200) // billed long ago, never paid
        completed(sid, subj, at(thisYear, 4), 50)
        env.paymentRepo.add(Payment(studentId = sid, date = Instant.ofEpochMilli(at(thisYear, 4)), amount = Money.ofMajor(50)))

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        val state = vm.state.current
        assertEquals("this year's income only", Money.ofMajor(50), state.totalIncome)
        assertEquals("but the whole debt", Money.ofMajor(200), state.totalOutstanding)
        assertEquals(listOf("Аня"), state.debts.map { it.label })
    }

    @Test
    fun `income by group names each group and leaves individual lessons out`() = runTest(mainRule.dispatcher) {
        val a = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val b = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val evening = env.groupDao.upsert(GroupEntity(name = "Вечерняя"))
        val morning = env.groupDao.upsert(GroupEntity(name = "Утренняя"))
        completed(a, subj, at(thisYear, 2), 25, groupId = evening)
        completed(b, subj, at(thisYear, 2), 25, groupId = evening)
        completed(a, subj, at(thisYear, 3), 30, groupId = morning)
        completed(a, subj, at(thisYear, 4), 90) // individual: not a group's income

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        val byGroup = vm.state.current.byGroup
        assertEquals(listOf("Вечерняя", "Утренняя"), byGroup.map { it.label })
        assertEquals(listOf(Money.ofMajor(50), Money.ofMajor(30)), byGroup.map { it.amount })
        // No share: the groups do not add up to the income, so a percentage of it is not theirs to
        // claim — the kind split is what says how much of everything came from groups.
        assertEquals(listOf(null, null), byGroup.map { it.percent })
    }

    @Test
    fun `an archived group keeps its name on the income it earned`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val old = env.groupDao.upsert(GroupEntity(name = "Выпускники", archived = true))
        completed(sid, subj, at(thisYear, 2), 40, groupId = old)

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(listOf("Выпускники"), vm.state.current.byGroup.map { it.label })
    }

    /** Students and subjects genuinely partition the income, so their lines carry a share of it. */
    @Test
    fun `lines that partition the income carry their share of it, debts do not`() = runTest(mainRule.dispatcher) {
        val a = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val b = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        completed(a, subj, at(thisYear, 2), 75)
        completed(b, subj, at(thisYear, 3), 25)

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(listOf(75, 25), vm.state.current.byStudent.map { it.percent })
        assertEquals(listOf(100), vm.state.current.bySubject.map { it.percent })
        // A debt is money to collect, not a proportion: nothing to print a percentage of.
        assertEquals(listOf(null, null), vm.state.current.debts.map { it.percent })
    }

    @Test
    fun `archived students are excluded from income, breakdowns and debts`() = runTest(mainRule.dispatcher) {
        val active = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val archived = env.studentDao.upsert(StudentEntity(name = "Старый", archived = true))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        // Active student: completed 30. Archived student: completed 100 (must be ignored everywhere).
        completed(active, subj, at(thisYear, 3), 30)
        completed(archived, subj, at(thisYear, 3), 100)

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        val state = vm.state.current
        assertEquals(Money.ofMajor(30), state.totalIncome)
        assertEquals(1, state.byStudent.size)
        assertEquals("Аня", state.byStudent.single().label)
        // Active student owes 30 (no payment), archived one's debt never appears.
        assertEquals(Money.ofMajor(30), state.totalOutstanding)
        assertEquals(listOf("Аня"), state.debts.map { it.label })
    }

    @Test
    fun `breakdowns drop zero entries and order by amount, largest first`() = runTest(mainRule.dispatcher) {
        val a = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val b = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        completed(a, subj, at(thisYear, 1), 20)
        completed(b, subj, at(thisYear, 2), 50)

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertEquals(listOf("Борис", "Аня"), vm.state.current.byStudent.map { it.label })
    }

    @Test
    fun `a fully paid student adds no debt`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        completed(sid, subj, at(thisYear, 3), 30)
        env.paymentRepo.add(Payment(studentId = sid, date = Instant.ofEpochMilli(at(thisYear, 3)), amount = Money.ofMajor(30)))

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertTrue(vm.state.current.debts.isEmpty())
        assertEquals(Money.ZERO, vm.state.current.totalOutstanding)
        assertEquals(Money.ofMajor(30), vm.state.current.totalIncome)
    }

    @Test
    fun `a year is charted as its twelve months, newest amounts read from the bars`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        completed(sid, subj, at(thisYear, 1), 10)
        completed(sid, subj, at(thisYear, 3), 20)

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        val trend = vm.state.current.trend
        assertEquals(Reports.Trend.Bucket.MONTHS, trend.bucket)
        assertEquals(12, trend.bars.size)
        assertEquals(Money.ofMajor(20), trend.peak?.total)
        assertFalse(trend.isEmpty)
    }

    @Test
    fun `the tutor's own lesson count treats a group evening as one lesson`() = runTest(mainRule.dispatcher) {
        val a = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val b = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        val group = env.groupDao.upsert(GroupEntity(name = "Вечерняя"))
        val evening = at(thisYear, 2)
        completed(a, subj, evening, 25, groupId = group)
        completed(b, subj, evening, 25, groupId = group)
        completed(a, subj, at(thisYear, 2, day = 11), 90)

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        val taught = vm.state.current.taught
        assertEquals("one group lesson plus one individual", 2, taught.lessons)
        assertEquals(120, taught.minutes)
        assertEquals(Money.ofMajor(140), taught.earned)
    }

    @Test
    fun `a period with no income still knows the database is not empty`() = runTest(mainRule.dispatcher) {
        val sid = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val subj = env.subjectDao.upsert(SubjectEntity(name = "Математика"))
        completed(sid, subj, at(thisYear - 3, 5), 60)

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        val state = vm.state.current
        assertTrue("this year earned nothing", state.noIncome)
        // ...but the screen must still draw the picker that gets the tutor out of the empty year.
        assertTrue(state.hasData)
        assertTrue(state.byStudent.isEmpty())
    }

    @Test
    fun `an empty database reports as empty`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()

        assertFalse(vm.state.current.hasData)
        assertTrue(vm.state.current.noIncome)
    }

    /**
     * Every line on the report is labelled — which is the symptom the typed breakdown keys exist to
     * make impossible.
     *
     * The three breakdowns are the same shape, and each is paired by hand with one of three equally
     * shaped tables of names. Pairing two of them wrongly was not a type error, and it did not fail
     * either: the lookup simply missed, `orEmpty()` turned the miss into `""`, and the report drew a
     * real amount against a blank row. Typed ids (`model/Ids.kt`) stop it compiling; this asserts the
     * result the tutor would have seen, so the guarantee is checked from the outside as well.
     */
    @Test
    fun `every breakdown line carries a name, in all four sections at once`() = runTest(mainRule.dispatcher) {
        val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
        val bob = env.studentDao.upsert(StudentEntity(name = "Борис"))
        val algebra = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
        val group = env.groupDao.upsert(GroupEntity(name = "11 «А»"))
        // An individual lesson, a group lesson for two, and a debt — so all four sections have rows.
        completed(ann, algebra, at(thisYear, 5), 60)
        completed(ann, algebra, at(thisYear, 6), 20, groupId = group)
        completed(bob, algebra, at(thisYear, 6), 20, groupId = group)
        env.paymentDao.insert(
            PaymentEntity(
                studentId = ann, dateMillis = at(thisYear, 7), amountMinor = 1000,
            ),
        )

        val vm = vm()
        observing(vm.state)
        advanceUntilIdle()
        val state = vm.state.current

        assertTrue("the fixture fills every section", state.byStudent.isNotEmpty())
        assertTrue(state.bySubject.isNotEmpty())
        assertTrue(state.byGroup.isNotEmpty())
        assertTrue(state.debts.isNotEmpty())
        val unlabelled = (state.byStudent + state.bySubject + state.byGroup + state.debts)
            .filter { it.label.isBlank() }
        assertEquals("a blank label means a breakdown met the wrong names", emptyList<Any>(), unlabelled)
        // And the names really are the right ones, not merely non-blank.
        assertEquals(listOf("Алгебра"), state.bySubject.map { it.label })
        assertEquals(listOf("11 «А»"), state.byGroup.map { it.label })
        assertEquals(setOf("Аня", "Борис"), state.byStudent.map { it.label }.toSet())
    }

    // --- Where the report is computed -------------------------------------------------------------

    /**
     * A tripwire, not a behaviour test: the report comes out the same whichever thread built it, so
     * nothing else here would notice the `flowOn` being deleted.
     *
     * It is worth guarding because this is the heaviest derivation in the app — every class against
     * every payment and every student, re-run on each lesson or payment written anywhere — and it used
     * to run on `Dispatchers.Main.immediate`, since `stateIn(viewModelScope)` collects there and Room's
     * own `flowOn` governs only what is upstream of *it*. Working at the scale a tutor starts with and
     * failing at the scale they arrive at is the worst way for it to be wrong.
     */
    @Test
    fun `the report is derived on the computation dispatcher, not the main one`() =
        runTest(mainRule.dispatcher) {
            // Standard rather than the rule's unconfined dispatcher: this one has to actually
            // *dispatch* for the count to mean anything, and it shares the test's scheduler either way.
            val counting = CountingDispatcher(StandardTestDispatcher(testScheduler))
            val ann = env.studentDao.upsert(StudentEntity(name = "Аня"))
            val subject = env.subjectDao.upsert(SubjectEntity(name = "Алгебра"))
            completed(ann, subject, at(thisYear, 5), major = 30)

            val vm = ReportsViewModel(
                env.classRepo, env.studentRepo, env.subjectRepo, env.groupRepo, env.paymentRepo,
                documents, counting,
            )
            observing(vm.state)
            advanceUntilIdle()

            assertEquals("the report was built", Money.ofMajor(30), vm.state.current.totalIncome)
            assertTrue(
                "nothing reached the computation dispatcher, so the derivation ran on the main one",
                counting.dispatches > 0,
            )
        }
}
