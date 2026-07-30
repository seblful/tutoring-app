package com.seblful.klas.ui.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.domain.ReportCsv
import com.seblful.klas.domain.ReportPeriod
import com.seblful.klas.domain.Reports
import com.seblful.klas.format.formatHours
import com.seblful.klas.format.formatMonth
import com.seblful.klas.format.formatMonthName
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.HeroCard
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.Stat
import com.seblful.klas.ui.common.StatRow
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.valueOrNull
import com.seblful.klas.ui.common.lessonKindFill
import com.seblful.klas.ui.common.lessonKindLabel
import com.seblful.klas.ui.common.lessonsStat
import com.seblful.klas.ui.common.text

/**
 * The money screen: what the chosen period earned, where it came from, and who still owes.
 *
 * **The period is the frame**, so it stays pinned above the report and every income number below reads
 * inside it — an all-time total stops answering "how am I doing" the moment there is a second year of
 * history.
 *
 * **Each number is drawn in the one shape that says something the number does not**, and in plain text
 * otherwise. A picture on every row is not a chart, it is wallpaper: ten students at a tenth each would
 * be ten identical slivers, and a bar beside a debt invites the tutor to admire a proportion instead of
 * collecting it. So:
 * - **a column chart** where the order is *time* — a year's shape is a fact about the order its months
 *   come in, which sorting by size destroys and no list of amounts can show;
 * - **one stacked bar** where two parts make the whole (individual vs group), because the question
 *   there is how the whole divides, and two bars side by side hide that they are halves of one thing;
 * - **ranked bars** where several rows are compared with each other, each drawn against the *largest*
 *   row rather than against the total, so the picture is the ranking — and dropped entirely when a
 *   section has one row, which has nothing to be ranked against;
 * - **plain text** for the totals, the counts, the per-month amounts and the debts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onStudentClick: (Long) -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val reportState by viewModel.state.collectAsState()
    // The period is its own flow, so the navigator is live while the report behind it is still being
    // assembled — the frame is the thing the tutor steers this screen with, and it needs no data.
    val period by viewModel.period.collectAsState()
    val report = reportState.valueOrNull()
    val messages = rememberMessages()

    val exportOkMsg = stringResource(R.string.reports_export_success)
    val exportErrorMsg = stringResource(R.string.reports_export_error)
    // Nothing to say when the sheet actually opened — the sheet is the feedback, and the app cannot see
    // whether the tutor went on to send anything. This is only for the case where it could not open.
    LaunchedEffect(Unit) {
        viewModel.events.stream.collect { event ->
            when (event) {
                ReportsViewModel.ExportEvent.OK -> messages.show(exportOkMsg)
                ReportsViewModel.ExportEvent.SHARED -> Unit
                ReportsViewModel.ExportEvent.ERROR -> messages.show(exportErrorMsg)
            }
        }
    }

    // Assembled here, where the section headings are string resources and the rows are already in
    // the order the screen shows them — so the file reads like the report rather than like a dump.
    val csvHeader = listOf(
        stringResource(R.string.reports_csv_section),
        stringResource(R.string.reports_csv_label),
        stringResource(R.string.reports_csv_amount),
    )
    // Remembered: this walks every group, subject, student and debt row, and recomposition is not
    // a new report. Rebuilt only when the report itself changes.
    val csvHeadings = csvHeadings(period)
    val csvLines = remember(report, csvHeadings) {
        report?.let { ReportCsv.linesFor(it, csvHeadings) }.orEmpty()
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CSV_MIME_TYPE),
    ) { uri ->
        if (uri != null) viewModel.export(DocumentRef(uri.toString()), csvHeader, csvLines)
    }
    val exportFilename = stringResource(R.string.reports_export_filename)
    // Names the period, so a report arriving in somebody's mail says which month it is.
    val shareSubject = stringResource(R.string.reports_share_subject, periodTitle(period))

    KlasScreen(
        title = stringResource(R.string.reports_title),
        messages = messages,
        actions = {
            // A backup is JSON meant to be read back by this app. What a tutor totalling their
            // year — or an accountant — needs is a file that opens in a spreadsheet, and there was
            // nothing here that produced one.
            if (report?.hasData == true) {
                // **Send comes before save**, and it is the one that was missing. Saving a CSV puts it in
                // a folder the tutor then has to go and find in a second app; the thing they were
                // actually doing with a month is sending it — to an accountant, or to their own mail so
                // it is somewhere other than this phone. Both stay: a file kept and a file sent are
                // different acts, and the share sheet cannot put one on the SD card.
                IconButton(
                    onClick = {
                        viewModel.share(shareFilename(period), shareSubject, csvHeader, csvLines)
                    },
                ) {
                    Icon(Icons.Filled.Share, stringResource(R.string.reports_share))
                }
                IconButton(onClick = { exportLauncher.launch(exportFilename) }) {
                    Icon(
                        Icons.Filled.FileDownload,
                        stringResource(R.string.reports_export),
                    )
                }
            }
        },
    ) { modifier ->
        // «Нет данных» says the tutor has earned nothing, which is a claim about their year — so it
        // waits for the report to actually arrive. Before [Loadable] the initial value was an empty
        // report, and every cold open of this screen made that claim for a frame or two.
        if (report == null) {
            Column(modifier) { ReportsSkeleton() }
            return@KlasScreen
        }
        if (!report.hasData) {
            EmptyState(
                title = stringResource(R.string.reports_empty),
                detail = stringResource(R.string.reports_empty_detail),
                icon = Icons.Outlined.QueryStats,
                modifier = modifier,
            )
            return@KlasScreen
        }

        Column(modifier) {
            // Pinned rather than scrolled away with the first card: the period is what everything
            // below means, and changing it is the main thing this screen is driven by.
            PeriodNavigator(
                period = period,
                onPrevious = { viewModel.shift(-1) },
                onNext = { viewModel.shift(1) },
                onKind = viewModel::setKind,
            )
            HorizontalDivider()
            ReportBody(report, onStudentClick, Modifier.weight(1f))
        }
    }
}

/**
 * The report's shape before its numbers: the hero card, then the section cards under it.
 *
 * Sized like the real thing rather than as uniform rows — the income card is much taller than a
 * breakdown, and a skeleton that ignores that reflows the screen on the first real frame, which is the
 * jump it exists to prevent.
 */
