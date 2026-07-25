package com.seblful.repetit.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import com.seblful.repetit.format.MoneyInput
import com.seblful.repetit.ui.common.DatePickerModal
import com.seblful.repetit.ui.common.ModeSelector
import com.seblful.repetit.ui.common.TimePickerModal
import com.seblful.repetit.ui.common.classModeLabel
import com.seblful.repetit.ui.common.constrainedContent
import com.seblful.repetit.ui.common.text
import com.seblful.repetit.format.formatDate
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
    val students by viewModel.students.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val members by viewModel.members.collectAsState()
    val subjects by viewModel.subjects.collectAsState()

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
                    TextButton(onClick = { viewModel.save(onBack) }, enabled = form.isValid) {
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
                enabled = targetChosen,
            )
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

            if (form.target == ClassTarget.GROUP) {
                // One rate per member, always visible: a group lesson is billed person by person, so
                // who is being charged what is the substance of the booking, not a detail to hide.
                Text(
                    stringResource(R.string.class_group_prices_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (form.subjectId == null) {
                    Text(
                        stringResource(R.string.class_group_pick_subject_first),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                members.forEach { member ->
                    val text = form.memberPrices[member.id] ?: ""
                    // Rates only make sense once a subject is chosen (they are pre-filled from it),
                    // and only a row that is actually editable can be flagged as needing a value —
                    // a row still waiting for its pre-fill isn't the tutor's problem.
                    val editable = form.subjectId != null && member.id in form.memberPrices
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(member.name, Modifier.weight(1f))
                        OutlinedTextField(
                            value = text,
                            onValueChange = { viewModel.setMemberPrice(member.id, it) },
                            label = { Text(stringResource(R.string.price_label)) },
                            isError = editable && MoneyInput.rate(text) == null,
                            enabled = editable,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.width(150.dp),
                        )
                    }
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    selectedText: String,
    options: List<Pair<Long, String>>,
    onSelect: (Long) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(id); expanded = false },
                )
            }
        }
    }
}
