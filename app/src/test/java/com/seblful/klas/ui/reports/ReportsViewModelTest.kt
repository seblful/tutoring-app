package com.seblful.klas.ui.reports

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.PaymentMethod
import com.seblful.klas.domain.reports.ReportCsv
import com.seblful.klas.domain.reports.ReportPeriod
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.domain.time.today
import com.seblful.klas.format.AppLocale
import com.seblful.klas.queries.reports.ReportScope
import com.seblful.klas.queries.reports.ReportsQuery
import com.seblful.klas.testutil.CountingDispatcher
import com.seblful.klas.testutil.FakeDocumentStore
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.MutableClock
import com.seblful.klas.testutil.RecordingDocumentSharing
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.navigation.Arrival
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReportsViewModelTest {

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    @get:Rule(order = 0)
    val env = TestEnv()
    private val zone: TimeZone = TimeZone.of("Europe/Minsk")

    private val clock: WallClock = WallClock.fixed(Instant.parse("2026-06-15T09:00:00Z"), zone)

    private val thisYear = clock.today().year

    private val documents = FakeDocumentStore()

    private val sharing = RecordingDocumentSharing()

    private fun vm() =
        mainRule.own(
            ReportsViewModel(
                ReportsQuery(
                    env.classRepo, env.studentRepo, env.subjectRepo,
                    env.groupRepo, env.balanceRepo, env.paymentRepo, clock,
                ),
                documents, sharing, mainRule.dispatcher, clock,
            ),
        )

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
    fun `the month-to-date comparison takes in a new day at midnight`() = runTest(mainRule.dispatcher) {
        val lateEvening = MutableClock(Instant.parse("2026-06-15T20:00:00Z"), zone)
        val subj = env.fixtures.subject("Математика")
        val sid = env.fixtures.pricedStudent("Аня", subj)
        completed(sid, subj, at(2026, 5, 1), 50)
        completed(sid, subj, at(2026, 5, 16), 50)
        completed(sid, subj, at(2026, 6, 1), 50)

        val vm = mainRule.own(
            ReportsViewModel(
                ReportsQuery(
                    env.classRepo, env.studentRepo, env.subjectRepo,
                    env.groupRepo, env.balanceRepo, env.paymentRepo, lateEvening,
                ),
                documents, sharing, mainRule.dispatcher, lateEvening,
            ),
        )
        vm.setKind(ReportPeriod.Kind.MONTH)
        observing(vm.state)
        runCurrent()
        assertEquals("1–15 June against 1–15 May", Reports.Change.Percent(0), vm.state.current.change)

        val twoHours = 2.hours.inWholeMilliseconds
        lateEvening.advanceBy(twoHours)
        advanceTimeBy(twoHours)
        runCurrent()

        assertEquals("1–16 June against 1–16 May", Reports.Change.Percent(-50), vm.state.current.change)
    }

    @Test
    fun `the screen opens on the year in progress`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)
        runCurrent()

        assertEquals(ReportPeriod.currentYear(LocalDate(2026, 6, 15)), vm.state.current.period)
        assertEquals(2026, vm.state.current.period.anchor.year)
        assertEquals(ReportPeriod.Kind.YEAR, vm.period.value.kind)
    }

    @Test
    fun `only the chosen period's lessons are counted`() = runTest(mainRule.dispatcher) {
        val sid = env.fixtures.student(name = "Аня")
        val subj = env.fixtures.subject(name = "Математика")
        completed(sid, subj, at(thisYear, 3), 30)
        completed(sid, subj, at(thisYear - 1, 3), 100)

        val vm = vm()
        observing(vm.state)
        runCurrent()

        assertEquals("last year's income is not this year's", Money.ofMajor(30), vm.state.current.totalIncome)

        vm.setKind(ReportPeriod.Kind.ALL)
        runCurrent()
        assertEquals(Money.ofMajor(130), vm.state.current.totalIncome)

        // Back to a year, one step behind: the year that only holds the older lesson.
        vm.setKind(ReportPeriod.Kind.YEAR)
        vm.shift(-1)
        runCurrent()
        assertEquals(Money.ofMajor(100), vm.state.current.totalIncome)
    }

    @Test
    fun `a report opening in January does not count last December`() = runTest(mainRule.dispatcher) {
        val newYear = WallClock.fixed(Instant.parse("2027-01-05T09:00:00Z"), zone)
        val subj = env.fixtures.subject("Математика")
        val sid = env.fixtures.pricedStudent("Аня", subj)
        completed(sid, subj, at(2026, 12, 28), 90)
        completed(sid, subj, at(2027, 1, 3), 40)

        val vm = mainRule.own(
            ReportsViewModel(
                ReportsQuery(
                    env.classRepo, env.studentRepo, env.subjectRepo,
                    env.groupRepo, env.balanceRepo, env.paymentRepo, newYear,
                ),
                documents, sharing, mainRule.dispatcher, newYear,
            ),
        )
        observing(vm.state)
        runCurrent()

        assertEquals(2027, vm.period.value.anchor.year)
        assertEquals("December belongs to the year that has ended", Money.ofMajor(40), vm.state.current.totalIncome)

        // A month at a time, then one step back: out of January 2027 and into December 2026.
        vm.setKind(ReportPeriod.Kind.MONTH)
        vm.shift(-1)
        runCurrent()

        assertEquals(12, vm.state.current.period.anchor.month.number)
        assertEquals(2026, vm.state.current.period.anchor.year)
        assertEquals(Money.ofMajor(90), vm.state.current.totalIncome)
    }

    @Test
    fun `narrowing to a month keeps the month the tutor stepped to`() = runTest(mainRule.dispatcher) {
        val sid = env.fixtures.student(name = "Аня")
        val subj = env.fixtures.subject(name = "Математика")
        completed(sid, subj, at(thisYear, 5), 40)
        completed(sid, subj, at(thisYear, 6), 70)

        val vm = vm()
        observing(vm.state)
        runCurrent()

        vm.setKind(ReportPeriod.Kind.MONTH)
        runCurrent()
        // The anchor is the pinned clock's month, June; one step back is May, and only May's lesson counts.
        vm.shift(-1)
        runCurrent()

        assertEquals(5, vm.state.current.period.anchor.month.number)
        assertEquals(Money.ofMajor(40), vm.state.current.totalIncome)
    }

    // --- Where the report is computed -------------------------------------------------------------

    @Test
    fun `the report is derived on the computation dispatcher, not the main one`() =
        runTest(mainRule.dispatcher) {
            // Standard rather than the rule's unconfined dispatcher: this one has to actually
            // *dispatch* for the count to mean anything, and it shares the test's scheduler either way.
            val counting = CountingDispatcher(StandardTestDispatcher(testScheduler))
            val ann = env.fixtures.student(name = "Аня")
            val subject = env.fixtures.subject(name = "Алгебра")
            completed(ann, subject, at(thisYear, 5), major = 30)

            val vm = mainRule.own(
                ReportsViewModel(
                    ReportsQuery(
                        env.classRepo, env.studentRepo, env.subjectRepo,
                        env.groupRepo, env.balanceRepo, env.paymentRepo, clock,
                    ),
                    documents, sharing, counting, clock,
                ),
            )
            observing(vm.state)
            runCurrent()

            assertEquals("the report was built", Money.ofMajor(30), vm.state.current.totalIncome)
            assertTrue(
                "nothing reached the computation dispatcher, so the derivation ran on the main one",
                counting.dispatches > 0,
            )
        }

