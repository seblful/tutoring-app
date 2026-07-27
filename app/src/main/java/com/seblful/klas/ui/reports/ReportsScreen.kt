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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.Stat
import com.seblful.klas.ui.common.StatRow
import com.seblful.klas.ui.common.constrainedContent
import com.seblful.klas.ui.common.rememberMessages
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
    val state by viewModel.state.collectAsState()
    val messages = rememberMessages()

    val exportOkMsg = stringResource(R.string.reports_export_success)
    val exportErrorMsg = stringResource(R.string.reports_export_error)
    LaunchedEffect(Unit) {
        viewModel.events.stream.collect { event ->
            messages.show(
                when (event) {
                    ReportsViewModel.ExportEvent.OK -> exportOkMsg
                    ReportsViewModel.ExportEvent.ERROR -> exportErrorMsg
                }
            )
        }
    }

    // Assembled here, where the section headings are string resources and the rows are already in
    // the order the screen shows them — so the file reads like the report rather than like a dump.
    val csvHeader = listOf(
        stringResource(R.string.reports_csv_section),
        stringResource(R.string.reports_csv_label),
        stringResource(R.string.reports_csv_amount),
    )
    val csvLines = csvLinesFor(state)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CSV_MIME_TYPE),
    ) { uri ->
        if (uri != null) viewModel.export(DocumentRef(uri.toString()), csvHeader, csvLines)
    }
    val exportFilename = stringResource(R.string.reports_export_filename)

    Scaffold(
        snackbarHost = { SnackbarHost(messages.hostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_title)) },
                actions = {
                    // A backup is JSON meant to be read back by this app. What a tutor totalling
                    // their year — or an accountant — needs is a file that opens in a spreadsheet,
                    // and there was nothing here that produced one.
                    if (state.hasData) {
                        IconButton(onClick = { exportLauncher.launch(exportFilename) }) {
                            Icon(
                                Icons.Filled.FileDownload,
                                stringResource(R.string.reports_export),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.hasData) {
            EmptyState(
                title = stringResource(R.string.reports_empty),
                detail = stringResource(R.string.reports_empty_detail),
                icon = Icons.Filled.QueryStats,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding).constrainedContent()) {
            // Pinned rather than scrolled away with the first card: the period is what everything
            // below means, and changing it is the main thing this screen is driven by.
            PeriodNavigator(
                period = state.period,
                onPrevious = { viewModel.shift(-1) },
                onNext = { viewModel.shift(1) },
                onKind = viewModel::setKind,
            )
            HorizontalDivider()
            ReportBody(state, onStudentClick, Modifier.weight(1f))
        }
    }
}

private const val CSV_MIME_TYPE = "text/csv"

/**
 * The report as spreadsheet rows, in screen order.
 *
 * Debts are included even though they ignore the period — the file is what the tutor takes away, and
 * leaving out the one section that says who owes money would make it the least useful export
 * possible. Its heading says so, as the card on screen does.
 */
@Composable
private fun csvLinesFor(state: ReportsState): List<ReportCsv.Line> {
    val totalTitle = stringResource(R.string.reports_total_income)
    val byGroupTitle = stringResource(R.string.reports_by_group)
    val bySubjectTitle = stringResource(R.string.reports_by_subject)
    val byStudentTitle = stringResource(R.string.reports_by_student)
    val debtsTitle = stringResource(R.string.reports_debts)
    val outstandingTitle = stringResource(R.string.reports_total_outstanding)
    val periodName = periodTitle(state.period)

    return buildList {
        add(ReportCsv.Line(totalTitle, periodName, state.totalIncome))
        fun section(title: String, lines: List<LabeledAmount>) {
            lines.forEach { add(ReportCsv.Line(title, it.label, it.amount)) }
        }
        section(byGroupTitle, state.byGroup)
        section(bySubjectTitle, state.bySubject)
        section(byStudentTitle, state.byStudent)
        if (state.debts.isNotEmpty()) {
            add(ReportCsv.Line(debtsTitle, outstandingTitle, state.totalOutstanding))
            section(debtsTitle, state.debts)
        }
    }
}

@Composable
private fun ReportBody(
    state: ReportsState,
    onStudentClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolved here (composable scope) so the LazyListScope blocks below stay string-free.
    val byGroupTitle = stringResource(R.string.reports_by_group)
    val bySubjectTitle = stringResource(R.string.reports_by_subject)
    val byStudentTitle = stringResource(R.string.reports_by_student)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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

private fun LazyListScope.rankedSection(title: String, lines: List<LabeledAmount>) {
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
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
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
 */
@Composable
private fun IncomeCard(state: ReportsState) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.reports_total_income), style = MaterialTheme.typography.labelMedium)
            Text(state.totalIncome.text(), style = MaterialTheme.typography.headlineMedium)
            ComparisonLine(state)
        }
    }
}

/** «+12 % к периоду "Июнь 2026"», or a note that there is nothing to compare against. */
@Composable
private fun ColumnScope.ComparisonLine(state: ReportsState) {
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        // An arrow only where there is a direction: "the previous period earned nothing" has none,
        // and an upward arrow beside it would be asserting growth the app just declined to measure.
        if (change is Reports.Change.Percent && change.value != 0) {
            Icon(
                if (change.value > 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
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
        IncomeTrendChart(trend) { key -> if (isMonths) months[key % 12] else key.toString() }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Newest first, matching every other list in the app; empty buckets are a gap in the chart
        // above and would only be a row of zeros here.
        trend.bars.asReversed().filterNot { it.total.isZero }.forEach { bar ->
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
private fun IncomeTrendChart(trend: Reports.Trend, labelOf: (Int) -> String) {
    val peak = trend.peak ?: return
    val filled = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Row(
        Modifier.fillMaxWidth().height(ChartHeight),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        trend.bars.forEach { bar ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxSize().clip(BarShape).background(track))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(bar.total.minor.toFloat() / peak.total.minor)
                            .clip(BarShape)
                            .background(if (bar.key == peak.key) filled else filled.copy(alpha = 0.45f)),
                    )
                }
                Text(
                    labelOf(bar.key),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = AxisFontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
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
private fun KindSplitCard(lines: List<KindAmount>) {
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
                Spacer(Modifier.width(8.dp))
                Text(
                    lessonKindLabel(line.kind),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
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
 * A ranked breakdown. The bar is drawn against the **largest** line rather than the total, so it
 * pictures the ranking the rows are already in — scaled to the total, a long tail of students would be
 * a column of near-empty tracks saying nothing. One line alone gets no bar: there is nothing to rank it
 * against, and a bar filled to the brim would only assert that the only row is the biggest one.
 */
@Composable
private fun RankedCard(title: String, lines: List<LabeledAmount>) {
    val top = lines.first().amount.minor.coerceAtLeast(1)
    ReportCard(title) {
        lines.forEach { line ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                AmountRow(line.label, line.amount.text(), color = MaterialTheme.colorScheme.primary)
                if (lines.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RankedBar(line.amount.minor.toFloat() / top, Modifier.weight(1f))
                        // A percentage only where the rows genuinely partition the income.
                        line.percent?.let {
                            Spacer(Modifier.width(8.dp))
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
private fun DebtsCard(state: ReportsState, onStudentClick: (Long) -> Unit) {
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
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        .padding(horizontal = 4.dp)
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
        Spacer(Modifier.width(12.dp))
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

/** Tall enough to tell a busy month from a quiet one, short enough to leave the list in view. */
private val ChartHeight = 116.dp
private val BarShape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
private val AxisFontSize = 9.sp