@Composable
private fun ReportsSkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        com.seblful.klas.ui.common.SkeletonBlock(IncomeCardHeight)
        ListSkeleton(rows = 3, rowHeight = SectionCardHeight, contentPadding = PaddingValues(0.dp))
    }
}

/** Roughly `IncomeCard`: a label, a headline amount and the comparison line, at 20dp padding. */
private val IncomeCardHeight = 116.dp

/** Roughly a `ReportCard` with a title and two or three rows. */
private val SectionCardHeight = 132.dp

private const val CSV_MIME_TYPE = "text/csv"

/**
 * The section headings the export is labelled with — the words, and nothing else. Which rows the file
 * contains is [ReportCsv.linesFor]'s.
 */
@Composable
private fun csvHeadings(period: ReportPeriod): ReportCsv.Headings = ReportCsv.Headings(
    total = stringResource(R.string.reports_total_income),
    period = periodTitle(period),
    byGroup = stringResource(R.string.reports_by_group),
    bySubject = stringResource(R.string.reports_by_subject),
    byStudent = stringResource(R.string.reports_by_student),
    debts = stringResource(R.string.reports_debts),
    outstanding = stringResource(R.string.reports_total_outstanding),
)

@Composable
private fun ReportBody(
    state: Reports.Report,
    onStudentClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolved here (composable scope) so the LazyListScope blocks below stay string-free.
    val byGroupTitle = stringResource(R.string.reports_by_group)
    val bySubjectTitle = stringResource(R.string.reports_by_subject)
    val byStudentTitle = stringResource(R.string.reports_by_student)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        item { IncomeCard(state) }

        if (state.noIncome) {
            item {
                Text(
                    stringResource(R.string.reports_no_income),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item { StatRow(listOf(lessonsStat(state.taught), hoursStat(state.taught.minutes))) }
            // The one breakdown ordered by time rather than by size, so it is drawn as a timeline.
            if (!state.trend.isEmpty) item { TrendCard(state.trend) }
            // Where the money came from, by *kind* of teaching — the one split the per-student and
            // per-subject lines below cannot show, since both kinds are mixed inside each of them.
            if (state.byKind.isNotEmpty()) item { KindSplitCard(state.byKind) }
            rankedSection(byGroupTitle, state.byGroup)
            rankedSection(bySubjectTitle, state.bySubject)
            rankedSection(byStudentTitle, state.byStudent)
        }

        item { DebtsCard(state, onStudentClick) }
    }
}

private fun LazyListScope.rankedSection(title: String, lines: List<Reports.Line>) {
    if (lines.isEmpty()) return
    item { RankedCard(title, lines) }
}

/**
 * ‹ «2026 год» › — the period being read, with the two ways to change it: step to the one beside it,
 * or tap the title for another width. All time disables the arrows rather than hiding them, so the
 * row keeps its shape and the title keeps its place.
 */
@Composable
private fun PeriodNavigator(
    period: ReportPeriod,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onKind: (ReportPeriod.Kind) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.inline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, enabled = period.isNavigable) {
            Icon(Icons.Filled.ChevronLeft, stringResource(R.string.reports_previous_period))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            TextButton(onClick = { expanded = true }) {
                Text(
                    periodTitle(period),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Filled.ArrowDropDown, stringResource(R.string.reports_period))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ReportPeriod.Kind.entries.forEach { kind ->
                    DropdownMenuItem(
                        text = { Text(stringResource(periodKindLabel(kind))) },
                        onClick = { onKind(kind); expanded = false },
                        trailingIcon = {
                            // Only the active width is ticked; the others leave the column empty.
                            if (kind == period.kind) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                    )
                }
            }
        }
        IconButton(onClick = onNext, enabled = period.isNavigable) {
            Icon(Icons.Filled.ChevronRight, stringResource(R.string.reports_next_period))
        }
    }
}

