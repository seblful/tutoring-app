package com.seblful.klas.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.guide.FirstStep
import com.seblful.klas.domain.guide.PhoneStep
import com.seblful.klas.domain.lesson.CompletionTiming
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.daySummaryText
import com.seblful.klas.format.formatDate
import com.seblful.klas.format.toLocalDate
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.ui.common.FabClearance
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.LocalCurrency
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
    onRecordPayment: (studentId: Long) -> Unit = {},
    onGiveHomework: (studentId: Long) -> Unit = {},
    onPeople: () -> Unit = {},
    onReminders: () -> Unit = {},
    onCalendar: () -> Unit = {},
    viewModel: TodayViewModel = metroViewModel(),
) {
    val messages = rememberMessages()
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayEvents(viewModel, messages)
    // Reminders may have been allowed in system settings while the app was away, which ticks a step.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshPermission()
        onPauseOrDispose { }
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
                    hideFirstSteps = viewModel::hideFirstSteps,
                    firstStep = { step, student ->
                        when (step) {
                            FirstStep.BOOK -> onBook()
                            FirstStep.MARK_TAUGHT -> onSchedule()
                            FirstStep.PAYMENT -> student?.let(onRecordPayment) ?: onPeople()
                            FirstStep.HOMEWORK -> student?.let(onGiveHomework) ?: onHomework()
                            FirstStep.REMINDERS -> onReminders()
                        }
                    },
                    hidePhoneSetup = viewModel::hidePhoneSetup,
                    phoneStep = { step ->
                        when (step) {
                            PhoneStep.REMINDERS -> onReminders()
                            PhoneStep.CALENDAR -> onCalendar()
                            PhoneStep.WEEKLY_COPY -> onBackup()
                        }
                    },
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
    val hideFirstSteps: () -> Unit = {},
    /** Opens where [FirstStep] is done — for a payment or homework, on [student]'s card when there is one. */
    val firstStep: (step: FirstStep, student: Long?) -> Unit = { _, _ -> },
    val hidePhoneSetup: () -> Unit = {},
    /** Opens where [PhoneStep] is done on this phone. */
    val phoneStep: (PhoneStep) -> Unit = {},
)

/**
 * The day in the order it is acted on: an old backup first, since it is the one thing here about not losing
 * everything; the lesson now or next; what is over and still to mark; a new tutor's first steps; the rest of the
 * day; then money and homework, which have their own tabs and appear only when there is something.
 */
@Composable
internal fun TodayContent(
    state: TodayState,
    actions: TodayActions,
    modifier: Modifier = Modifier,
    bottomInset: PaddingValues = PaddingValues(),
) {
    val scroll = rememberScrollState()
    ScrollsToTopOnReselect(scroll)
    Column(
        modifier
            .verticalScroll(scroll)
            .padding(horizontal = Spacing.gutter)
            // Under «Book», like every list with an add button: at the gutter alone the last homework row ended
            // beneath it.
            .padding(bottom = FabClearance)
            .padding(bottomInset),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        if (state.backupStale) BackupBanner(state.lastBackupAt, actions.backup)
        state.next?.let { NextCard(it, actions.openLesson) }
        if (state.toMark.isNotEmpty()) ToMark(state.toMark, actions)
        if (state.recentMarks.isNotEmpty()) RecentlyMarked(state.recentMarks, actions.undoMark)
        // Under what the day needs doing now, above the rest of it: a new tutor's day is mostly this card, and a
        // restored one's first day starts with what the backup could not bring.
        state.phoneSetup?.let { PhoneSetupSection(it, actions) }
        state.firstSteps?.let { FirstStepsSection(it, actions) }
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
        // «Open» beside the label rather than the lines under it, which keep the card's whole width: beside
        // them, at large text, the time broke mid-number and the name mid-name.
        Column(Modifier.padding(start = Spacing.gutter, end = Spacing.inline, bottom = Spacing.gutter)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (row.phase.isRunning) {
                        stringResource(R.string.today_now)
                    } else {
                        stringResource(R.string.today_next_in, durationLabel(row.minutesAway.toInt().coerceAtLeast(1)))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                KlasButton(onClick = open, kind = ButtonKind.Text) { Text(stringResource(R.string.today_open)) }
            }
            Column(Modifier.padding(end = Spacing.inline)) {
                Text(timeRange(row), style = MaterialTheme.typography.titleLarge)
                Text(row.who, style = MaterialTheme.typography.titleLarge)
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
        val taught = stringResource(R.string.class_status_completed)
        rows.forEach { row ->
            // The widget's tick, leading as on homework and the day below (R6): the empty circle marks the
            // lesson taught, and the row itself opens it.
            val open = { actions.openLesson(row.session.first.id) }
            KlasRow(
                title = row.who,
                supporting = bulletJoin(timeRange(row), row.subject),
                onClick = open,
                start = RowStart.Done(
                    done = false,
                    label = stringResource(R.string.today_mark_one, row.who, row.session.start.timeText()),
                    doneWord = taught,
                    onDone = { actions.markTaught(row) },
                    onOpen = open,
                ),
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
                    enabled = row.session.status == LessonStatus.COMPLETED ||
                        CompletionTiming.STARTED.allows(row.phase),
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