/**
     * Sharing hands over **the same bytes** the export writes, as a CSV.
     *
     * Two things can go wrong here and neither is visible on a device without another app installed: the
     * share could carry a different assembly of the report than the file does, and it could offer the
     * wrong type — `text/plain` would hand a spreadsheet to a notes app.
     */
    @Test
    fun `sharing hands over the same CSV the export writes`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.state)
        runCurrent()

        val header = listOf("Раздел", "Строка", "Сумма")
        val lines = listOf(ReportCsv.Line("Итого", "2026", Money.ofMajor(30)))

        vm.share(
            filename = "klas-report.csv",
            subject = "Отчёт Klas",
            header = header,
            lines = lines,
        )
        runCurrent()

        val shared = sharing.shared.single()
        assertEquals("klas-report.csv", shared.filename)
        assertEquals(
            "a CSV shared as anything else is offered to the wrong apps",
            "text/csv",
            shared.mimeType,
        )
        assertEquals("Отчёт Klas", shared.subject)
        assertEquals(
            "the file and the share are one assembly",
            // `AppLocale` has not been moved, so the app's language is the JVM default's and the dialect
            // follows it — the point of the assertion is that both ways out produce the *same* bytes.
            ReportCsv.build(header, lines, ReportCsv.Dialect.forDecimal(AppLocale.decimalSeparator)),
            shared.text,
        )
    }

    @Test
    fun `a share nothing can receive is reported`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        sharing.failing = true
        var received: ReportsViewModel.ExportEvent? = null
        val job = backgroundScope.launch { vm.events.stream.collect { received = it } }

        vm.share(filename = "klas-report.csv", subject = "x", header = emptyList(), lines = emptyList())
        runCurrent()
        job.cancel()

        assertEquals(ReportsViewModel.ExportEvent.ERROR, received)
    }

    @Test
    fun `the debt digest's tap is owed until it is honoured, and a new tap again`() {
        val vm = vm()
        val tap = Arrival(Unit)

        assertTrue(vm.owesDebts(tap))
        assertTrue("offering it does not spend it", vm.owesDebts(tap))
        vm.debtsScrolled(tap)
        assertFalse("the same tap, on the tab's return", vm.owesDebts(tap))
        assertTrue("a second tap on the notification", vm.owesDebts(Arrival(Unit)))
        assertFalse("nobody asked", vm.owesDebts(null))
    }

    @Test
    fun `the report reads what was paid in its period, apart from what was earned`() = runTest(mainRule.dispatcher) {
        val subj = env.fixtures.subject("Математика")
        val sid = env.fixtures.pricedStudent("Аня", subj)
        completed(sid, subj, at(thisYear, 3), 50)
        env.paymentRepo.record(sid, Money.ofMajor(30), LocalDate(thisYear, 4, 2), PaymentMethod.CASH)
        env.paymentRepo.record(sid, Money.ofMajor(40), LocalDate(thisYear - 1, 12, 30), PaymentMethod.CARD)
        val vm = vm()
        observing(vm.state)
        runCurrent()

        val report = vm.state.current
        assertEquals(Money.ofMajor(50), report.totalIncome)
        assertEquals(Money.ofMajor(30), report.received.total)
        assertEquals(listOf(Reports.MethodLine(PaymentMethod.CASH, Money.ofMajor(30))), report.received.byMethod)
    }

    @Test
    fun `a report narrowed to a student or a subject adds up only theirs`() = runTest(mainRule.dispatcher) {
        val maths = env.fixtures.subject("Математика")
        val physics = env.fixtures.subject("Физика")
        val anya = env.fixtures.pricedStudent("Аня", maths)
        val boris = env.fixtures.pricedStudent("Борис", physics)
        completed(anya, maths, at(thisYear, 3), 50)
        completed(boris, physics, at(thisYear, 3, 12), 70)
        env.paymentRepo.record(anya, Money.ofMajor(30), LocalDate(thisYear, 4, 2), PaymentMethod.CASH)
        env.paymentRepo.record(boris, Money.ofMajor(20), LocalDate(thisYear, 4, 3), PaymentMethod.CARD)
        val vm = vm()
        observing(vm.state)
        observing(vm.scopes)
        runCurrent()
        assertEquals(Money.ofMajor(120), vm.state.current.totalIncome)
        assertEquals(
            listOf("Математика", "Физика", "Аня", "Борис"),
            vm.scopes.value.map { it.name },
        )

        vm.setScope(ReportScope.Student(anya))
        runCurrent()
        assertEquals(Money.ofMajor(50), vm.state.current.totalIncome)
        assertEquals(Money.ofMajor(30), vm.state.current.received.total)
        assertEquals(listOf(anya), vm.state.current.debts.mapNotNull { it.studentId })

        vm.setScope(ReportScope.Subject(physics))
        runCurrent()
        assertEquals(Money.ofMajor(70), vm.state.current.totalIncome)
        assertTrue("no payment belongs to a subject", vm.state.current.received.total.isZero)
    }
}
