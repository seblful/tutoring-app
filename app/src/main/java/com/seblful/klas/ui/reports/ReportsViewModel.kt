package com.seblful.klas.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.reports.ReportCsv
import com.seblful.klas.domain.reports.ReportPeriod
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.domain.time.today
import com.seblful.klas.format.AppLocale
import com.seblful.klas.queries.reports.ReportsQuery
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.DocumentSharing
import com.seblful.klas.storage.DocumentStore
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.navigation.Arrival
import com.seblful.klas.ui.navigation.ArrivalGate
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ReportsViewModel @Inject constructor(
    reports: ReportsQuery,
    private val documents: DocumentStore,
    private val sharing: DocumentSharing,
    @param:Computation private val computation: CoroutineDispatcher,
    private val clock: WallClock,
) : ViewModel() {

    private val _period = MutableStateFlow(ReportPeriod.currentYear(clock.today()))
    val period: StateFlow<ReportPeriod> = _period.asStateFlow()

    /**
     * [Loadable] because an empty report and an unread one look identical and mean opposite things:
     * `Reports.Report(period)` as an initial value made every cold open of this screen state «Нет
     * данных» — that the tutor has earned nothing — before the first read came back.
     */
    val state: StateFlow<Loadable<Reports.Report>> = reports.observe(_period)
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
     * Whether [arrival] — the debt digest's tap — is one the screen still owes a scroll for. The screen
     * hands it over every time the tab comes back, and scrolling each time would take the tutor away from
     * wherever they had read to. See [Arrival].
     */
    fun owesDebts(arrival: Arrival<Unit>?): Boolean = arrivals.owes(arrival)

    /** The scroll the tap asked for has run. That is the point it can no longer be owed again. */
    fun debtsScrolled(arrival: Arrival<Unit>?) = arrivals.honour(arrival)

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

    /** rites the report to the document the tutor picked. */
    fun export(ref: DocumentRef, header: List<String>, lines: List<ReportCsv.Line>) {
        viewModelScope.launch {
            val ok = runCatchingCancellable {
                documents.write(ref, csv(header, lines))
            }.isSuccess
            events.send(if (ok) ExportEvent.OK else ExportEvent.ERROR)
        }
    }

    private fun csv(header: List<String>, lines: List<ReportCsv.Line>): String =
        ReportCsv.build(header, lines, ReportCsv.Dialect.forDecimal(AppLocale.decimalSeparator))

    /** ands the same report to another app, through the system share sheet. */
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