/** The period's own name: «Июль 2026», «2026 год», «Всё время». */
@Composable
private fun periodTitle(period: ReportPeriod): String = when (period.kind) {
    ReportPeriod.Kind.MONTH -> formatMonth(period.anchor)
    ReportPeriod.Kind.YEAR -> stringResource(R.string.reports_period_year_title, period.anchor.year)
    ReportPeriod.Kind.ALL -> stringResource(R.string.reports_period_all)
}

/**
 * What a shared report is **called**: `klas-report-2026-06.csv`, `klas-report-2026.csv`,
 * `klas-report-all.csv`.
 *
 * The period is in the name because the file leaves the app. Every share was `klas-report.csv`, so an
 * accountant sent twelve months received twelve files with one name and no way to tell them apart — and
 * the subject line that does name the period belongs to the message rather than to the attachment.
 *
 * Not a string resource, and not localized: it is a filename, read by whatever the receiving app is, and
 * `klas-report-2026-06` sorts and is typed the same everywhere. The screen's own «Выгрузить в CSV» dialog
 * keeps the plain `reports_export_filename`, since the tutor is naming that one themselves.
 */
private fun shareFilename(period: ReportPeriod): String {
    val suffix = when (period.kind) {
        ReportPeriod.Kind.MONTH -> "%d-%02d".format(period.anchor.year, period.anchor.monthValue)
        ReportPeriod.Kind.YEAR -> period.anchor.year.toString()
        ReportPeriod.Kind.ALL -> "all"
    }
    return "klas-report-$suffix.csv"
}

private fun periodKindLabel(kind: ReportPeriod.Kind): Int = when (kind) {
    ReportPeriod.Kind.MONTH -> R.string.reports_period_month
    ReportPeriod.Kind.YEAR -> R.string.reports_period_year
    ReportPeriod.Kind.ALL -> R.string.reports_period_all
}

@Composable
private fun hoursStat(minutes: Int): Stat =
    Stat(label = stringResource(R.string.stat_hours), value = formatHours(minutes))

/**
 * The screen's answer, in the loudest colour it has: what the chosen period earned — and whether that
 * is more or less than last time.
 *
 * The comparison is the line that turns the number into an answer. This screen's whole purpose is
 * "how am I doing", and «45 000 BYN» on its own does not address it: without a baseline the biggest
 * figure on the screen is a fact the tutor still has to interpret. One signed percentage, naming the
 * period it is measured against, is the cheapest thing that closes the gap.
 *
 * Drawn as a [HeroCard], which is what makes the filled accent surface a device the system owns rather
 * than a flourish on one screen. Its only sibling is the student card's balance.
 */
@Composable
private fun IncomeCard(state: Reports.Report) {
    HeroCard(
        label = stringResource(R.string.reports_total_income),
        value = state.totalIncome.text(),
    ) {
        ComparisonLine(state)
    }
}

