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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Today
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.repetit.R
import com.seblful.repetit.domain.ClassRules
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.TutoringClass
import com.seblful.repetit.ui.common.ConfirmDialog
import com.seblful.repetit.ui.common.ContentCard
import com.seblful.repetit.ui.common.DatePickerModal
import com.seblful.repetit.ui.common.EmptyState
import com.seblful.repetit.ui.common.StatusBadge
import com.seblful.repetit.ui.common.TimePickerModal
import com.seblful.repetit.ui.common.classModeLabel
import com.seblful.repetit.ui.common.classStatusColor
import com.seblful.repetit.ui.common.classStatusLabel
import com.seblful.repetit.ui.common.constrainedContent
import com.seblful.repetit.format.formatDay
import com.seblful.repetit.format.formatTime
import com.seblful.repetit.format.toLocalDate
import com.seblful.repetit.format.toLocalTime
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onAddClass: (LocalDate) -> Unit,
    onEditClass: (Long) -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsState()
    val date by viewModel.selectedDate.collectAsState()
    val canAddClass by viewModel.canAddClass.collectAsState()

    var sheetFor by remember { mutableStateOf<ScheduleRow?>(null) }
    var movingClass by remember { mutableStateOf<TutoringClass?>(null) }
    var deletingClass by remember { mutableStateOf<TutoringClass?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val needSetupMsg = stringResource(R.string.schedule_need_setup)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.schedule_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (canAddClass) onAddClass(date)
                else android.widget.Toast.makeText(context, needSetupMsg, android.widget.Toast.LENGTH_LONG).show()
            }) {
                Icon(Icons.Filled.Add, stringResource(R.string.action_add))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).constrainedContent()) {
            DayNavigator(
                date = date,
                onPrevious = viewModel::goToPreviousDay,
                onNext = viewModel::goToNextDay,
                onToday = viewModel::goToToday,
            )
            HorizontalDivider()
            if (rows.isEmpty()) {
                EmptyState(stringResource(R.string.schedule_empty))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(rows, key = { it.klass.id }) { row ->
                        ScheduleCard(row = row, onClick = { sheetFor = row })
                    }
                }
            }
        }
    }

    sheetFor?.let { row ->
        val klass = row.klass
        ClassActionsSheet(
            row = row,
            sheetState = rememberModalBottomSheetState(),
            onEdit = { onEditClass(klass.id); sheetFor = null },
            onStatus = { viewModel.setStatus(klass.id, it); sheetFor = null },
            onMove = { movingClass = klass; sheetFor = null },
            onDelete = { deletingClass = klass; sheetFor = null },
            onDismiss = { sheetFor = null },
        )
    }

    movingClass?.let { klass ->
        MoveClassFlow(
            klass = klass,
            onMoved = { newStart -> viewModel.move(klass.id, newStart) },
            onDone = { movingClass = null },
        )
    }

    deletingClass?.let { klass ->
        val confirmText = if (klass.status == ClassStatus.COMPLETED) {
            stringResource(R.string.class_delete_completed_confirm)
        } else {
            stringResource(R.string.class_delete_confirm)
        }
        ConfirmDialog(
            text = confirmText,
            onConfirm = { viewModel.delete(klass) },
            onDismiss = { deletingClass = null },
        )
    }
}

@Composable
private fun ScheduleCard(row: ScheduleRow, onClick: () -> Unit) {
    ContentCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.klass.start.formatTime(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(row.studentName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${row.subjectName} · ${classModeLabel(row.klass.mode)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            StatusBadge(
                text = classStatusLabel(row.klass.status),
                color = classStatusColor(row.klass.status),
            )
        }
    }
}

@Composable
private fun DayNavigator(
    date: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) { Icon(Icons.Filled.ChevronLeft, stringResource(R.string.action_back)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                date.atStartOfDay(ZoneId.systemDefault()).toInstant().formatDay(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onToday) { Icon(Icons.Filled.Today, stringResource(R.string.nav_schedule)) }
        }
        IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, stringResource(R.string.action_add)) }
    }
}

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
            // Header: which class these actions apply to.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${klass.start.formatTime()} · ${row.studentName}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${row.subjectName} · ${classModeLabel(klass.mode)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(
                    text = classStatusLabel(klass.status),
                    color = classStatusColor(klass.status),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Editing details (student/subject/price) is allowed in any status; saving a completed
            // class re-bills it so its recognized income tracks the edit (see ClassRules.rebill).
            SheetAction(stringResource(R.string.action_edit), Icons.Filled.Edit, onClick = onEdit)
            // Offer only the statuses this class can move to — never the one it is already in.
            ClassRules.availableTransitions(klass.status).forEach { target ->
                SheetAction(
                    stringResource(statusActionLabel(target)),
                    statusActionIcon(target),
                    onClick = { onStatus(target) },
                )
            }
            // Move (reschedule) is offered for any status: moving a class to the future re-plans it,
            // moving it to the past just corrects its recorded time.
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
    klass: TutoringClass,
    onMoved: (java.time.Instant) -> Unit,
    onDone: () -> Unit,
) {
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    if (pickedDate == null) {
        DatePickerModal(
            initial = klass.start.toLocalDate(),
            onConfirm = { pickedDate = it },
            onDismiss = onDone,
        )
    } else {
        TimePickerModal(
            initial = klass.start.toLocalTime(),
            onConfirm = { time ->
                val newStart = pickedDate!!.atTime(time).atZone(ZoneId.systemDefault()).toInstant()
                onMoved(newStart)
                onDone()
            },
            onDismiss = onDone,
        )
    }
}
