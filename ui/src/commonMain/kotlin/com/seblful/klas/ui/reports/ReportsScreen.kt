package com.seblful.klas.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.domain.pro.ProFeature
import com.seblful.klas.domain.reports.ReportPeriod
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.format.formatMonth
import com.seblful.klas.format.money
import com.seblful.klas.print.SheetPrinter
import com.seblful.klas.queries.reports.ReportScope
import com.seblful.klas.queries.reports.ScopeChoice
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.FilterChipRow
import com.seblful.klas.ui.common.FilterMenuAction
import com.seblful.klas.ui.common.KlasMenu
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.MaxContentWidth
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.common.PageStepper
import com.seblful.klas.ui.common.ScreenHeader
import com.seblful.klas.ui.common.ScrollsToTopOnReselect
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.SettingsGear
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.SupportingPaneMaxWidth
import com.seblful.klas.ui.common.TickedMenuItem
import com.seblful.klas.ui.common.constrainedContent
import com.seblful.klas.ui.common.plusInset
import com.seblful.klas.ui.common.rememberCreateDocument
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.rememberSkeletonPulse
import com.seblful.klas.ui.common.roomForSupportingPane
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.designsystem.component.ButtonKind
import com.seblful.klas.ui.designsystem.component.KlasButton
import com.seblful.klas.ui.navigation.Arrival
import com.seblful.klas.ui.pro.LocalPro
import com.seblful.klas.ui.pro.ProAction
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.first

