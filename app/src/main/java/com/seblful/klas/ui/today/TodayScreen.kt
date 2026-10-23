package com.seblful.klas.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.daySummaryText
import com.seblful.klas.format.formatDate
import com.seblful.klas.format.toLocalDate
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.common.ScrollsToTopOnReselect
import com.seblful.klas.ui.common.SettingsGear
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.durationLabel
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.common.timeRangeText
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.designsystem.component.AccentCard
import com.seblful.klas.ui.designsystem.component.AmountMeaning
import com.seblful.klas.ui.designsystem.component.AmountText
import com.seblful.klas.ui.designsystem.component.Banner
import com.seblful.klas.ui.designsystem.component.BannerKind
import com.seblful.klas.ui.designsystem.component.ButtonKind
import com.seblful.klas.ui.designsystem.component.HeaderedCard
import com.seblful.klas.ui.designsystem.component.KlasButton
import com.seblful.klas.ui.designsystem.component.KlasFab
import com.seblful.klas.ui.designsystem.component.KlasRow
import com.seblful.klas.ui.designsystem.component.RowEnd
import com.seblful.klas.ui.designsystem.component.RowStart
import com.seblful.klas.ui.designsystem.component.SectionAction
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasSemantics
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlin.time.Instant

/**
 * «Сегодня» — where the app opens. Each section links to the tab that holds its whole story: a lesson to its
 * sheet on the schedule, what is owed to the reports, homework to its tab.
 */
@Composable
fun TodayScreen(
    onSettings: () -> Unit,
    onOpenLesson: (classId: Long) -> Unit,
    onBook: () -> Unit,
    onDebts: () -> Unit,
    onHomework: () -> Unit,
    onBackup: () -> Unit,
    onSchedule: () -> Unit = {},
    onOpenStudent: (Long) -> Unit = {},
    viewModel: TodayViewModel = metroViewModel(),
) {
    val messages = rememberMessages()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources by rememberUpdatedState(LocalResources.current)
    LaunchedEffect(viewModel, messages) {
        viewModel.events.stream.collect { event ->
            when (event) {
                is TodayViewModel.Event.Marked -> messages.show(
                    resources.getQuantityString(R.plurals.today_marked, event.sessions.size, event.sessions.size),
                    resources.getString(R.string.action_undo),
                ) { viewModel.undo(event) }
                TodayViewModel.Event.UndoChanged -> messages.show(resources.getString(R.string.class_undo_changed))
            }
        }
    }
    KlasScreen(
        title = stringResource(R.string.nav_today),
        messages = messages,
        actions = { SettingsGear(onSettings) },
        floatingActionButton = { KlasFab(stringResource(R.string.class_book), onClick = onBook) },
    ) { modifier, bottomInset ->
        when (val loaded = state) {
            Loadable.Loading -> ListSkeleton(modifier)
            is Loadable.Ready -> TodayContent(
                state = loaded.value,
                actions = TodayActions(
                    openLesson = onOpenLesson,
                    markTaught = viewModel::markTaught,
                    markAllTaught = viewModel::markAllTaught,
                    debts = onDebts,
                    homework = onHomework,
                    backup = onBackup,
                    schedule = onSchedule,
                    openStudent = onOpenStudent,
                    undoMark = viewModel::undo,
                ),
                modifier = modifier,
                bottomInset = bottomInset,
            )
        }
    }
}

/** What Today's rows and buttons do — one value, so the content stays previewable without a view model. */
class TodayActions(
    val openLesson: (classId: Long) -> Unit = {},
    val markTaught: (TodayLessonRow) -> Unit = {},
    val markAllTaught: () -> Unit = {},
    val debts: () -> Unit = {},
    val homework: () -> Unit = {},
    val backup: () -> Unit = {},
    val schedule: () -> Unit = {},
    val openStudent: (Long) -> Unit = {},
    val undoMark: (TodayMark) -> Unit = {},
)

/**
 * The day in the order it is acted on: an old backup first, since it is the one thing here about not losing
 * everything; the lesson now or next; what is over and still to mark; the rest of the day; then money and
 * homework, which have their own tabs and appear only when there is something.
 */
