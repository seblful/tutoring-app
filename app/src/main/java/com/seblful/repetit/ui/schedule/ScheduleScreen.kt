package com.seblful.repetit.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.repetit.R
import com.seblful.repetit.domain.GroupRules
import com.seblful.repetit.domain.LessonKind
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.ui.common.ConfirmDialog
import com.seblful.repetit.ui.common.ContentCard
import com.seblful.repetit.ui.common.DatePickerModal
import com.seblful.repetit.ui.common.EmptyState
import com.seblful.repetit.ui.common.LessonKindIcon
import com.seblful.repetit.ui.common.StatusBadge
import com.seblful.repetit.ui.common.TimePickerModal
import com.seblful.repetit.ui.common.classModeLabel
import com.seblful.repetit.ui.common.classStatusColor
import com.seblful.repetit.ui.common.classStatusLabel
import com.seblful.repetit.ui.common.constrainedContent
import com.seblful.repetit.format.formatDay
import com.seblful.repetit.format.formatMonth
import com.seblful.repetit.format.formatTime
import com.seblful.repetit.format.formatWeekRange
import com.seblful.repetit.format.toLocalDate
import com.seblful.repetit.format.toLocalTime
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onAddClass: () -> Unit,
    onEditClass: (Long) -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    // One state for the body: only the active view's lessons are assembled (see ScheduleContent).
    val content by viewModel.content.collectAsState()
    val date by viewModel.selectedDate.collectAsState()
    val canAddClass by viewModel.canAddClass.collectAsState()
    val today = LocalDate.now(ZoneId.systemDefault())
    val viewMode by viewModel.viewMode.collectAsState()
    val month by viewModel.visibleMonth.collectAsState()
    val grid by viewModel.monthGrid.collectAsState()
    val week by viewModel.weekDays.collectAsState()
    val loads by viewModel.dayLoads.collectAsState()
    val moveConflict by viewModel.moveConflict.collectAsState()

    // The sheet acts on the whole lesson in it, a group's included: its actions are the lesson's
    // time and status, and who is in one is changed on the lesson's own form.
    var sheetFor by remember { mutableStateOf<ScheduleRow?>(null) }
    var movingRow by remember { mutableStateOf<ScheduleRow?>(null) }
    var deletingRow by remember { mutableStateOf<ScheduleRow?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val needSetupMsg = stringResource(R.string.schedule_need_setup)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.schedule_title)) },
                actions = {
                    ViewModePicker(selected = viewMode, onSelect = viewModel::setViewMode)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (canAddClass) onAddClass()
                else android.widget.Toast.makeText(context, needSetupMsg, android.widget.Toast.LENGTH_LONG).show()
            }) {
                Icon(Icons.Filled.Add, stringResource(R.string.action_add))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).constrainedContent()) {
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
                    ScheduleViewMode.DAY -> dayList(content.rows, onRowClick = { sheetFor = it })

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
                                    stringResource(R.string.schedule_week_empty),
                                    Modifier.heightIn(min = 200.dp),
                                )
                            }
                        } else {
                            content.sections.forEach { section ->
                                item(key = "day-${section.date}", contentType = "day-header") {
                                    DaySectionHeader(section.date, isSelected = section.date == date)
                                }
                                items(section.rows, key = { it.klass.id }) { row ->
                                    ScheduleCard(
                                        row = row,
                                        onClick = { sheetFor = row },
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
                        dayList(content.rows, onRowClick = { sheetFor = it })
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

    // The move is refused when that hour is already taken; say which lesson holds it.
    moveConflict?.let { clash ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMoveConflict,
            text = {
                Text(
                    stringResource(
                        R.string.class_time_conflict,
                        clash.who,
                        "${clash.slot.start.formatTime()} – ${clash.slot.end.formatTime()}",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMoveConflict) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }

    deletingRow?.let { row ->
        val hasCompleted = row.classes.any { it.status == ClassStatus.COMPLETED }
        val count = row.session.size
        val confirmText = when {
            // Deleting a completed lesson takes its recognized income with it — say so first.
            hasCompleted && row.isGroup ->
                pluralStringResource(R.plurals.class_delete_group_completed_confirm, count, count)
            hasCompleted -> stringResource(R.string.class_delete_completed_confirm)
            row.isGroup -> pluralStringResource(R.plurals.class_delete_group_confirm, count, count)
            else -> stringResource(R.string.class_delete_confirm)
        }
        ConfirmDialog(
            text = confirmText,
            onConfirm = { viewModel.delete(row.session) },
            onDismiss = { deletingRow = null },
        )
    }
}

/** The selected day's classes, or an empty state. Shared by the day and month views. */
private fun LazyListScope.dayList(rows: List<ScheduleRow>, onRowClick: (ScheduleRow) -> Unit) {
    if (rows.isEmpty()) {
        item(key = "empty") {
            EmptyState(stringResource(R.string.schedule_empty), Modifier.heightIn(min = 200.dp))
        }
    } else {
        items(rows, key = { it.klass.id }) { row ->
            ScheduleCard(
                row = row,
                onClick = { onRowClick(row) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
            )
        }
    }
}

/** Chooses between the day, week and month views. */
@Composable
private fun ViewModePicker(selected: ScheduleViewMode, onSelect: (ScheduleViewMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.CalendarMonth, stringResource(R.string.schedule_view_mode))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ScheduleViewMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(viewModeLabel(mode))) },
                    onClick = { onSelect(mode); expanded = false },
                    trailingIcon = {
                        // Only the active mode is ticked; the others leave the column empty.
                        if (mode == selected) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                )
            }
        }
    }
}

private fun viewModeLabel(mode: ScheduleViewMode): Int = when (mode) {
    ScheduleViewMode.DAY -> R.string.schedule_view_day
    ScheduleViewMode.WEEK -> R.string.schedule_view_week
    ScheduleViewMode.MONTH -> R.string.schedule_view_month
}

@Composable
private fun ScheduleCard(row: ScheduleRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ContentCard(onClick = onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.klass.start.formatTime(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Both kinds are marked, not just the group ones: an icon that appears only
                    // sometimes reads as "this lesson has something extra", which leaves an individual
                    // lesson looking like a lesson with a missing icon rather than an individual one.
                    LessonKindIcon(LessonKind.of(row.klass), Modifier.padding(end = 6.dp))
                    Text(row.title, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "${row.subjectName} · ${classModeLabel(row.klass.mode)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (row.isGroup) {
                    Text(
                        row.members.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            SessionStatusBadge(row)
        }
    }
}

/**
 * A session's status: the one its classes share, or «разные» when a member has been corrected on
 * their own (an absentee cancelled while the rest were completed).
 */
@Composable
private fun SessionStatusBadge(row: ScheduleRow) {
    val common = row.session.commonStatus
    if (common != null) {
        StatusBadge(text = classStatusLabel(common), color = classStatusColor(common))
    } else {
        StatusBadge(
            text = stringResource(R.string.class_status_mixed),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayNavigator(
    date: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    PageNavigator(
        title = date.atStartOfDay(ZoneId.systemDefault()).toInstant().formatDay(),
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
        text = date.atStartOfDay(ZoneId.systemDefault()).toInstant().formatDay(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
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
        text = date.atStartOfDay(ZoneId.systemDefault()).toInstant().formatDay(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
    )
}

/**
 * What can be done to the lesson that was tapped: edit it, move it, change its status, delete it.
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
                        "${klass.start.formatTime()} · ${row.title}",
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
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Editing details (student/subject/price) is allowed in any status; saving a completed
            // class re-bills it so its recognized income tracks the edit (see ClassRules.rebill).
            // A group lesson edits as a whole — every member's class moves and re-bills together.
            SheetAction(stringResource(R.string.action_edit), Icons.Filled.Edit, onClick = onEdit)
            // Offer only the statuses this lesson can move to — never one it is already wholly in.
            // For a group, that is the union across its members, and the change is applied to the
            // members it is legal for (see GroupRules.applicableTo).
            GroupRules.sessionTransitions(row.session).forEach { target ->
                SheetAction(
                    stringResource(statusActionLabel(target)),
                    statusActionIcon(target),
                    onClick = { onStatus(target) },
                )
            }
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

private fun statusActionLabel(status: ClassStatus): Int = when (status) {
    ClassStatus.COMPLETED -> R.string.class_mark_completed
    ClassStatus.CANCELLED -> R.string.class_mark_cancelled
    ClassStatus.SCHEDULED -> R.string.class_mark_scheduled
}

private fun statusActionIcon(status: ClassStatus): ImageVector = when (status) {
    ClassStatus.COMPLETED -> Icons.Filled.CheckCircle
    ClassStatus.CANCELLED -> Icons.Filled.Cancel
    ClassStatus.SCHEDULED -> Icons.Filled.Event
}

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
    if (pickedDate == null) {
        DatePickerModal(
            initial = start.toLocalDate(),
            onConfirm = { pickedDate = it },
            onDismiss = onDone,
        )
    } else {
        TimePickerModal(
            initial = start.toLocalTime(),
            onConfirm = { time ->
                val newStart = pickedDate!!.atTime(time).atZone(ZoneId.systemDefault()).toInstant()
                onMoved(newStart)
                onDone()
            },
            onDismiss = onDone,
        )
    }
}
