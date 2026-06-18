package com.seblful.tutoringapp.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.seblful.tutoringapp.R
import com.seblful.tutoringapp.domain.ClassRules
import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.domain.model.TutoringClass
import com.seblful.tutoringapp.ui.common.ConfirmDialog
import com.seblful.tutoringapp.ui.common.DatePickerModal
import com.seblful.tutoringapp.ui.common.EmptyState
import com.seblful.tutoringapp.ui.common.TimePickerModal
import com.seblful.tutoringapp.ui.common.classStatusColor
import com.seblful.tutoringapp.ui.common.classStatusLabel
import com.seblful.tutoringapp.ui.common.formatDay
import com.seblful.tutoringapp.ui.common.formatTime
import com.seblful.tutoringapp.ui.common.toLocalDate
import com.seblful.tutoringapp.ui.common.toLocalTime
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

    var sheetFor by remember { mutableStateOf<TutoringClass?>(null) }
    var movingClass by remember { mutableStateOf<TutoringClass?>(null) }
    var deletingClass by remember { mutableStateOf<TutoringClass?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.schedule_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddClass(date) }) {
                Icon(Icons.Filled.Add, stringResource(R.string.action_add))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { it.klass.id }) { row ->
                        ListItem(
                            headlineContent = { Text("${row.klass.start.formatTime()} · ${row.studentName}") },
                            supportingContent = { Text(row.subjectName) },
                            trailingContent = {
                                Text(classStatusLabel(row.klass.status), color = classStatusColor(row.klass.status))
                            },
                            modifier = Modifier.clickable { sheetFor = row.klass },
                        )
                    }
                }
            }
        }
    }

    sheetFor?.let { klass ->
        ClassActionsSheet(
            klass = klass,
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
        ConfirmDialog(
            text = stringResource(R.string.class_delete_confirm),
            onConfirm = { viewModel.delete(klass) },
            onDismiss = { deletingClass = null },
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
    klass: TutoringClass,
    sheetState: androidx.compose.material3.SheetState,
    onEdit: () -> Unit,
    onStatus: (ClassStatus) -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            SheetAction(stringResource(R.string.action_edit), onClick = onEdit)
            // Only offer status changes that are legal from the current status.
            statusActionLabel(ClassStatus.COMPLETED)
                .takeIf { ClassRules.canTransition(klass.status, ClassStatus.COMPLETED) }
                ?.let { SheetAction(stringResource(it), onClick = { onStatus(ClassStatus.COMPLETED) }) }
            statusActionLabel(ClassStatus.MISSED)
                .takeIf { ClassRules.canTransition(klass.status, ClassStatus.MISSED) }
                ?.let { SheetAction(stringResource(it), onClick = { onStatus(ClassStatus.MISSED) }) }
            statusActionLabel(ClassStatus.CANCELLED)
                .takeIf { ClassRules.canTransition(klass.status, ClassStatus.CANCELLED) }
                ?.let { SheetAction(stringResource(it), onClick = { onStatus(ClassStatus.CANCELLED) }) }
            statusActionLabel(ClassStatus.SCHEDULED)
                .takeIf { klass.status != ClassStatus.SCHEDULED && ClassRules.canTransition(klass.status, ClassStatus.SCHEDULED) }
                ?.let { SheetAction(stringResource(it), onClick = { onStatus(ClassStatus.SCHEDULED) }) }
            SheetAction(stringResource(R.string.class_move), onClick = onMove)
            SheetAction(stringResource(R.string.action_delete), onClick = onDelete, destructive = true)
        }
    }
}

private fun statusActionLabel(status: ClassStatus): Int = when (status) {
    ClassStatus.COMPLETED -> R.string.class_mark_completed
    ClassStatus.MISSED -> R.string.class_mark_missed
    ClassStatus.CANCELLED -> R.string.class_mark_cancelled
    ClassStatus.SCHEDULED -> R.string.class_mark_scheduled
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    Text(
        text = label,
        color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 24.dp, vertical = 14.dp),
    )
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
