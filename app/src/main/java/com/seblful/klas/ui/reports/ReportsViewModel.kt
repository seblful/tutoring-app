package com.seblful.klas.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.di.Computation
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.PaymentRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.ReportCsv
import com.seblful.klas.domain.ReportPeriod
import com.seblful.klas.domain.Reports
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.GroupId
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.SubjectId
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.DocumentStore
import com.seblful.klas.ui.common.UiEvents
import com.seblful.klas.ui.common.derive
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    @param:Computation private val computation: CoroutineDispatcher,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val _period = MutableStateFlow(ReportPeriod.currentYear(LocalDate.now(zone)))
    val period: StateFlow<ReportPeriod> = _period.asStateFlow()

    /** Everything a report is assembled from — five flows, so the period can be a sixth. */
    private data class Sources(
        val classes: List<TutoringClass>,
        val students: List<Student>,
        val subjects: List<Subject>,
        val groups: List<Group>,
        val payments: List<Payment>,
    )

    val state: StateFlow<Reports.Report> = combine(
        classes.observeAll(),
        students.observeAll(),
        subjects.observeAll(),
        groups.observeAll(),
        payments.observeAll(),
        ::Sources,
    ).combine(_period) { sources, period -> sources.report(period) }
        // The heaviest derivation in the app — every class against every payment and every student,
        // re-run on each write anywhere — and it used to run on the main thread. See [Computation].
        .derive(this, computation, Reports.Report(_period.value))

    /** Reads the same period at another width, keeping where the tutor was looking. */
    fun setKind(kind: ReportPeriod.Kind) = _period.update { it.withKind(kind) }

    /** Steps one period back (−1) or forward (+1). All time ignores it — it has nowhere to go. */
    fun shift(by: Int) = _period.update { it.shifted(by) }

    /** Whether the export landed. One-shot, because a file written is not a state to be in. */
    enum class ExportEvent { OK, ERROR }

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
                documents.write(ref, ReportCsv.build(header, lines))
            }.isSuccess
            events.send(if (ok) ExportEvent.OK else ExportEvent.ERROR)
        }
    }

    /**
     * The report itself is [Reports.of]'s — what a report counts, how it is ordered and which sections
     * are worth showing are all statements about reports, and none of them is this class's to make. What
     * is left here is naming: two id→name tables the domain has no database to look up.
     */
    private fun Sources.report(period: ReportPeriod): Reports.Report = Reports.of(
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
    )
}
