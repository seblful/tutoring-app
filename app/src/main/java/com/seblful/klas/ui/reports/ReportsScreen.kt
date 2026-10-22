package com.seblful.klas.ui.reports

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.reports.ReportCsv
import com.seblful.klas.domain.reports.ReportPeriod
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.format.formatMonth
import com.seblful.klas.format.formatMonthName
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.HeroCard
import com.seblful.klas.ui.common.KlasMenu
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.MaxContentWidth
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.common.PageStepper
import com.seblful.klas.ui.common.ScreenHeader
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.StatRow
import com.seblful.klas.ui.common.SupportingPaneMaxWidth
import com.seblful.klas.ui.common.TickedMenuItem
import com.seblful.klas.ui.common.constrainedContent
import com.seblful.klas.ui.common.hoursStat
import com.seblful.klas.ui.common.lessonKindFill
import com.seblful.klas.ui.common.lessonKindLabel
import com.seblful.klas.ui.common.lessonsStat
import com.seblful.klas.ui.common.outsetRowPress
import com.seblful.klas.ui.common.plusInset
import com.seblful.klas.ui.common.rememberCreateDocument
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.rememberSkeletonPulse
import com.seblful.klas.ui.common.roomForSupportingPane
import com.seblful.klas.ui.common.sectionSurface
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.navigation.Arrival
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.KlasMotion
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** The money screen: what the chosen period earned, where it came from, and who still owes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onStudentClick: (Long) -> Unit,
    /** The debt digest's latest tap, which scrolls to the debts once — see `AppRoot`. */
    debtsArrival: Arrival<Unit>? = null,
    viewModel: ReportsViewModel = metroViewModel(),
) {
    val listState = rememberLazyListState()
    // The debts are the last card, below every breakdown — on a busy year, several screens down. There is
    // no list to scroll until the report has arrived, so this waits for one to have been laid out; and the
    // tap is recorded only once the scroll has run, so leaving the tab while the report is still being
    // assembled leaves it owed rather than spent on a scroll that never happened.
    val reportState by viewModel.state.collectAsStateWithLifecycle()
    // The period is its own flow, so the navigator is live while the report behind it is still being
    // assembled — the frame is the thing the tutor steers this screen with, and it needs no data. What is
    // drawn and what may be sent differ for that frame or two: see [ReportView].
    val period by viewModel.period.collectAsStateWithLifecycle()
    val (report, exportable) = reportState.viewFor(period)
    val shown by rememberUpdatedState(report)
    LaunchedEffect(debtsArrival) {
        if (!viewModel.owesDebts(debtsArrival)) return@LaunchedEffect
        val items = snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        // The debts card is the list's last items, its heading first among them.
        listState.scrollToItem((items - (shown?.let(::debtsItemCount) ?: 1)).coerceAtLeast(0))
        viewModel.debtsScrolled(debtsArrival)
    }
    val messages = rememberMessages()
    val noPickerMsg = stringResource(R.string.student_contact_failed)

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
    val csvLines = remember(exportable, csvHeadings) {
        exportable?.let { ReportCsv.linesFor(it, csvHeadings) }.orEmpty()
    }
    val export = rememberCreateDocument(CSV_MIME_TYPE, noPickerMsg, messages) { document ->
        viewModel.export(document, csvHeader, csvLines)
    }
    val exportFilename = stringResource(R.string.reports_export_filename)
    // Names the period, so a report arriving in somebody's mail says which month it is.
    val shareSubject = stringResource(R.string.reports_share_subject, periodTitle(period))

    val twoPane = roomForSupportingPane()
    KlasScreen(
        title = stringResource(R.string.reports_title),
        messages = messages,
        maxContentWidth = if (twoPane) SupportingPaneMaxWidth else MaxContentWidth,
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
                // Kept on screen while a new period assembles, so they don't blink with every arrow tap;
                // a tap inside that frame or two sends nothing rather than the previous period's file.
                NamedIconButton(
                    KlasIcons.Outlined.Share,
                    stringResource(R.string.reports_share),
                    onClick = {
                        if (exportable != null) {
                            viewModel.share(ReportCsv.fileName(period), shareSubject, csvHeader, csvLines)
                        }
                    },
                )
                NamedIconButton(
                    KlasIcons.Filled.Download,
                    stringResource(R.string.reports_export),
                    onClick = {
                        if (exportable != null) export(exportFilename)
                    },
                )
            }
        },
    ) { modifier, bottomInset ->
        // «Нет данных» says the tutor has earned nothing, which is a claim about their year — so it
        // waits for the report to actually arrive. Before [Loadable] the initial value was an empty
        // report, and every cold open of this screen made that claim for a frame or two.
        if (report == null) {
            Column(modifier.padding(bottomInset)) { ReportsSkeleton() }
            return@KlasScreen
        }
        if (!report.hasData) {
            EmptyState(
                title = stringResource(R.string.reports_empty),
                detail = stringResource(R.string.reports_empty_detail),
                icon = KlasIcons.Outlined.QueryStats,
                modifier = modifier.padding(bottomInset),
            )
            return@KlasScreen
        }

        Column(modifier) {
            // Pinned rather than scrolled away with the first card: the period is what everything
            // below means, and changing it is the main thing this screen is driven by.
            // Painted with the bar rather than ruled off under it — see [ScreenHeader].
            ScreenHeader {
                // At a column's width even beside two panes: its arrows belong either side of the title.
                Box(Modifier.fillMaxWidth().constrainedContent()) {
                    PeriodNavigator(
                        period = period,
                        onPrevious = { viewModel.shift(-1) },
                        onNext = { viewModel.shift(1) },
                        onKind = viewModel::setKind,
                    )
                }
            }
            ReportBody(report, onStudentClick, listState, bottomInset, twoPane, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReportsSkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        val pulse = rememberSkeletonPulse()
        com.seblful.klas.ui.common.SkeletonBlock(IncomeCardHeight, pulse = pulse)
        ListSkeleton(rows = 3, rowHeight = SectionCardHeight, contentPadding = PaddingValues(0.dp), pulse = pulse)
    }
}

private val IncomeCardHeight = 116.dp

private val SectionCardHeight = 132.dp

private const val CSV_MIME_TYPE = "text/csv"

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

/**
 * The report as one list, or on an expanded window as Material's supporting pane: what the period came to
 * (the income, its stats and shape) beside where it came from and who still owes, each scrolling alone.
 *
 * @param listState the list holding the breakdowns and debts, the one the debt digest scrolls.
 */
@Composable
private fun ReportBody(
    state: Reports.Report,
    onStudentClick: (Long) -> Unit,
    listState: LazyListState,
    bottomInset: PaddingValues,
    twoPane: Boolean,
    modifier: Modifier = Modifier,
) {
    val titles = BreakdownTitles(
        byGroup = stringResource(R.string.reports_by_group),
        bySubject = stringResource(R.string.reports_by_subject),
        byStudent = stringResource(R.string.reports_by_student),
    )
    val padding = PaddingValues(Spacing.gutter).plusInset(bottomInset)

    // No arrangement: a card's rows are list items of their own (see [CardSlice]), so the gap between
    // cards is each card's own, and its rows sit as close as they did inside one card.
    if (!twoPane) {
        LazyColumn(modifier = modifier.fillMaxWidth(), state = listState, contentPadding = padding) {
            summaryItems(state)
            breakdownItems(state, titles, onStudentClick)
        }
        return
    }
    Row(modifier.fillMaxWidth()) {
        LazyColumn(Modifier.weight(1f), contentPadding = padding) { summaryItems(state) }
        // Its first card's own gap above, taken off the top, so the two panes' first cards line up.
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            // The summary's gutter and this make Material's 24 dp between panes.
            contentPadding = PaddingValues(
                start = Spacing.sectionGap - Spacing.gutter,
                top = Spacing.gutter - Spacing.itemGap,
                end = Spacing.gutter,
            ).plusInset(bottomInset).plusInset(PaddingValues(bottom = Spacing.gutter)),
        ) { breakdownItems(state, titles, onStudentClick) }
    }
}

