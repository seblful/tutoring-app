package com.seblful.klas.ui.reports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.PaymentMethod
import com.seblful.klas.domain.reports.ReportCsv
import com.seblful.klas.domain.reports.ReportPeriod
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.format.money
import com.seblful.klas.print.Sheet
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.KlasMenu
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.common.MenuItem
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.common.paymentMethodLabel
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.theme.KlasIcons

@Composable
private fun csvHeadings(period: ReportPeriod, methods: Map<PaymentMethod?, String>) = ReportCsv.Headings(
    total = stringResource(Strings.reports_total_income),
    period = periodTitle(period),
    byGroup = stringResource(Strings.reports_by_group),
    bySubject = stringResource(Strings.reports_by_subject),
    byStudent = stringResource(Strings.reports_by_student),
    debts = stringResource(Strings.reports_debts),
    outstanding = stringResource(Strings.reports_total_outstanding),
    received = stringResource(Strings.reports_received),
    methods = methods,
)

/** The files a report leaves as, for the report [exportable] — null while there is none to send. */
internal class ReportFiles(
    val summary: ReportsViewModel.ReportFile,
    val ledger: ReportsViewModel.ReportFile,
    val pdf: ReportsViewModel.ReportFile,
)

/**
 * Assembled here, where the headings are string resources and the rows already in the order the screen
 * shows them — so the summary reads like the report rather than like a dump. Remembered: it walks every
 * row of the report, and recomposition is not a new report.
 */
@Composable
internal fun rememberReportFiles(exportable: Reports.Report?, period: ReportPeriod, scopeName: String): ReportFiles? {
    val methods = (PaymentMethod.entries + null).associateWith { method ->
        method?.let { paymentMethodLabel(it) } ?: stringResource(Strings.payment_method_none)
    }
    val summaryHeader = listOf(
        stringResource(Strings.reports_csv_section),
        stringResource(Strings.reports_csv_label),
        stringResource(Strings.reports_csv_amount),
    )
    val ledgerHeader = listOf(
        stringResource(Strings.reports_ledger_day),
        stringResource(Strings.reports_ledger_entry),
        stringResource(Strings.reports_ledger_student),
        stringResource(Strings.reports_ledger_detail),
        stringResource(Strings.reports_csv_amount),
    )
    val words = ReportCsv.LedgerWords(
        lesson = stringResource(Strings.reports_ledger_lesson),
        payment = stringResource(Strings.reports_ledger_payment),
        methods = methods,
    )
    val headings = csvHeadings(period, methods)
    val title = stringResource(Strings.reports_share_subject, periodTitle(period))
    val currency = LocalCurrency.current
    return remember(exportable, headings, words, title, scopeName, currency) {
        exportable?.let { report ->
            val lines = ReportCsv.linesFor(report, headings)
            ReportFiles(
                summary = ReportsViewModel.ReportFile.Summary(summaryHeader, lines),
                ledger = ReportsViewModel.ReportFile.Ledger(ledgerHeader, report, words),
                pdf = ReportsViewModel.ReportFile.Pdf(reportSheet(title, scopeName, lines, currency)),
            )
        }
    }
}

/** The summary printed: the CSV's lines under their sections, amounts in [currency], narrowed to [scopeName]. */
fun reportSheet(title: String, scopeName: String, lines: List<ReportCsv.Line>, currency: Currency) = Sheet(
    title = title,
    subtitle = scopeName,
    sections = lines.groupBy { it.section }.map { (section, rows) ->
        Sheet.Section(section, rows.map { Sheet.Row(it.label, it.amount.money(currency)) })
    },
)

/**
 * [ExportMenu] over [files]: each sent under the period's own name ([ReportCsv.fileName]), or saved under
 * a plain one the tutor renames in the picker. Taps before the report has arrived send nothing.
 */
@Composable
internal fun ReportExportMenu(
    files: ReportFiles?,
    period: ReportPeriod,
    onShare: (name: String, file: ReportsViewModel.ReportFile) -> Unit,
    onSave: (name: String, file: ReportsViewModel.ReportFile) -> Unit,
) {
    val summaryName = stringResource(Strings.reports_export_filename)
    val ledgerName = stringResource(Strings.reports_ledger_filename)
    val pdfName = stringResource(Strings.reports_pdf_filename)
    ExportMenu(
        onShare = { files?.let { onShare(ReportCsv.fileName(period), it.summary) } },
        onSave = { files?.let { onSave(summaryName, it.summary) } },
        onShareLedger = { files?.let { onShare(ReportCsv.ledgerFileName(period), it.ledger) } },
        onSaveLedger = { files?.let { onSave(ledgerName, it.ledger) } },
        onSharePdf = { files?.let { onShare(ReportCsv.pdfFileName(period), it.pdf) } },
        onSavePdf = { files?.let { onSave(pdfName, it.pdf) } },
    )
}

/** «⤓» and the two ways a report leaves the phone: sent, or saved as a file. */
@Composable
fun ExportMenu(
    onShare: () -> Unit,
    onSave: () -> Unit,
    onShareLedger: () -> Unit = {},
    onSaveLedger: () -> Unit = {},
    onSharePdf: () -> Unit = {},
    onSavePdf: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Box {
        NamedIconButton(KlasIcons.Filled.Download, stringResource(Strings.reports_export_menu), { open = true })
        KlasMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem(
                text = { Text(stringResource(Strings.reports_share)) },
                leadingIcon = { Icon(KlasIcons.Outlined.Share, contentDescription = null) },
                onClick = { open = false; onShare() },
            )
            MenuItem(
                text = { Text(stringResource(Strings.reports_export)) },
                leadingIcon = { Icon(KlasIcons.Filled.Download, contentDescription = null) },
                onClick = { open = false; onSave() },
            )
            // Every lesson and payment, a row each — what an accountant reconciles, beside the summary a
            // tutor reads.
            MenuItem(
                text = { Text(stringResource(Strings.reports_share_ledger)) },
                leadingIcon = { Icon(KlasIcons.Outlined.Share, contentDescription = null) },
                onClick = { open = false; onShareLedger() },
            )
            MenuItem(
                text = { Text(stringResource(Strings.reports_export_ledger)) },
                leadingIcon = { Icon(KlasIcons.Filled.Download, contentDescription = null) },
                onClick = { open = false; onSaveLedger() },
            )
            // The summary as a page to read — for someone who will not open a spreadsheet.
            MenuItem(
                text = { Text(stringResource(Strings.reports_share_pdf)) },
                leadingIcon = { Icon(KlasIcons.Outlined.Share, contentDescription = null) },
                onClick = { open = false; onSharePdf() },
            )
            MenuItem(
                text = { Text(stringResource(Strings.reports_export_pdf)) },
                leadingIcon = { Icon(KlasIcons.Filled.Download, contentDescription = null) },
                onClick = { open = false; onSavePdf() },
            )
        }
    }
}