/** The money screen: what the chosen period earned, where it came from, and who still owes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onStudentClick: (Long) -> Unit,
    /** The debt digest's latest tap, which scrolls to the debts once — see `AppRoot`. */
    debtsArrival: Arrival<Unit>? = null,
    onSettings: () -> Unit = {},
    viewModel: ReportsViewModel = metroViewModel(),
) {
    val listState = rememberLazyListState()
    ScrollsToTopOnReselect(listState)
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
    val scope by viewModel.scope.collectAsStateWithLifecycle()
    val scopes by viewModel.scopes.collectAsStateWithLifecycle()
    // The free report: this month, its income and debts. The rest opens Klas Pro.
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val pro = LocalPro.current
    val openPro = { pro.open(ProFeature.REPORTS) }
    val shown by rememberUpdatedState(report)
    LaunchedEffect(debtsArrival) {
        if (!viewModel.owesDebts(debtsArrival)) return@LaunchedEffect
        val items = snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        // The debts card is the list's last items, its heading first among them.
        listState.scrollToItem((items - (shown?.let(::debtsItemCount) ?: 1)).coerceAtLeast(0))
        viewModel.debtsScrolled(debtsArrival)
    }
    val messages = rememberMessages()
    val noPickerMsg = stringResource(Strings.student_contact_failed)

    val exportOkMsg = stringResource(Strings.reports_export_success)
    val exportErrorMsg = stringResource(Strings.reports_export_error)
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

    val files = rememberReportFiles(exportable, period, scopes.firstOrNull { it.scope == scope }?.name.orEmpty())
    // Which file the picker was opened for: one picker per type, one menu for every file. Lost if the process
    // is put away while the picker is up; the answer then says nothing was saved.
    var saving by remember { mutableStateOf<ReportsViewModel.ReportFile?>(null) }
    val exportCsv = rememberCreateDocument(CSV_MIME_TYPE, noPickerMsg, messages) { document ->
        viewModel.exportOrSay(document, saving) { messages.show(exportErrorMsg) }
    }
    val exportPdf = rememberCreateDocument(SheetPrinter.PDF_MIME_TYPE, noPickerMsg, messages) { document ->
        viewModel.exportOrSay(document, saving) { messages.show(exportErrorMsg) }
    }
    // Names the period, so a report arriving in somebody's mail says which month it is.
    val shareSubject = stringResource(Strings.reports_share_subject, periodTitle(period))

    val twoPane = roomForSupportingPane()
    KlasScreen(
        title = stringResource(Strings.reports_title),
        messages = messages,
        maxContentWidth = if (twoPane) SupportingPaneMaxWidth else MaxContentWidth,
        actions = {
            // A backup is JSON meant to be read back by this app; a tutor totalling their year — or an
            // accountant — needs a file that opens in a spreadsheet.
            if (report?.hasData == true && locked) {
                NamedIconButton(KlasIcons.Filled.Download, stringResource(Strings.reports_export_menu), openPro)
            } else if (report?.hasData == true) {
                // **Send comes before save.** Saving a CSV puts it in a folder the tutor then has to go and
                // find in a second app; what they do with a month is send it — to an accountant, or to
                // their own mail so it is somewhere other than this phone. Both stay: a file kept and a
                // file sent are different acts, and the share sheet cannot put one on the SD card.
                // Kept on screen while a new period assembles, so they don't blink with every arrow tap;
                // a tap inside that frame or two sends nothing rather than the previous period's file.
                // One Export menu, not two look-alike icons that leave the tutor to guess which sent and
                // which saved.
                ReportExportMenu(
                    files = files,
                    period = period,
                    onShare = { name, file -> viewModel.share(name, shareSubject, file) },
                    onSave = { name, file ->
                        saving = file
                        if (file is ReportsViewModel.ReportFile.Pdf) exportPdf(name) else exportCsv(name)
                    },
                )
                ScopeMenu(scopes, scope, viewModel::setScope)
            }
            ProAction()
            SettingsGear(onSettings)
        },
    ) { modifier, bottomInset ->
        // «Нет данных» says the tutor has earned nothing, which is a claim about their year — so it
        // waits for the report to actually arrive: an empty initial report would make that claim on
        // every cold open for a frame or two. See [Loadable].
        if (report == null) {
            Column(modifier.padding(bottomInset)) { ReportsSkeleton() }
            return@KlasScreen
        }
        if (!report.hasData) {
            EmptyState(
                title = stringResource(Strings.reports_empty),
                detail = stringResource(Strings.reports_empty_detail),
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
                        onLocked = openPro.takeIf { locked },
                    )
                }
                // A narrowed report says so beside the period it covers, and is let go from there.
                scopes.firstOrNull { it.scope == scope }?.let { choice ->
                    FilterChipRow(choice.name, onClear = { viewModel.setScope(ReportScope.Everything) })
                }
            }
            ReportBody(
                report,
                scope,
                onStudentClick,
                listState,
                bottomInset,
                twoPane,
                onPro = openPro.takeIf { locked },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Writes [file] to [document]; with no file left to write — the process was put away — [nothingToSave] says so. */
private fun ReportsViewModel.exportOrSay(
    document: DocumentRef,
    file: ReportsViewModel.ReportFile?,
    nothingToSave: () -> Unit,
) {
    if (file == null) nothingToSave() else export(document, file)
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

/**
 * The report as one list, or on an expanded window as Material's supporting pane: what the period came to
 * (the income, its stats and shape) beside where it came from and who still owes, each scrolling alone.
 *
 * @param listState the list holding the breakdowns and debts, the one the debt digest scrolls.
 */
@Composable
private fun ReportBody(
    state: Reports.Report,
    scope: ReportScope,
    onStudentClick: (Long) -> Unit,
    listState: LazyListState,
    bottomInset: PaddingValues,
    twoPane: Boolean,
    /** The way to Klas Pro while the report is the free one; null when it is whole. */
    onPro: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val titles = BreakdownTitles(
        byGroup = stringResource(Strings.reports_by_group),
        bySubject = stringResource(Strings.reports_by_subject),
        byStudent = stringResource(Strings.reports_by_student),
    )
    val padding = PaddingValues(Spacing.gutter).plusInset(bottomInset)

    // No arrangement: a card's rows are list items of their own (see [CardSlice]), so the gap between
    // cards is each card's own, and its rows sit as close as they did inside one card.
    if (!twoPane) {
        LazyColumn(modifier = modifier.fillMaxWidth(), state = listState, contentPadding = padding) {
            summaryItems(state, scope, whole = onPro == null)
            breakdownItems(state, titles, onStudentClick, onPro)
        }
        return
    }
    val summaryState = rememberLazyListState()
    ScrollsToTopOnReselect(summaryState)
    Row(modifier.fillMaxWidth()) {
        LazyColumn(Modifier.weight(1f), state = summaryState, contentPadding = padding) {
            summaryItems(state, scope, whole = onPro == null)
        }
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
        ) { breakdownItems(state, titles, onStudentClick, onPro) }
    }
}

private class BreakdownTitles(val byGroup: String, val bySubject: String, val byStudent: String)

/**
 * What the period came to: the income, then — if there was any — its stats, its shape and its kinds; the
 * income alone unless the report is [whole].
 */
private fun LazyListScope.summaryItems(state: Reports.Report, scope: ReportScope, whole: Boolean) {
    // Earned and received on one hero (D6), so a period paid for without earning still has its card.
    item(key = "income", contentType = "income") { IncomeCard(state, showsReceived = scope.showsReceived) }
    if (state.noIncome) {
        standalone("no-income") {
            Text(
                stringResource(Strings.reports_no_income),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    if (!whole) return
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
    onPro: (() -> Unit)?,
) {
    if (onPro != null) {
        standalone("pro") { ProReportsCard(onPro) }
    } else if (!state.noIncome) {
        rankedSection("group", titles.byGroup, state.byGroup)
        rankedSection("subject", titles.bySubject, state.bySubject)
        // Each student's row opens them, as a debt's does (R6): a name worth reading here is worth a tap.
        rankedSection("student", titles.byStudent, state.byStudent, onOpen = onStudentClick)
    }
    // Last, and it has to stay last: the debt digest's tap scrolls to it — see [debtsItemCount].
    debtsSection(state, onStudentClick)
}

/** What the free report leaves out, and the way to it. */
@Composable
private fun ProReportsCard(onPro: () -> Unit) {
    SectionCard {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            Text(stringResource(Strings.pro_reports_locked), style = MaterialTheme.typography.bodyMedium)
            KlasButton(onClick = onPro, kind = ButtonKind.Tonal) { Text(stringResource(Strings.pro_see)) }
        }
    }
}

/** An item standing on its own, [Spacing.itemGap] below the one before it — its [key] is its kind too. */
private fun LazyListScope.standalone(key: String, content: @Composable () -> Unit) {
    item(key = key, contentType = key) { Box(Modifier.padding(top = Spacing.itemGap)) { content() } }
}

/**
 * A ranked breakdown, one list item per line: a year's per-student section can run to hundreds, and as a
 * single card it was composed whole in the frame it scrolled into view.
 */
private fun LazyListScope.rankedSection(
    section: String,
    title: String,
    lines: List<Reports.Line>,
    onOpen: ((Long) -> Unit)? = null,
) {
    if (lines.isEmpty()) return
    item(key = "$section/heading", contentType = CARD_HEADING) { CardSlice(first = true) { CardHeading(title) } }
    itemsIndexed(lines, key = { _, line -> "$section/${line.id}" }, contentType = { _, _ -> "ranked" }) { index, line ->
        CardSlice(last = index == lines.lastIndex, gapAbove = Spacing.itemGap) {
            RankedLine(line, onOpen?.let { open -> { open(line.id) } })
        }
    }
}

/** How many items [debtsSection] adds: its heading, then the «нет долгов» line or the total and each debt. */
private fun debtsItemCount(state: Reports.Report): Int =
    1 + if (state.debts.isEmpty()) 1 else 1 + state.debts.size

@Composable
private fun PeriodNavigator(
    period: ReportPeriod,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onKind: (ReportPeriod.Kind) -> Unit,
    /** Where every step goes while the report is the free one: Klas Pro. */
    onLocked: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    PageStepper(
        previousDescription = stringResource(Strings.reports_previous_period),
        nextDescription = stringResource(Strings.reports_next_period),
        onPrevious = onLocked ?: onPrevious,
        onNext = onLocked ?: onNext,
        enabled = period.isNavigable,
    ) {
        Box {
            // Ink like the schedule's page title: the same control in both places, the arrow saying it opens.
            KlasButton(
                onClick = { expanded = true },
                kind = ButtonKind.Quiet,
            ) {
                // Shrinks before it cuts, as the schedule's page title does.
                Text(
                    periodTitle(period),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = with(LocalDensity.current) { PeriodTitleMinSize.toSp() },
                        maxFontSize = MaterialTheme.typography.titleMedium.fontSize,
                    ),
                )
                Icon(KlasIcons.Filled.ArrowDropDown, stringResource(Strings.reports_period))
            }
            KlasMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ReportPeriod.Kind.entries.forEach { kind ->
                    TickedMenuItem(
                        text = stringResource(periodKindLabel(kind)),
                        selected = kind == period.kind,
                        onClick = {
                            onLocked?.invoke() ?: onKind(kind)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun periodTitle(period: ReportPeriod): String = when (period.kind) {
    ReportPeriod.Kind.MONTH -> formatMonth(period.anchor)
    ReportPeriod.Kind.YEAR -> stringResource(Strings.reports_period_year_title, period.anchor.year)
    ReportPeriod.Kind.ALL -> stringResource(Strings.reports_period_all)
}

private fun periodKindLabel(kind: ReportPeriod.Kind): StringResource = when (kind) {
    ReportPeriod.Kind.MONTH -> Strings.reports_period_month
    ReportPeriod.Kind.YEAR -> Strings.reports_period_year
    ReportPeriod.Kind.ALL -> Strings.reports_period_all
}

/** The funnel narrowing the report to one subject or one student (R5), as the homework tab's does. */
@Composable
private fun ScopeMenu(choices: List<ScopeChoice>, scope: ReportScope, onSelect: (ReportScope) -> Unit) {
    if (choices.isEmpty()) return
    FilterMenuAction(
        name = stringResource(Strings.reports_filter),
        allLabel = stringResource(Strings.reports_filter_all),
        options = choices,
        selected = choices.firstOrNull { it.scope == scope },
        optionLabel = { it.name },
        onSelect = { onSelect(it?.scope ?: ReportScope.Everything) },
        leading = { choice ->
            Icon(
                if (choice.scope is ReportScope.Subject) KlasIcons.Outlined.MenuBook else KlasIcons.Outlined.Person,
                contentDescription = null,
            )
        },
        narrowed = scope != ReportScope.Everything,
    )
}

/** The schedule title's floor (`NavigatorTitleMinSize`), in dp so font scaling leaves room to shrink. */
private val PeriodTitleMinSize = 11.dp