private class BreakdownTitles(val byGroup: String, val bySubject: String, val byStudent: String)

/** What the period came to: the income, then — if there was any — its stats, its shape and its kinds. */
private fun LazyListScope.summaryItems(state: Reports.Report) {
    item(key = "income", contentType = "income") { IncomeCard(state) }
    if (state.noIncome) {
        standalone("no-income") {
            Text(
                stringResource(R.string.reports_no_income),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    standalone("stats") { StatRow(listOf(lessonsStat(state.taught), hoursStat(state.taught.minutes))) }
    // The one breakdown ordered by time rather than by size, so it is drawn as a timeline.
    if (!state.trend.isEmpty) standalone("trend") { TrendCard(state.trend) }
    // Where the money came from, by *kind* of teaching — the one split the per-student and
    // per-subject lines below cannot show, since both kinds are mixed inside each of them.
    if (state.byKind.isNotEmpty()) standalone("kinds") { KindSplitCard(state.byKind) }
}

/** Where it came from, ranked, and who still owes — the debts last, where the digest's tap scrolls to. */
private fun LazyListScope.breakdownItems(
    state: Reports.Report,
    titles: BreakdownTitles,
    onStudentClick: (Long) -> Unit,
) {
    if (!state.noIncome) {
        rankedSection("group", titles.byGroup, state.byGroup)
        rankedSection("subject", titles.bySubject, state.bySubject)
        rankedSection("student", titles.byStudent, state.byStudent)
    }
    // Last, and it has to stay last: the debt digest's tap scrolls to it — see [debtsItemCount].
    debtsSection(state, onStudentClick)
}

/** An item standing on its own, [Spacing.itemGap] below the one before it — its [key] is its kind too. */
private fun LazyListScope.standalone(key: String, content: @Composable () -> Unit) {
    item(key = key, contentType = key) { Box(Modifier.padding(top = Spacing.itemGap)) { content() } }
}

/**
 * A ranked breakdown, one list item per line: a year's per-student section can run to hundreds, and as a
 * single card it was composed whole in the frame it scrolled into view.
 */
private fun LazyListScope.rankedSection(section: String, title: String, lines: List<Reports.Line>) {
    if (lines.isEmpty()) return
    item(key = "$section/heading", contentType = CARD_HEADING) { CardSlice(first = true) { CardHeading(title) } }
    itemsIndexed(lines, key = { _, line -> "$section/${line.id}" }, contentType = { _, _ -> "ranked" }) { index, line ->
        CardSlice(last = index == lines.lastIndex, gapAbove = Spacing.itemGap) { RankedLine(line) }
    }
}

/** How many items [debtsSection] adds: its heading, then the «нет долгов» line or the total and each debt. */
private fun debtsItemCount(state: Reports.Report): Int =
    1 + if (state.debts.isEmpty()) 1 else 1 + state.debts.size

private const val CARD_HEADING = "heading"

@Composable
private fun PeriodNavigator(
    period: ReportPeriod,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onKind: (ReportPeriod.Kind) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    PageStepper(
        previousDescription = stringResource(R.string.reports_previous_period),
        nextDescription = stringResource(R.string.reports_next_period),
        onPrevious = onPrevious,
        onNext = onNext,
        enabled = period.isNavigable,
    ) {
        Box {
            // Ink like the schedule's page title: the same control in both places, the arrow saying it opens.
            TextButton(
                onClick = { expanded = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                Text(
                    periodTitle(period),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(KlasIcons.Filled.ArrowDropDown, stringResource(R.string.reports_period))
            }
            KlasMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ReportPeriod.Kind.entries.forEach { kind ->
                    TickedMenuItem(
                        text = stringResource(periodKindLabel(kind)),
                        selected = kind == period.kind,
                        onClick = { onKind(kind); expanded = false },
                    )
                }
            }
        }
    }
}

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
private fun IncomeCard(state: Reports.Report) {
    HeroCard(
        label = stringResource(R.string.reports_total_income),
        value = state.totalIncome.text(),
    ) {
        ComparisonLine(state)
        Text(
            stringResource(R.string.reports_earned_hint),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ColumnScope.ComparisonLine(state: Reports.Report) {
    val change = state.change ?: return
    val previous = state.previousPeriod ?: return
    val previousName = periodTitle(previous)
    // While the period is still going the comparison is over the days it has had — and says so, or
    // «+12 % к периоду «2025»» would read as the whole of last year.
    val toDate = state.changeToDate
    val text = when (change) {
        Reports.Change.NoBaseline -> stringResource(
            if (toDate) R.string.reports_vs_previous_from_zero_to_date else R.string.reports_vs_previous_from_zero,
            previousName,
        )
        is Reports.Change.Percent -> stringResource(
            if (toDate) R.string.reports_vs_previous_to_date else R.string.reports_vs_previous,
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
                    KlasIcons.Filled.TrendingUp
                } else {
                    KlasIcons.Filled.TrendingDown
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

@Composable
private fun signedPercent(value: Int): String = when {
    value > 0 -> stringResource(R.string.reports_percent_signed_up, value)
    value < 0 -> stringResource(R.string.reports_percent_signed_down, -value)
    else -> stringResource(R.string.reports_percent_signed_same)
}

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

@Composable
private fun IncomeTrendChart(trend: Reports.Trend, labelOf: (Reports.Trend.Bar) -> String) {
    val peak = trend.peak ?: return
    val filled = MaterialTheme.colorScheme.primary
    val others = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    // Only the bars' tops are rounded: they stand on the axis.
    val barShape = MaterialTheme.shapes.extraSmall.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0))
    Row(
        // Not read: twelve bare month names and no amounts. The rows under the chart are its text, with
        // the amounts, and a screen reader reads those instead.
        Modifier.fillMaxWidth().height(ChartHeight).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(BarGap),
    ) {
        trend.bars.forEach { bar ->
            // Against the peak already in hand: [Reports.Trend.shareOf] would find it again for every bar.
            val share = animateBarShare(bar.total.minor.toFloat() / peak.total.minor)
            // The peak in the accent, the rest in the secondary role: both stand 3:1 off the track.
            val fill = if (bar.key == peak.key) filled else others
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                // The track and its fill on one node, with the outlines kept until the bar is resized or moves.
                Spacer(
                    Modifier.fillMaxWidth().weight(1f).drawWithCache {
                        val trackOutline = barShape.createOutline(size, layoutDirection, this)
                        val height = (size.height * share.value.coerceIn(0f, 1f)).roundToInt().toFloat()
                        val fillOutline = barShape.createOutline(Size(size.width, height), layoutDirection, this)
                        onDrawBehind {
                            drawOutline(trackOutline, track)
                            if (height > 0f) translate(top = size.height - height) { drawOutline(fillOutline, fill) }
                        }
                    },
                )
                // Shrinks rather than clipping: a twelfth of a phone at a large font scale cut «сен» to «се».
                Text(
                    labelOf(bar),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = with(LocalDensity.current) { AxisMinSize.toSp() },
                        maxFontSize = MaterialTheme.typography.labelSmall.fontSize,
                    ),
                    modifier = Modifier.padding(top = Spacing.tight),
                )
            }
        }
    }
}

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
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun RankedLine(line: Reports.Line) {
    Column(verticalArrangement = Arrangement.spacedBy(BarGap)) {
        // Ink, like every other amount on the screen that is not a debt. Colour on this screen
        // means something — the hero's fill, a debt's red — and primary here meant nothing the
        // primary bar under it was not already saying.
        AmountRow(line.label, line.amount.text())
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

/** Drawn on one node: it is in every ranked row, and a section can run to hundreds of them. */
@Composable
internal fun RankedBar(fraction: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val fill = MaterialTheme.colorScheme.primary
    val shown = animateBarShare(fraction)
    Spacer(
        modifier.height(6.dp).drawBehind {
            drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
            val filled = Size((size.width * shown.value.coerceIn(0f, 1f)).roundToInt().toFloat(), size.height)
            drawRoundRect(fill, size = filled, cornerRadius = CornerRadius(filled.minDimension / 2))
        },
    )
}

/**
 * A bar's share of its track as drawn: a new period's grows or shrinks out of the last one's rather than
 * replacing it, its spring clamped at the track's ends by whoever draws it. Read it in the draw.
 */
@Composable
private fun animateBarShare(share: Float): State<Float> =
    animateFloatAsState(share, KlasMotion.defaultSpatial(), label = "bar share")

private fun LazyListScope.debtsSection(state: Reports.Report, onStudentClick: (Long) -> Unit) {
    // «Текущий баланс, за всё время» belongs to the title: it says why this card ignores the period. As
    // the card's first row it sat a full item gap below the heading and read as a caption to the total.
    item(key = "debt/heading", contentType = CARD_HEADING) {
        CardSlice(first = true) {
            CardHeading(stringResource(R.string.reports_debts), stringResource(R.string.reports_debts_all_time))
        }
    }
    if (state.debts.isEmpty()) {
        item(key = "debt/none", contentType = "debt-none") {
            CardSlice(last = true, gapAbove = Spacing.itemGap) {
                Text(
                    stringResource(R.string.reports_no_debts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    item(key = "debt/total", contentType = "debt-total") {
        CardSlice(gapAbove = Spacing.itemGap) {
            AmountRow(
                label = stringResource(R.string.reports_total_outstanding),
                amount = state.totalOutstanding.text(),
                bold = true,
                // In the column the debts below it are in, which their chevron pulls inwards.
                alignWithLinks = true,
            )
        }
    }
    // Every row opens the person who owes. Reading a name here and then going to find them by
    // hand — through a list that could not be searched or sorted by debt — was the longest
    // journey in the app, between two screens that are one tap apart.
    //
    // No gap between the rows: each is already a full touch target, and an item gap on top of that spaced
    // the debts twice as far apart as any other rows on the screen.
    itemsIndexed(state.debts, key = { _, debt -> "debt/${debt.id}" }, contentType = { _, _ -> "debt" }) { index, debt ->
        CardSlice(last = index == state.debts.lastIndex, gapAbove = if (index == 0) Spacing.itemGap else 0.dp) {
            AmountRow(
                label = debt.label,
                amount = debt.amount.text(),
                color = MaterialTheme.colorScheme.error,
                onClick = debt.studentId?.let { id -> { onStudentClick(id) } },
            )
        }
    }
}

@Composable
private fun ReportCard(
    title: String,
    /** A line qualifying the title, set tight under it — part of the heading, not the first row. */
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            CardHeading(title, subtitle)
            content()
        }
    }
}

@Composable
private fun CardHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        SectionTitle(title)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One list item's share of a card drawn across several: the [first] rounds the card's top and stands
 * [Spacing.itemGap] below the item before it, the [last] rounds its bottom, and [gapAbove] is the room
 * the card's own column would have left above this row.
 */
@Composable
private fun CardSlice(
    first: Boolean = false,
    last: Boolean = false,
    gapAbove: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val card = MaterialTheme.shapes.medium
    val square = CornerSize(0.dp)
    val shape = card.copy(
        topStart = if (first) card.topStart else square,
        topEnd = if (first) card.topEnd else square,
        bottomStart = if (last) card.bottomStart else square,
        bottomEnd = if (last) card.bottomEnd else square,
    )
    // The surface [SectionCard] is, so a sliced card reads as the cards beside it — painted rather than a
    // `Surface`, which added a node, a clip layer and a semantics group to every row of the report. Nothing
    // inside needs the clip: a debt's press is clipped to its own shape, inset from the card's edge.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = if (first) Spacing.itemGap else 0.dp)
                .background(MaterialTheme.colorScheme.sectionSurface, shape)
                .padding(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    top = if (first) Spacing.gutter else gapAbove,
                    bottom = if (last) Spacing.gutter else 0.dp,
                ),
            content = content,
        )
    }
}

/**
 * A label on the left, its amount on the right — the shape every number on this screen is read in.
 *
 * @param onClick makes the row a target. Only the debts pass one: those name a person to go and talk
 * to, while «Июль · 12 400» names a month, which is not somewhere to be taken. A row that leads
 * somewhere gets a chevron and a full-height touch target so it says so before it is tapped.
 */
@Composable
private fun AmountRow(
    label: String,
    amount: String,
    bold: Boolean = false,
    color: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    /** Leaves the room a linked row's chevron and inset take, so a total lines up over the rows it sums. */
    alignWithLinks: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                when {
                    // The ripple needs room either side of the text, and taking it from inside the card
                    // put the debts' names a step to the right of every other label on the screen. It is
                    // taken from the card's padding instead, so the text stays on the shared edge.
                    onClick != null ->
                        Modifier
                            .outsetRowPress { Modifier.clickable(it, indication = null, onClick = onClick) }
                            .heightIn(min = MinTouchTarget)
                    else -> Modifier
                },
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
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            color = color,
        )
        if (onClick != null) {
            Icon(
                KlasIcons.Filled.ChevronRight,
                contentDescription = stringResource(R.string.reports_open_student),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ChevronSize),
            )
        } else if (alignWithLinks) {
            Spacer(Modifier.width(ChevronSize))
        }
    }
}

private val BarGap = Spacing.tight

private val ChartHeight = 116.dp

// A floor in dp, so a large font scale still leaves the month names room to shrink.
private val AxisMinSize = 9.dp
private val ChevronSize = 18.dp
