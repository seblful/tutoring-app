package com.seblful.klas.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.domain.LessonKind
import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.ContentCard
import com.seblful.klas.ui.common.DatePickerModal
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.LessonKindIcon
import com.seblful.klas.ui.common.StatusBadge
import com.seblful.klas.ui.common.SwipeToConfirmBox
import com.seblful.klas.ui.common.TimePickerModal
import com.seblful.klas.ui.common.classModeLabel
import com.seblful.klas.ui.common.classStatusColor
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.common.constrainedContent
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.format.formatDay
import com.seblful.klas.format.formatMonth
import com.seblful.klas.format.formatWeekRange
import com.seblful.klas.format.toLocalDate
import com.seblful.klas.format.toLocalTime
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onAddClass: () -> Unit,
    onEditClass: (Long) -> Unit,
    onAddStudent: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    // One state for the body: only the active view's lessons are assembled (see ScheduleContent).
    val content by viewModel.content.collectAsState()
    val date by viewModel.selectedDate.collectAsState()
    val canAddClass by viewModel.canAddClass.collectAsState()
    // From the view model, so it rolls over at midnight rather than being fixed at composition.
    val today by viewModel.today.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val month by viewModel.visibleMonth.collectAsState()
    val grid by viewModel.monthGrid.collectAsState()
    val week by viewModel.weekDays.collectAsState()
    val loads by viewModel.dayLoads.collectAsState()
    val timeConflict by viewModel.timeConflict.collectAsState()

    // The sheet acts on the whole lesson in it, a group's included: its actions are the lesson's
    // time and status, and who is in one is changed on the lesson's own form.
    var sheetFor by remember { mutableStateOf<ScheduleRow?>(null) }
    var movingRow by remember { mutableStateOf<ScheduleRow?>(null) }
    var deletingRow by remember { mutableStateOf<ScheduleRow?>(null) }

    // These actions are tapped in a sheet that closes as they are tapped, so a write that failed would
    // otherwise read as one that had not been tapped at all.
    val messages = rememberMessages()
    val actionFailedMsg = stringResource(R.string.action_failed)
    LaunchedEffect(Unit) {
        viewModel.events.stream.collect { messages.show(actionFailedMsg) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(messages.hostState) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.schedule_title)) }) },
        floatingActionButton = {
            // Hidden rather than shown-and-refusing. It used to be always present: with no students
            // it looked enabled, popped a toast naming what was missing, and left the tutor to go and
            // find it — a control whose only behaviour is to decline. The empty state below carries
            // the same information *and* the way out of it.
            if (canAddClass) {
                FloatingActionButton(onClick = onAddClass) {
                    Icon(Icons.Filled.Add, stringResource(R.string.action_add))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).constrainedContent()) {
            // Which view, said out loud. It used to hide behind an unlabelled calendar icon that
            // opened a menu — two taps to switch, and the icon never told you which of the three you
            // were in. A segmented control is one tap and is its own label; the app already picks
            // this way in Settings.
            ViewModeSelector(selected = viewMode, onSelect = viewModel::setViewMode)

            // The navigator stays pinned in every mode, so paging is always one tap away.
            when (viewMode) {
                ScheduleViewMode.DAY -> {
                    DayNavigator(
                        date = date,
                        onPrevious = viewModel::goToPreviousDay,
                        onNext = viewModel::goToNextDay,
                        onToday = viewModel::goToToday,
                    )
                    HorizontalDivider()
                }
                ScheduleViewMode.WEEK -> WeekNavigator(
                    days = week,
                    onPrevious = viewModel::goToPreviousWeek,
                    onNext = viewModel::goToNextWeek,
                    onToday = viewModel::goToToday,
                )
                ScheduleViewMode.MONTH -> MonthNavigator(
                    month = month,
                    onPrevious = viewModel::goToPreviousMonth,
                    onNext = viewModel::goToNextMonth,
                    onToday = viewModel::goToToday,
                )
            }
            // The grid scrolls with the classes rather than sitting above them: six week rows plus
            // a full day of classes doesn't fit a landscape phone.
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 11.dp, bottom = 88.dp),
            ) {
                when (viewMode) {
                    // The day list stands alone under its stepper.
                    ScheduleViewMode.DAY -> dayList(
                        rows = content.rows,
                        canAddClass = canAddClass,
                        onRowClick = { sheetFor = it },
                        onComplete = { viewModel.setStatus(it.session, ClassStatus.COMPLETED) },
                        onAddClass = onAddClass,
                        onAddStudent = onAddStudent,
                    )

                    // A whole week at once: the strip is an overview and a day picker for the add
                    // button, while the agenda below always shows every day that has classes.
                    ScheduleViewMode.WEEK -> {
                        item(key = "week-strip", contentType = "calendar") {
                            Column {
                                WeekStrip(
                                    days = week,
                                    loads = loads,
                                    selected = date,
                                    today = today,
                                    onSelect = viewModel::selectDate,
                                )
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider()
                            }
                        }
                        if (content.sections.isEmpty()) {
                            item(key = "empty") {
                                EmptyState(
                                    title = stringResource(R.string.schedule_week_empty),
                                    icon = Icons.Filled.EventBusy,
                                    modifier = Modifier.heightIn(min = 200.dp),
                                )
                            }
                        } else {
                            content.sections.forEach { section ->
                                item(key = "day-${section.date}", contentType = "day-header") {
                                    DaySectionHeader(section.date, isSelected = section.date == date)
                                }
                                items(section.rows, key = { it.klass.id }) { row ->
                                    SwipeToCompleteCard(
                                        row = row,
                                        onClick = { sheetFor = row },
                                        onComplete = {
                                            viewModel.setStatus(row.session, ClassStatus.COMPLETED)
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }

                    // The calendar takes over from the day stepper: the grid is how you move
                    // between days, and the header below it names the day the list is showing.
                    ScheduleViewMode.MONTH -> {
                        item(key = "calendar", contentType = "calendar") {
                            Column {
                                MonthCalendar(
                                    grid = grid,
                                    loads = loads,
                                    selected = date,
                                    today = today,
                                    onSelect = viewModel::selectDate,
                                )
                                Spacer(Modifier.height(12.dp))
                                SelectedDayHeader(date)
                                HorizontalDivider()
                                Spacer(Modifier.height(11.dp))
                            }
                        }
                        dayList(
                            rows = content.rows,
                            canAddClass = canAddClass,
                            onRowClick = { sheetFor = it },
                            onComplete = { viewModel.setStatus(it.session, ClassStatus.COMPLETED) },
                            onAddClass = onAddClass,
                            onAddStudent = onAddStudent,
                        )
                    }
                }
            }
        }
    }

    sheetFor?.let { row ->
        ClassActionsSheet(
            row = row,
            sheetState = rememberModalBottomSheetState(),
            onEdit = { onEditClass(row.klass.id); sheetFor = null },
            onStatus = { viewModel.setStatus(row.session, it); sheetFor = null },
            onMove = { movingRow = row; sheetFor = null },
            onDelete = { deletingRow = row; sheetFor = null },
            onDismiss = { sheetFor = null },
        )
    }

    movingRow?.let { row ->
        MoveClassFlow(
            start = row.klass.start,
            onMoved = { newStart -> viewModel.move(row.session, newStart) },
            onDone = { movingRow = null },
        )
    }

    // A move, or a lesson being returned to the schedule, is refused when that hour is already
    // taken; either way, say which lesson holds it.
    timeConflict?.let { clash ->
        AlertDialog(
            onDismissRequest = viewModel::dismissTimeConflict,
            text = {
                Text(
                    stringResource(
                        R.string.class_time_conflict,
                        clash.who,
                        "${clash.slot.start.timeText()} – ${clash.slot.end.timeText()}",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissTimeConflict) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }

    deletingRow?.let { row ->
        // Which warning applies is `row.deleteWarning`'s — see [DeleteWarning]. The four strings for it
        // are this screen's, and that is all this `when` says.
        val warning = row.deleteWarning
        val count = warning.classes
        val confirmText = when {
            warning.billed && warning.group ->
                pluralStringResource(R.plurals.class_delete_group_completed_confirm, count, count)
            warning.billed -> stringResource(R.string.class_delete_completed_confirm)
            warning.group -> pluralStringResource(R.plurals.class_delete_group_confirm, count, count)
            else -> stringResource(R.string.class_delete_confirm)
        }
        ConfirmDialog(
            text = confirmText,
            onConfirm = { viewModel.delete(row.session) },
            onDismiss = { deletingRow = null },
        )
    }
}

/**
 * The selected day's classes, or an empty state. Shared by the day and month views.
 *
 * The empty state depends on **why** it is empty. A day with no lessons is an ordinary day and offers
 * to book one; an app with no students cannot book anything yet, and offers the step that unblocks it
 * instead of naming it and stopping there.
 */
private fun LazyListScope.dayList(
    rows: List<ScheduleRow>,
    canAddClass: Boolean,
    onRowClick: (ScheduleRow) -> Unit,
    onComplete: (ScheduleRow) -> Unit,
    onAddClass: () -> Unit,
    onAddStudent: () -> Unit,
) {
    if (rows.isEmpty()) {
        item(key = "empty") {
            if (canAddClass) {
                EmptyState(
                    title = stringResource(R.string.schedule_empty),
                    detail = stringResource(R.string.schedule_empty_detail),
                    icon = Icons.Filled.EventBusy,
                    actionLabel = stringResource(R.string.schedule_add_class),
                    onAction = onAddClass,
                    modifier = Modifier.heightIn(min = 260.dp),
                )
            } else {
                EmptyState(
                    title = stringResource(R.string.schedule_empty_no_setup),
                    detail = stringResource(R.string.schedule_empty_no_setup_detail),
                    icon = Icons.Filled.PersonAddAlt,
                    actionLabel = stringResource(R.string.schedule_add_student),
                    onAction = onAddStudent,
                    modifier = Modifier.heightIn(min = 260.dp),
                )
            }
        }
    } else {
        items(rows, key = { it.klass.id }) { row ->
            SwipeToCompleteCard(
                row = row,
                onClick = { onRowClick(row) },
                onComplete = { onComplete(row) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
            )
        }
    }
}

/** Chooses between the day, week and month views. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeSelector(selected: ScheduleViewMode, onSelect: (ScheduleViewMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        ScheduleViewMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, ScheduleViewMode.entries.size),
            ) { Text(stringResource(viewModeLabel(mode))) }
        }
    }
}

private fun viewModeLabel(mode: ScheduleViewMode): Int = when (mode) {
    ScheduleViewMode.DAY -> R.string.schedule_view_day
    ScheduleViewMode.WEEK -> R.string.schedule_view_week
    ScheduleViewMode.MONTH -> R.string.schedule_view_month
}

/**
 * A lesson card that can be marked **completed** with one swipe.
 *
 * Marking a lesson done is the highest-frequency action in the product and cost two taps and a sheet,
 * every time, forever. A gesture on the row makes it one — and the row is exactly where the tutor is
 * already looking, having just taught it.
 *
 * Three deliberate limits:
 * - **Only where completing is legal.** A lesson already completed, or one whose members are in mixed
 *   states, gets a plain card: [ScheduleRow.canSwipeComplete] decides, from the same rule the sheet's
 *   actions are built from, so the gesture can never do something the menu would refuse.
 * - **It is taken back by sliding home.** The phone ticks at the mark and the lesson is marked only on
 *   the lift, so a swipe begun by accident — or aimed at the row below — costs a thumb moving back and
 *   nothing else. [SwipeToConfirmBox] owns that whole contract.
 * - **One direction, and it never sticks.** The row springs back rather than staying dismissed —
 *   nothing is being removed, and the status badge flipping to «Проведено» is the confirmation. A box
 *   left in its dismissed state would be a row hidden behind its own background until recomposed.
 */
@Composable
private fun LazyItemScope.SwipeToCompleteCard(
    row: ScheduleRow,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!row.canSwipeComplete) {
        ScheduleCard(row, onClick, modifier.animateItem())
        return
    }
    SwipeToConfirmBox(
        onConfirm = onComplete,
        modifier = modifier.animateItem(),
        reveal = {
            Row(
                Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.shapes.medium,
                    )
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.class_mark_completed),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.class_status_completed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        },
    ) {
        ScheduleCard(row, onClick)
    }
}

@Composable
private fun ScheduleCard(row: ScheduleRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Whether the card recedes is the row's own answer — see [ScheduleRow.recedes]: a day with
    // four lessons and three cancellations must not look like a full day.
    val cancelled = row.recedes
    ContentCard(onClick = onClick, modifier = modifier, dimmed = cancelled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.klass.start.timeText(),
                style = MaterialTheme.typography.titleMedium,
                color = if (cancelled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
                // Struck through, so the hour reads as one that was given back.
                textDecoration = if (cancelled) TextDecoration.LineThrough else null,
            )
            // 12dp rather than 16. Every dp between the time and the badge is width the lesson's own
            // name does not get, and that name is the one thing on the card that can run long.
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Both kinds are marked, not just the group ones: an icon that appears only
                    // sometimes reads as "this lesson has something extra", which leaves an individual
                    // lesson looking like a lesson with a missing icon rather than an individual one.
                    LessonKindIcon(LessonKind.of(row.klass), Modifier.padding(end = 6.dp))
                    // **One line.** A name long enough to wrap — «Группа Биология» — used to break
                    // across two, and because the time on the left is centred against the whole card
                    // it then sat beside the *second* line with a hole above it. Rows also stopped
                    // being a uniform height, which is what a scannable list is for. The weight is
                    // what makes the ellipsis land at the badge rather than the text pushing into it.
                    Text(
                        row.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    "${row.subjectName} · ${classModeLabel(row.klass.mode)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.isGroup) {
                    Text(
                        // The roster is the least important line on the card and the most likely to
                        // run long, so it is the one allowed to be cut.
                        row.members.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            SessionStatusBadge(row)
        }
    }
}

/**
 * The lesson's status — one of the three, whatever its members have been corrected to individually.
 * Which of them missed it is the lesson's own roster to say, not this badge's.
 */
@Composable
private fun SessionStatusBadge(row: ScheduleRow) {
    val status = row.session.status
    StatusBadge(text = classStatusLabel(status), color = classStatusColor(status))
}

@Composable
private fun DayNavigator(
    date: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    PageNavigator(
        title = date.formatDay(),
        previousDescription = stringResource(R.string.schedule_previous_day),
        nextDescription = stringResource(R.string.schedule_next_day),
        onPrevious = onPrevious,
        onNext = onNext,
        onToday = onToday,
    )
}

@Composable
private fun WeekNavigator(
    days: List<LocalDate>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    PageNavigator(
        title = formatWeekRange(days.first(), days.last()),
        previousDescription = stringResource(R.string.schedule_previous_week),
        nextDescription = stringResource(R.string.schedule_next_week),
        onPrevious = onPrevious,
        onNext = onNext,
        onToday = onToday,
    )
}

@Composable
private fun MonthNavigator(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    PageNavigator(
        title = formatMonth(month),
        previousDescription = stringResource(R.string.schedule_previous_month),
        nextDescription = stringResource(R.string.schedule_next_month),
        onPrevious = onPrevious,
        onNext = onNext,
        onToday = onToday,
    )
}

/** ‹ title › with a "jump to today" button — shared by the day stepper and the month pager. */
@Composable
private fun PageNavigator(
    title: String,
    previousDescription: String,
    nextDescription: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) { Icon(Icons.Filled.ChevronLeft, previousDescription) }
        // The title takes the space the arrows leave: a week range spanning two long month names
        // is far wider than "Июль 2026", and must ellipsize rather than push the arrows off-screen.
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onToday) {
                Icon(Icons.Filled.Today, stringResource(R.string.schedule_today))
            }
        }
        IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, nextDescription) }
    }
}