/** «+12 % к периоду "Июнь 2026"», or a note that there is nothing to compare against. */
@Composable
private fun ColumnScope.ComparisonLine(state: Reports.Report) {
    val change = state.change ?: return
    val previous = state.previousPeriod ?: return
    val previousName = periodTitle(previous)
    val text = when (change) {
        Reports.Change.NoBaseline -> stringResource(R.string.reports_vs_previous_from_zero, previousName)
        is Reports.Change.Percent -> stringResource(
            R.string.reports_vs_previous,
            signedPercent(change.value),
            previousName,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.tight)) {
        // An arrow only where there is a direction: "the previous period earned nothing" has none,
        // and an upward arrow beside it would be asserting growth the app just declined to measure.
        if (change is Reports.Change.Percent && change.value != 0) {
            Icon(
                // AutoMirrored: a trend arrow points *forward in time*, so it flips with the layout
                // direction. The non-mirrored pair is deprecated for exactly this reason.
                if (change.value > 0) {
                    Icons.AutoMirrored.Filled.TrendingUp
                } else {
                    Icons.AutoMirrored.Filled.TrendingDown
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Spacing.tight))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A percentage with its sign, using a real minus (−, U+2212) rather than a hyphen.
 *
 * The card's headline is set in a proportional face at 28sp; a hyphen beside it is a short mark at
 * roughly x-height and reads as a dash, not as "less than". Money is the one place on the screen
 * where the difference between −8 % and 8 % is the entire message.
 */
@Composable
private fun signedPercent(value: Int): String = when {
    value > 0 -> stringResource(R.string.reports_percent_signed_up, value)
    value < 0 -> stringResource(R.string.reports_percent_signed_down, -value)
    else -> stringResource(R.string.reports_percent_signed_same)
}

/**
 * The trend: bars for the shape, then the same buckets as rows for the exact amounts.
 *
 * Both, because they answer different questions — the chart says which part of the year was busy,
 * the rows say what a given month came to — and neither substitutes for the other at this width: a
 * bar narrow enough that twelve fit across a phone has no room for the number it stands for.
 */
@Composable
private fun TrendCard(trend: Reports.Trend) {
    val months = stringArrayResource(R.array.month_short_names)
    val isMonths = trend.bucket == Reports.Trend.Bucket.MONTHS
    ReportCard(stringResource(if (isMonths) R.string.reports_by_month else R.string.reports_by_year)) {
        IncomeTrendChart(trend) { bar -> if (isMonths) months[bar.monthIndex] else bar.key.toString() }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // `rows`, not `bars`: newest first and without the empty buckets, which are a gap in the chart
        // above and would only be a row of zeros here. Both halves of that are stated on [Reports.Trend].
        trend.rows.forEach { bar ->
            AmountRow(
                label = if (isMonths) formatMonthName(bar.key) else bar.key.toString(),
                amount = bar.total.text(),
            )
        }
    }
}

/**
 * One column per bucket, oldest on the left, each drawn as a fraction of the best one — so the chart
 * is about *relative* size and never needs a value axis. Every bucket keeps a faint full-height slot,
 * which is what makes a month with no lessons read as a gap in the year rather than as an absence the
 * neighbouring bars close over. The best bucket is the solid one.
 */
@Composable
private fun IncomeTrendChart(trend: Reports.Trend, labelOf: (Reports.Trend.Bar) -> String) {
    val peak = trend.peak ?: return
    val filled = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Row(
        Modifier.fillMaxWidth().height(ChartHeight),
        horizontalArrangement = Arrangement.spacedBy(BarGap),
    ) {
        trend.bars.forEach { bar ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxSize().clip(BarShape).background(track))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(trend.shareOf(bar))
                            .clip(BarShape)
                            .background(if (bar.key == peak.key) filled else filled.copy(alpha = 0.45f)),
                    )
                }
                Text(
                    labelOf(bar),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = AxisFontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.tight),
                )
            }
        }
    }
}

/**
 * The income as **one** bar of two segments, because individual and group teaching are the two halves
 * of the same whole and the question is how that whole divides. Two separate ranked bars would say
 * "63 %" and "38 %" of something the eye is left to reconstruct.
 */