@Composable
internal fun TodayContent(
    state: TodayState,
    actions: TodayActions,
    modifier: Modifier = Modifier,
    bottomInset: PaddingValues = PaddingValues(),
) {
    val scroll = rememberScrollState()
    ScrollsToTopOnReselect { scroll.animateScrollTo(0) }
    Column(
        modifier
            .verticalScroll(scroll)
            .padding(horizontal = Spacing.gutter)
            .padding(bottom = Spacing.gutter)
            .padding(bottomInset),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        if (state.backupStale) BackupBanner(state.lastBackupAt, actions.backup)
        state.next?.let { NextCard(it, actions.openLesson) }
        if (state.toMark.isNotEmpty()) ToMark(state.toMark, actions)
        if (state.recentMarks.isNotEmpty()) RecentlyMarked(state.recentMarks, actions.undoMark)
        DayLessons(state, actions)
        if (state.outstanding.students > 0) Owed(state, actions)
        if (state.homework.isNotEmpty()) HomeworkDue(state.homework, actions.homework)
    }
}

/** Advisory, not an error: nothing is broken, but a lost phone would take everything since [at] with it. */
@Composable
private fun BackupBanner(at: Instant?, onBackup: () -> Unit) {
    Banner(
        title = at?.let { stringResource(R.string.today_backup_old, it.formatDate()) }
            ?: stringResource(R.string.today_backup_never),
        kind = BannerKind.Advisory,
        modifier = Modifier.padding(top = Spacing.itemGap),
        detail = stringResource(R.string.today_backup_detail),
        action = SectionAction(stringResource(R.string.today_backup_action), onBackup),
    )
}

/**
 * The lesson now or next, leading the day: when it is — «Next · in 55 min» — what and who, and «Open» for its
 * sheet. The whole card opens it too.
 */
