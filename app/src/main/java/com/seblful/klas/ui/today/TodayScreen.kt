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
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDate
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.SettingsGear
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.designsystem.component.AccentCard
import com.seblful.klas.ui.designsystem.component.AmountMeaning
import com.seblful.klas.ui.designsystem.component.AmountText
import com.seblful.klas.ui.designsystem.component.ButtonKind
import com.seblful.klas.ui.designsystem.component.HeaderedCard
import com.seblful.klas.ui.designsystem.component.KlasButton
import com.seblful.klas.ui.designsystem.component.KlasFab
import com.seblful.klas.ui.designsystem.component.KlasRow
import com.seblful.klas.ui.designsystem.component.RowEnd
import com.seblful.klas.ui.designsystem.component.SectionAction
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasSemantics
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlin.time.Duration.Companion.minutes
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
        floatingActionButton = { KlasFab(stringResource(R.string.schedule_add_class), onClick = onBook) },
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
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter)
            .padding(bottom = Spacing.gutter)
            .padding(bottomInset),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        if (state.backupStale) BackupBanner(state.lastBackupAt, actions.backup)
        state.next?.let { NextCard(it, actions.openLesson) }
        if (state.toMark.isNotEmpty()) ToMark(state.toMark, actions)
        DayLessons(state.lessons, actions.openLesson)
        if (state.outstanding.students > 0) {
            HeaderedCard(
                title = stringResource(R.string.today_owed),
                action = SectionAction(stringResource(R.string.section_see_all), actions.debts),
            ) {
                KlasRow(
                    title = pluralStringResource(
                        R.plurals.students_count,
                        state.outstanding.students,
                        state.outstanding.students,
                    ),
                    end = RowEnd.Money(AmountText(state.outstanding.total.text(), AmountMeaning.Debt)),
                    onClick = actions.debts,
                )
            }
        }
        if (state.homework.isNotEmpty()) HomeworkDue(state.homework, actions.homework)
    }
}

/** Advisory, not an error: nothing is broken, but a lost phone would take everything since [at] with it. */
@Composable
private fun BackupBanner(at: Instant?, onBackup: () -> Unit) {
    val roles = klasSemantics
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.inline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.itemGap)
            .background(roles.advisory, MaterialTheme.shapes.medium)
            .padding(start = Spacing.gutter, end = Spacing.inline, top = Spacing.inline, bottom = Spacing.inline),
    ) {
        Icon(KlasIcons.Outlined.Warning, contentDescription = null, tint = roles.onAdvisory)
        Column(Modifier.weight(1f)) {
            Text(
                at?.let { stringResource(R.string.today_backup_old, it.formatDate()) }
                    ?: stringResource(R.string.today_backup_never),
                style = MaterialTheme.typography.titleSmall,
                color = roles.onAdvisory,
            )
            Text(
                stringResource(R.string.today_backup_detail),
                style = MaterialTheme.typography.bodySmall,
                color = roles.onAdvisory,
            )
        }
        KlasButton(onClick = onBackup, kind = ButtonKind.Quiet) { Text(stringResource(R.string.today_backup_action)) }
    }
}

@Composable
private fun NextCard(row: TodayLessonRow, onOpen: (Long) -> Unit) {
    AccentCard(Modifier.padding(top = Spacing.itemGap), onClick = { onOpen(row.session.first.id) }) {
        Column(Modifier.padding(Spacing.gutter)) {
            Text(
                stringResource(if (row.phase.isRunning) R.string.today_now else R.string.today_next),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(timeRange(row), style = MaterialTheme.typography.titleLarge)
            Text(bulletJoin(row.who, row.subject), style = MaterialTheme.typography.bodyMedium)
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
            KlasRow(
                title = row.who,
                supporting = bulletJoin(timeRange(row), row.subject),
                onClick = { actions.openLesson(row.session.first.id) },
                end = RowEnd.Action(
                    KlasIcons.Filled.Check,
                    stringResource(R.string.today_mark_one, row.who),
                ) { actions.markTaught(row) },
            )
        }
    }
}

@Composable
private fun DayLessons(rows: List<TodayLessonRow>, onOpen: (Long) -> Unit) {
    HeaderedCard(
        title = stringResource(R.string.today_lessons),
        summary = if (rows.isEmpty()) null else pluralStringResource(R.plurals.lessons_count, rows.size, rows.size),
    ) {
        if (rows.isEmpty()) {
            KlasRow(stringResource(R.string.today_no_lessons))
        }
        rows.forEach { row ->
            KlasRow(
                title = row.who,
                supporting = bulletJoin(listOfNotNull(timeRange(row), row.subject, phaseWord(row.phase))),
                onClick = { onOpen(row.session.first.id) },
                end = RowEnd.Opens,
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
private fun timeRange(row: TodayLessonRow): String {
    val start = row.session.start
    return "${start.timeText()}–${(start + row.session.durationMinutes.minutes).timeText()}"
}

private const val HOMEWORK_SHOWN = 3