/** Names the day the list below belongs to, since the calendar's title is the month. */
@Composable
private fun SelectedDayHeader(date: LocalDate) {
    Text(
        text = date.formatDay(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * Divides one day's classes from the next in the week agenda. The selected day is tinted so the
 * strip's selection stays traceable in the list below it.
 */
@Composable
private fun DaySectionHeader(date: LocalDate, isSelected: Boolean) {
    Text(
        text = date.formatDay(),
        style = MaterialTheme.typography.titleSmall,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
    )
}

/**
 * What can be done to the lesson that was tapped: change its status, edit it, move it, delete it.
 *
 * **Status comes first and is a control, not a list of verbs.** It is the most common reason the sheet
 * is open by a wide margin, and it used to be one or two ordinary rows in the middle of a flat menu,
 * indistinguishable from «Перенести» — so the sheet answered "what can I do" without answering "what
 * is this lesson now".
 *
 * Every action is the **whole lesson's**, a group's included. The sheet deliberately does not list
 * the members: who a lesson covers is picked while booking it, on the form, and a menu that changes
 * a lesson's status is not where a roster belongs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassActionsSheet(
    row: ScheduleRow,
    sheetState: androidx.compose.material3.SheetState,
    onEdit: () -> Unit,
    onStatus: (ClassStatus) -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val klass = row.klass
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // Header: which lesson these actions apply to.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LessonKindIcon(LessonKind.of(klass), Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${klass.start.timeText()} · ${row.title}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${row.subjectName} · ${classModeLabel(klass.mode)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SessionStatusBadge(row)
            }

            // The lesson's state, as a state: every status it can be in, with the current one shown
            // selected. Offering only the transitions (as a list of actions did) meant the sheet never
            // said what the lesson *is* — the tutor had to infer it from which verbs were absent.
            StatusSelector(row = row, onStatus = onStatus)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Editing details (student/subject/price) is allowed in any status; saving a completed
            // class re-bills it so its recognized income tracks the edit (see ClassRules.rebill).
            // A group lesson edits as a whole — every member's class moves and re-bills together.
            SheetAction(stringResource(R.string.action_edit), Icons.Filled.Edit, onClick = onEdit)
            // Move (reschedule) is offered for any status: moving a lesson to the future re-plans it,
            // moving it to the past just corrects its recorded time. A group lesson moves as one.
            SheetAction(stringResource(R.string.class_move), Icons.Filled.EditCalendar, onClick = onMove)
            // Delete is offered for any status. Deleting a completed class removes its recognized
            // income (the confirm dialog warns about this); the repository deletes the calendar
            // event and refreshes reminders regardless of status.
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SheetAction(
                stringResource(R.string.action_delete),
                Icons.Filled.DeleteOutline,
                onClick = onDelete,
                destructive = true,
            )
        }
    }
}

/**
 * The lesson's status as a segmented control.
 *
 * Shows all three, with the lesson's own selected — so the sheet states the fact as well as offering
 * the change. There is always one selected, [ClassSession.status] being one of the three even for a
 * lesson whose members were corrected apart; the segments it *enables* are still the union of what
 * those members can each move to ([GroupRules.sessionTransitions]).
 *
 * **The current status is enabled and does nothing.** [ClassRules.availableTransitions] excludes the
 * status a lesson is already in — quite rightly, it drove a menu of *actions* — so gating `enabled`
 * on it alone greyed out the one segment that is selected, every single time. The control then read
 * as broken rather than as informative. Being tappable-but-inert is how a segmented control's chosen
 * option behaves anyway; what stays disabled is the genuinely unreachable transition, which is real
 * information (a completed lesson can be cancelled but not un-completed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusSelector(row: ScheduleRow, onStatus: (ClassStatus) -> Unit) {
    val current = row.session.status
    val allowed = row.statusChoices
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            stringResource(R.string.class_status_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ClassStatus.entries.forEachIndexed { index, status ->
                val isCurrent = status == current
                SegmentedButton(
                    selected = isCurrent,
                    onClick = { if (!isCurrent) onStatus(status) },
                    enabled = isCurrent || status in allowed,
                    shape = SegmentedButtonDefaults.itemShape(index, ClassStatus.entries.size),
                    // No check mark. Its slot costs ~28dp of a segment that is one third of a phone,
                    // and the selected segment already carries a tinted container — which is
                    // Material's own affordance, and is what the tick was duplicating.
                    icon = {},
                ) { SegmentLabel(classStatusLabel(status)) }
            }
        }
    }
}

/**
 * A segment's label on **one line**, shrinking a step at a time rather than wrapping.
 *
 * The same rule and mechanism as the bottom bar's tab labels: a segment is a fixed fraction of the
 * screen, so on a narrow phone or at a large font scale the longest word genuinely does not fit — and
 * a wrapped label makes the row uneven, which is worse than a slightly smaller one. Capped at the
 * inherited size so a label with room to spare is drawn untouched.
 */
@Composable
private fun SegmentLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        // Only reachable if even the minimum overflows; wrapping is what we are avoiding, so cut the
        // word rather than add a line.
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = MinSegmentFontSize,
            maxFontSize = LocalTextStyle.current.fontSize.takeOrElse { FallbackSegmentFontSize },
        ),
    )
}

private val MinSegmentFontSize = 10.sp

/** Material's label size for a segmented button; used only if the style leaves the size unspecified. */
private val FallbackSegmentFontSize = 14.sp

@Composable
private fun SheetAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(20.dp))
        Text(label, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Picks a new date then time, then reports the combined instant. */
@Composable
private fun MoveClassFlow(
    start: java.time.Instant,
    onMoved: (java.time.Instant) -> Unit,
    onDone: () -> Unit,
) {
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    // Held in a local so the time step can use the date without asserting it: `pickedDate` is a `by`
    // delegate over mutable state, which Kotlin re-reads on each access and so cannot smart-cast, and
    // the `null` branch below is precisely what establishes that it is set.
    val date = pickedDate
    if (date == null) {
        DatePickerModal(
            initial = start.toLocalDate(),
            onConfirm = { pickedDate = it },
            onDismiss = onDone,
        )
    } else {
        TimePickerModal(
            initial = start.toLocalTime(),
            onConfirm = { time ->
                onMoved(date.atTime(time).atZone(ZoneId.systemDefault()).toInstant())
                onDone()
            },
            onDismiss = onDone,
        )
    }
}