@Composable
private fun NextCard(row: TodayLessonRow, onOpen: (Long) -> Unit) {
    val open = { onOpen(row.session.first.id) }
    AccentCard(Modifier.padding(top = Spacing.itemGap), onClick = open) {
        Row(Modifier.padding(Spacing.gutter), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (row.phase.isRunning) {
                        stringResource(R.string.today_now)
                    } else {
                        stringResource(R.string.today_next_in, durationLabel(row.minutesAway.toInt().coerceAtLeast(1)))
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(bulletJoin(timeRange(row), row.who), style = MaterialTheme.typography.titleLarge)
                Text(
                    bulletJoin(
                        listOfNotNull(
                            row.subject,
                            durationLabel(row.session.durationMinutes),
                            row.session.classes.size.takeIf { row.session.isGroup }?.let {
                                pluralStringResource(R.plurals.today_members, it, it)
                            },
                        ),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            KlasButton(onClick = open, kind = ButtonKind.Text) { Text(stringResource(R.string.today_open)) }
        }
    }
}

@Composable
private fun ToMark(rows: List<TodayLessonRow>, actions: TodayActions) {
    HeaderedCard(
        title = stringResource(R.string.today_to_mark),
        summary = pluralStringResource(R.plurals.lessons_count, rows.size, rows.size),
        // One lesson has its own tick; the batch is for the evening with several.
        action = SectionAction(stringResource(R.string.today_mark_all), actions.markAllTaught).takeIf { rows.size > 1 },
    ) {
        rows.forEach { row ->
            // The widget's tick, leading as on homework (R1): the empty circle marks the lesson taught, and
            // the row itself opens it.
            val markOne = stringResource(R.string.today_mark_one, row.who, row.session.start.timeText())
            KlasRow(
                title = row.who,
                supporting = bulletJoin(timeRange(row), row.subject),
                onClick = { actions.openLesson(row.session.first.id) },
                start = RowStart.Custom {
                    NamedIconButton(
                        KlasIcons.Outlined.Circle,
                        markOne,
                        onClick = { actions.markTaught(row) },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                end = RowEnd.Opens,
            )
        }
    }
}

/**
 * The whole day as one card: its count, hours and what is billed so far, «Schedule» for the rest of the week,
 * and each lesson with the widget's round mark-done button (R6) — off for one still to come, since marking
 * a lesson before it starts stays in its sheet; a filled tick once taught.
 */
@Composable
private fun DayLessons(state: TodayState, actions: TodayActions) {
    val rows = state.lessons
    val summary = daySummaryText(LocalResources.current, state.summary, LocalCurrency.current)
    HeaderedCard(
        title = stringResource(R.string.today_lessons),
        summary = summary.takeIf { rows.isNotEmpty() },
        action = SectionAction(stringResource(R.string.nav_schedule), actions.schedule),
    ) {
        if (rows.isEmpty()) {
            KlasRow(stringResource(R.string.today_no_lessons))
        }
        val taught = stringResource(R.string.class_status_completed)
        rows.forEach { row ->
            val open = { actions.openLesson(row.session.first.id) }
            KlasRow(
                title = row.who,
                supporting = bulletJoin(listOfNotNull(timeRange(row), row.subject, phaseWord(row.phase))),
                onClick = open,
                start = RowStart.Done(
                    done = row.session.status == LessonStatus.COMPLETED,
                    label = stringResource(R.string.today_mark_one, row.who, row.session.start.timeText()),
                    doneWord = taught,
                    onDone = { actions.markTaught(row) },
                    onOpen = open,
                    enabled = row.phase != LessonPhase.UPCOMING,
                ),
                end = RowEnd.Opens,
            )
        }
    }
}

/** Who owes, most first — three of them, each with when they last paid — and «See all» for the rest. */
@Composable
private fun Owed(state: TodayState, actions: TodayActions) {
    HeaderedCard(
        title = stringResource(R.string.today_owed),
        summary = bulletJoin(
            state.outstanding.total.text(),
            pluralStringResource(R.plurals.students_count, state.outstanding.students, state.outstanding.students),
        ),
        action = SectionAction(stringResource(R.string.section_see_all), actions.debts),
    ) {
        state.debtors.take(DEBTORS_SHOWN).forEach { debtor ->
            KlasRow(
                title = debtor.name,
                supporting = debtor.lastPaidAt?.let {
                    stringResource(R.string.today_last_paid, it.toLocalDate().formatDate())
                } ?: stringResource(R.string.today_never_paid),
                end = RowEnd.Money(AmountText(debtor.owed.text(), AmountMeaning.Debt)),
                onClick = { actions.openStudent(debtor.studentId) },
            )
        }
    }
}

/**
 * Lessons marked taught from the widget or a reminder in the last ten minutes, each with its Undo: neither can
 * offer one where it is tapped (the widget stays one-way), so it is offered here.
 */
@Composable
private fun RecentlyMarked(marks: List<TodayMark>, onUndo: (TodayMark) -> Unit) {
    HeaderedCard(title = stringResource(R.string.today_recent_marks)) {
        val undo = stringResource(R.string.action_undo)
        marks.forEach { recent ->
            KlasRow(
                title = recent.who,
                supporting = stringResource(R.string.today_marked_at, recent.mark.at.timeText()),
                end = RowEnd.Action(KlasIcons.Filled.Undo, undo) { onUndo(recent) },
            )
        }
    }
}

@Composable
private fun HomeworkDue(rows: List<HomeworkRow>, onHomework: () -> Unit) {
    HeaderedCard(
        title = stringResource(R.string.today_homework),
        summary = rows.size.toString(),
        action = SectionAction(stringResource(R.string.section_see_all), onHomework),
    ) {
        rows.take(HOMEWORK_SHOWN).forEach { row ->
            KlasRow(row.title, supporting = row.homework.text, onClick = onHomework)
        }
    }
}

/** What a row adds beyond its time: a taught lesson says so; the rest are told apart by the sections. */
@Composable
private fun phaseWord(phase: LessonPhase): String? = when (phase) {
    LessonPhase.IN_PROGRESS -> stringResource(R.string.class_status_in_progress)
    LessonPhase.SETTLED -> stringResource(R.string.class_status_completed)
    LessonPhase.UPCOMING, LessonPhase.OVERDUE -> null
}

@Composable
private fun timeRange(row: TodayLessonRow): String = row.session.start.timeRangeText(row.session.durationMinutes)

private const val HOMEWORK_SHOWN = 3

private const val DEBTORS_SHOWN = 3