@Composable
private fun KindSplitCard(lines: List<Reports.KindLine>) {
    ReportCard(stringResource(R.string.reports_by_kind)) {
        Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)) {
            lines.forEach { line ->
                Box(
                    Modifier
                        // Weighted by the money itself, so the bar *is* the split.
                        .weight(line.amount.minor.toFloat())
                        .fillMaxHeight()
                        .background(lessonKindFill(line.kind)),
                )
            }
        }
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(lessonKindFill(line.kind)))
                Spacer(Modifier.width(Spacing.inline))
                Text(
                    lessonKindLabel(line.kind),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.itemGap))
                Text(
                    stringResource(R.string.reports_amount_share, line.amount.text(), line.percent),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * A ranked breakdown. Each bar's fraction — against the section's largest line, absent for a
 * section of one — arrives on the line itself ([Reports.Line.share]): both rules are statements
 * about a report, and they are decided and tested where the report is.
 */
@Composable
private fun RankedCard(title: String, lines: List<Reports.Line>) {
    ReportCard(title) {
        lines.forEach { line ->
            Column(verticalArrangement = Arrangement.spacedBy(BarGap)) {
                AmountRow(line.label, line.amount.text(), color = MaterialTheme.colorScheme.primary)
                // The fraction and the single-line rule are the report's own — see [Reports.Line.share].
                line.share?.let { share ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RankedBar(share, Modifier.weight(1f))
                        // A percentage only where the rows genuinely partition the income.
                        line.percent?.let {
                            Spacer(Modifier.width(Spacing.inline))
                            Text(
                                stringResource(R.string.reports_percent, it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A track filled to [fraction] of the best line in its section. */
@Composable
private fun RankedBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * Who owes money — the one section the period does not touch (see [ReportsViewModel]), and the one
 * kept deliberately plain: a debt is a number to collect, and drawing it as a share of the arrears
 * would dress up the thing to act on as a statistic.
 */
@Composable
private fun DebtsCard(state: Reports.Report, onStudentClick: (Long) -> Unit) {
    ReportCard(stringResource(R.string.reports_debts)) {
        Text(
            stringResource(R.string.reports_debts_all_time),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.debts.isEmpty()) {
            Text(
                stringResource(R.string.reports_no_debts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ReportCard
        }
        AmountRow(
            label = stringResource(R.string.reports_total_outstanding),
            amount = state.totalOutstanding.text(),
            bold = true,
        )
        // Every row opens the person who owes. Reading a name here and then going to find them by
        // hand — through a list that could not be searched or sorted by debt — was the longest
        // journey in the app, between two screens that are one tap apart.
        state.debts.forEach { debt ->
            AmountRow(
                label = debt.label,
                amount = debt.amount.text(),
                color = MaterialTheme.colorScheme.error,
                onClick = debt.studentId?.let { id -> { onStudentClick(id) } },
            )
        }
    }
}

/**
 * A titled surface for one section of the report.
 *
 * A *section*, so it sits on `surfaceContainer` — one rung above the item cards elsewhere in the app
 * (see [com.seblful.klas.ui.common.SectionCard]). The title needs no explicit weight now that the
 * type scale states `titleSmall` as semi-bold like the titles above it.
 */
@Composable
private fun ReportCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionCard {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

/**
 * A label on the left, its amount on the right — the shape every number on this screen is read in.
 *
 * @param onClick makes the row a target. Only the debts pass one: those name a person to go and talk
 *   to, while «Июль · 12 400» names a month, which is not somewhere to be taken. A row that leads
 *   somewhere gets a chevron and a full-height touch target so it says so before it is tapped.
 */
@Composable
private fun AmountRow(
    label: String,
    amount: String,
    bold: Boolean = false,
    color: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onClick)
                        .heightIn(min = MinTouchTarget)
                        .padding(horizontal = Spacing.tight)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.itemGap))
        Text(
            amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = color,
        )
        if (onClick != null) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.reports_open_student),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * The hairline between a stacked bar's segments and between a ranked bar's rows — off the `Spacing`
 * scale on purpose: the gap is there so the bar still reads as **one shape**, and 4 dp between twelve
 * month columns would take a quarter of the chart's width.
 */
private val BarGap = 3.dp

/** Tall enough to tell a busy month from a quiet one, short enough to leave the list in view. */
private val ChartHeight = 116.dp
private val BarShape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
private val AxisFontSize = 9.sp
