package com.seblful.klas.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.PaymentRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.GroupId
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.SubjectId
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reports.ReportCsv
import com.seblful.klas.domain.reports.ReportPeriod
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.format.AppLocale
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.DocumentSharing
import com.seblful.klas.storage.DocumentStore
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.days
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.navigation.Arrival
import com.seblful.klas.ui.navigation.ArrivalGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    classes: ClassRepository,
    students: StudentRepository,
    subjects: SubjectRepository,
    groups: GroupRepository,
    payments: PaymentRepository,
    private val documents: DocumentStore,
    private val sharing: DocumentSharing,
    @param:Computation private val computation: CoroutineDispatcher,
    private val clock: Clock,
) : ViewModel() {

    /**
     * The zone the report's dates are read in — **the injected [Clock]'s**, like `ScheduleViewModel`'s and
     * `ClassEditViewModel`'s.
     *
     * It was `ZoneId.systemDefault()`, which made this the one view model outside the app's own clock seam
     * — on the screen where a month boundary decides the money. `di/TimeModule` states the rule and
     * `domain/billing/PaymentDate` exists entirely because one surface broke it. The cost was visible in the test:
     * `ReportsViewModelTest` had to re-declare the zone, derive its fixtures from the real current year and
     * assert the opening period by **re-evaluating the production expression**, which is an assertion that
     * cannot fail. A report across a year boundary could not be written at all.
     */
    private val zone: ZoneId get() = clock.zone

    /**
     * The period on screen, opening on **the year in progress** — re-read from the clock rather than fixed
     * at construction, so a phone left on this screen over midnight on 31 December does not go on reporting
     * last year.
     */
    private val _period = MutableStateFlow(ReportPeriod.currentYear(LocalDate.now(clock)))
    val period: StateFlow<ReportPeriod> = _period.asStateFlow()

    /** Everything a report is assembled from — five flows, so the period can be a sixth. */
    private data class Sources(
        val classes: List<TutoringClass>,
        val students: List<Student>,
        val subjects: List<Subject>,
        val groups: List<Group>,
        val payments: List<Payment>,
    )

    /**
     * [Loadable] because an empty report and an unread one look identical and mean opposite things:
     * `Reports.Report(period)` as an initial value made every cold open of this screen state «Нет
     * данных» — that the tutor has earned nothing — before the first read came back.
     */
    val state: StateFlow<Loadable<Reports.Report>> = combine(
        classes.observeAll(),
        students.observeAll(),
        subjects.observeAll(),
        groups.observeAll(),
        payments.observeAll(),
        ::Sources,
    ).combine(_period, ::Pair)
        // Today re-read at midnight ([days]), not once per emission: the current period's per-day
        // figures count the days gone, and the tab stays alive behind the bar overnight.
        .combine(clock.days()) { (sources, period), today -> sources.report(period, today) }
        .ready()
        // The heaviest derivation in the app — every class against every payment and every student,
        // re-run on each write anywhere — and it used to run on the main thread. See [Computation].
        .derive(this, computation, Loadable.Loading)

    /** Reads the same period at another width, keeping where the tutor was looking. */
    fun setKind(kind: ReportPeriod.Kind) = _period.update { it.withKind(kind) }

    /** Steps one period back (−1) or forward (+1). All time ignores it — it has nowhere to go. */
    fun shift(by: Int) = _period.update { it.shifted(by) }

    private val arrivals = ArrivalGate()

    /**
     * Whether [arrival] — the debt digest's tap — is one the screen has not scrolled for yet. The screen
     * hands it over every time the tab comes back, and scrolling each time would take the tutor away from
     * wherever they had read to. See [Arrival].
     */
    fun admitDebts(arrival: Arrival<Unit>?): Boolean = arrivals.admit(arrival) != null

    /**
     * Whether the export landed. One-shot, because a file written is not a state to be in.
     *
     * [SHARED] rather than reusing [OK]: the two are different promises. A saved file is somewhere the
     * tutor chose and can go back to; a shared one has been handed to another app, and «Файл сохранён»
     * about a message the tutor may or may not have sent would be the app claiming credit for something
     * it cannot see the end of.
     */
    enum class ExportEvent { OK, SHARED, ERROR }

    val events = UiEvents<ExportEvent>()

    /**
     * Writes the report to the document the tutor picked.
     *
     * The **content** is assembled by the screen and handed in: every line is labelled, the section
     * headings are string resources, and language is not the view model's to own. What is left here is
     * the I/O and its outcome.
     *
     * In `viewModelScope` rather than the application scope, unlike the app's database writes. The
     * distinction is what a half-finished one leaves behind: an abandoned export is a short file in a
     * place the tutor chose and can see, not a row missing from the ledger. `DiagnosticsViewModel`
     * exports on the same terms.
     */
    fun export(ref: DocumentRef, header: List<String>, lines: List<ReportCsv.Line>) {
        viewModelScope.launch {
            val ok = runCatchingCancellable {
                documents.write(ref, csv(header, lines))
            }.isSuccess
            events.send(if (ok) ExportEvent.OK else ExportEvent.ERROR)
        }
    }

    /**
     * The file, both ways out — saved and shared assemble the *same bytes*, which is what
     * `ReportsViewModelTest` pins.
     *
     * The dialect is the app language's ([AppLocale]): the file is read away from the phone but by the tutor
     * who exported it, in a spreadsheet set up for the numbers they write — see [ReportCsv].
     */
    private fun csv(header: List<String>, lines: List<ReportCsv.Line>): String =
        ReportCsv.build(header, lines, ReportCsv.Dialect.forDecimal(AppLocale.decimalSeparator))

    /**
     * Hands the same report to another app, through the system share sheet.
     *
     * The screen already had a way to *save* a CSV and no way to *send* one, which is the thing a tutor
     * actually does with a month: it goes to an accountant, or to their own mail so it is somewhere other
     * than this phone. `CreateDocument` cannot do that — it puts a file in a folder and the tutor then has
     * to go and find it in a second app — and it is the same bytes either way, so the gap was one action.
     *
     * Same content, same assembly, same scope as [export]: the file is a means, not a record, so an
     * abandoned share leaves a stale file in the cache and nothing else. [filename] and [subject] are
     * words and arrive from the screen for the reason the CSV's headings do.
     */
    fun share(filename: String, subject: String, header: List<String>, lines: List<ReportCsv.Line>) {
        viewModelScope.launch {
            val ok = runCatchingCancellable {
                sharing.share(
                    filename = filename,
                    mimeType = CSV_MIME_TYPE,
                    subject = subject,
                    text = csv(header, lines),
                )
            }.isSuccess
            events.send(if (ok) ExportEvent.SHARED else ExportEvent.ERROR)
        }
    }

    /**
     * The report itself is [Reports.of]'s — what a report counts, how it is ordered and which sections
     * are worth showing are all statements about reports, and none of them is this class's to make. What
     * is left here is naming: two id→name tables the domain has no database to look up.
     */
    private fun Sources.report(period: ReportPeriod, today: LocalDate): Reports.Report = Reports.of(
        period = period,
        classes = classes,
        students = students,
        payments = payments,
        // Keyed by typed ids, so each table can only be handed to the breakdown it names. The two are
        // otherwise interchangeable, and a mismatch drew a real amount against a blank label rather
        // than failing — see `model/Ids.kt`.
        subjectNames = subjects.associate { SubjectId(it.id) to it.name },
        // Archived groups included: income a group already earned stays the tutor's, and a line
        // labelled by an id would be the only unreadable row on the screen.
        groupNames = groups.associate { GroupId(it.id) to it.name },
        zone = zone,
        today = today,
    )

    private companion object {
        /**
         * What a report is, to the app receiving it. `text/csv` rather than `text/plain`: the type is
         * what decides who the share sheet offers, and the point of this file is that it opens in a
         * spreadsheet. The screen states the same constant for the `CreateDocument` contract, which is
         * the platform's half of the same question.
         */
        const val CSV_MIME_TYPE = "text/csv"
    }
}
