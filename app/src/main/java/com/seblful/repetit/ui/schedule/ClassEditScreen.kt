package com.seblful.repetit.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.repetit.R
import com.seblful.repetit.ui.common.DropdownField
import com.seblful.repetit.ui.common.DatePickerModal
import com.seblful.repetit.ui.common.ModeSelector
import com.seblful.repetit.ui.common.TimePickerModal
import com.seblful.repetit.ui.common.classModeLabel
import com.seblful.repetit.ui.common.constrainedContent
import com.seblful.repetit.ui.common.text
import com.seblful.repetit.format.formatDate
import com.seblful.repetit.format.formatTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassEditScreen(
    onBack: () -> Unit,
    viewModel: ClassEditViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsState()
    val conflict by viewModel.conflict.collectAsState()
    val students by viewModel.students.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val members by viewModel.members.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val subjectFixed by viewModel.subjectFixedByGroup.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    var showModeSelector by remember { mutableStateOf(false) }
    var showPriceField by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (viewModel.isNew) R.string.class_add_title else R.string.class_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // A taken time is as disqualifying as a missing field: the lesson could not happen.
                    TextButton(
                        onClick = { viewModel.save(onBack) },
                        enabled = form.isValid && conflict == null,
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .constrainedContent()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Who the lesson is for. Only offered while booking: an existing lesson already is one
            // kind or the other, and turning a student's class into a group lesson (or back) would
            // silently create or destroy other students' billed classes.
            if (viewModel.isNew) {
                TargetSelector(selected = form.target, onSelect = { viewModel.setTarget(it) })
            }

            when (form.target) {
                ClassTarget.STUDENT -> DropdownField(
                    label = stringResource(R.string.class_student_label),
                    selectedText = students.firstOrNull { it.id == form.studentId }?.name ?: "",
                    options = students.map { it.id to it.name },
                    onSelect = { id -> viewModel.selectStudent(id) },
                )

                ClassTarget.GROUP -> {
                    DropdownField(
                        label = stringResource(R.string.class_group_label),
                        selectedText = groups.firstOrNull { it.id == form.groupId }?.name ?: "",
                        options = groups.map { it.id to it.name },
                        onSelect = { id -> viewModel.selectGroup(id) },
                        // A booked lesson can't be handed to another group: its members' classes are
                        // billed history, and the other group is a different set of people.
                        enabled = viewModel.isNew,
                    )
                    if (groups.isEmpty()) {
                        Text(
                            stringResource(R.string.class_no_groups),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            val targetChosen = form.studentId != null || form.groupId != null
            DropdownField(
                label = stringResource(R.string.class_subject_label),
                selectedText = subjects.firstOrNull { it.id == form.subjectId }?.name ?: "",
                options = subjects.map { it.id to it.name },
                onSelect = { id -> viewModel.selectSubject(id) },
                // A group that has a subject decides it for every one of its lessons; offering a
                // dropdown with one entry would imply a choice that doesn't exist.
                enabled = targetChosen && !subjectFixed,
            )
            if (subjectFixed) {
                Text(
                    stringResource(R.string.class_subject_from_group),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (targetChosen && subjects.isEmpty()) {
                Text(
                    stringResource(
                        if (form.target == ClassTarget.GROUP) R.string.class_no_subjects_for_group
                        else R.string.class_no_subjects_for_student
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            PickerField(
                value = form.date?.let { it.atStartOfDay(ZoneId.systemDefault()).toInstant().formatDate() } ?: "",
                label = stringResource(R.string.class_date_label),
                onClick = { showDatePicker = true },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PickerField(
                    value = form.timeFrom?.formatHm() ?: "",
                    label = stringResource(R.string.class_time_from_label),
                    onClick = { showFromPicker = true },
                    modifier = Modifier.weight(1f),
                )
                PickerField(
                    value = form.timeTo?.formatHm() ?: "",
                    label = stringResource(R.string.class_time_to_label),
                    onClick = { showToPicker = true },
                    enabled = form.timeFrom != null,
                    modifier = Modifier.weight(1f),
                )
            }

            // Why the time won't do — said here rather than left to a greyed-out save button.
            val clash = conflict
            val timeProblem = when {
                // An end at or before the start is not a lesson (and 23:00 → 00:30 is the same case:
                // the times are wall-clock on one date, so a lesson can't cross midnight).
                form.timeFrom != null && form.timeTo != null && form.durationMinutes == null ->
                    stringResource(R.string.class_time_range_invalid)

                clash != null -> stringResource(
                    R.string.class_time_conflict,
                    clash.who,
                    "${clash.slot.start.formatTime()} – ${clash.slot.end.formatTime()}",
                )

                else -> null
            }
            if (timeProblem != null) {
                Text(
                    timeProblem,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (form.target == ClassTarget.GROUP) {
                // Who the lesson covers. While **booking**, each member can be dropped from this one
                // lesson — someone away this week is not off the roster, so the tick lives on the
                // lesson and the group is untouched. While **editing**, read-only: those classes are
                // billed history, and adding or dropping one here would invent or erase a lesson.
                if (members.isNotEmpty()) {
                    Text(
                        stringResource(R.string.class_group_members_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (viewModel.isNew) {
                        members.forEach { student ->
                            val included = student.id !in form.excludedMemberIds
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = included,
                                        onValueChange = { on -> viewModel.setMemberIncluded(student.id, on) },
                                    ),
                            ) {
                                Text(student.name, Modifier.weight(1f))
                                Checkbox(checked = included, onCheckedChange = null)
                            }
                        }
                        if (form.excludedMemberIds.containsAll(members.map { it.id })) {
                            Text(
                                stringResource(R.string.class_group_no_members_left),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        Text(
                            members.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // The group's rate, pre-filled from the group and editable for this one lesson. Shown
                // plainly rather than behind a tap: for a group lesson it is *n* students' money.
                OutlinedTextField(
                    value = form.priceText,
                    onValueChange = { text -> viewModel.update { it.copy(priceText = text) } },
                    label = { Text(stringResource(R.string.class_group_price_label)) },
                    isError = form.priceText.isNotBlank() && form.price == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (showPriceField) {
                // Price is hidden behind its current value (like the location row); tap to edit.
                OutlinedTextField(
                    value = form.priceText,
                    onValueChange = { text -> viewModel.update { it.copy(priceText = text) } },
                    label = { Text(stringResource(R.string.price_label)) },
                    isError = form.priceText.isNotBlank() && form.price == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = "${stringResource(R.string.price_label)}: " +
                        (form.price?.text() ?: stringResource(R.string.price_not_set)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPriceField = true }
                        .padding(vertical = 12.dp),
                )
            }

            if (showModeSelector) {
                Text(stringResource(R.string.class_location_label), style = MaterialTheme.typography.labelLarge)
                ModeSelector(
                    selected = form.mode,
                    onSelect = { selected -> viewModel.update { it.copy(mode = selected) } },
                )
            } else {
                // Hidden by default; tap the current value to reveal the selector.
                Text(
                    text = "${stringResource(R.string.class_location_label)}: ${classModeLabel(form.mode)}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showModeSelector = true }
                        .padding(vertical = 12.dp),
                )
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            initial = form.date ?: LocalDate.now(ZoneId.systemDefault()),
            onConfirm = { date ->
                viewModel.update { it.copy(date = date) }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showFromPicker) {
        TimePickerModal(
            initial = viewModel.startPickerDefault(),
            onConfirm = { time ->
                viewModel.setTimeFrom(time)
                showFromPicker = false
            },
            onDismiss = { showFromPicker = false },
        )
    }
    if (showToPicker) {
        TimePickerModal(
            initial = viewModel.endPickerDefault(),
            onConfirm = { time ->
                viewModel.setTimeTo(time)
                showToPicker = false
            },
            onDismiss = { showToPicker = false },
        )
    }
}

private fun LocalTime.formatHm(): String = "%02d:%02d".format(hour, minute)

/** Student-or-group chooser for a new booking. */
@Composable
private fun TargetSelector(selected: ClassTarget, onSelect: (ClassTarget) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClassTarget.entries.forEach { target ->
            FilterChip(
                selected = selected == target,
                onClick = { onSelect(target) },
                label = {
                    Text(
                        stringResource(
                            when (target) {
                                ClassTarget.STUDENT -> R.string.class_target_student
                                ClassTarget.GROUP -> R.string.class_target_group
                            }
                        )
                    )
                },
            )
        }
    }
}

/**
 * A non-editable field that opens a picker when tapped. Unlike a plain disabled field, its text and
 * border keep their normal (non-faded) colours, so a chosen value reads as a real selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(modifier.fillMaxWidth().let { if (enabled) it.clickable { onClick() } else it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = false, // disabled so the Box catches the tap; colours below undo the fade
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

// DropdownField now lives in ui/common/Components.kt — the group form picks a subject the same way.
