package com.seblful.tutoringapp.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.seblful.tutoringapp.R
import com.seblful.tutoringapp.ui.common.DatePickerModal
import com.seblful.tutoringapp.ui.common.ModeSelector
import com.seblful.tutoringapp.ui.common.TimePickerModal
import com.seblful.tutoringapp.ui.common.classModeLabel
import com.seblful.tutoringapp.ui.common.text
import com.seblful.tutoringapp.format.formatDate
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DropdownField(
                label = stringResource(R.string.class_student_label),
                selectedText = students.firstOrNull { it.id == form.studentId }?.name ?: "",
                options = students.map { it.id to it.name },
                onSelect = { id -> viewModel.selectStudent(id) },
            )
            DropdownField(
                label = stringResource(R.string.class_subject_label),
                selectedText = subjects.firstOrNull { it.id == form.subjectId }?.name ?: "",
                options = subjects.map { it.id to it.name },
                onSelect = { id -> viewModel.selectSubject(id) },
                enabled = form.studentId != null,
            )
            if (form.studentId != null && subjects.isEmpty()) {
                Text(
                    stringResource(R.string.class_no_subjects_for_student),
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

            // Price is hidden behind its current value (like the location row); tap to edit.
            if (showPriceField) {
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

            if (viewModel.isNew) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.class_repeat_weekly))
                    Switch(
                        checked = form.repeatWeekly,
                        onCheckedChange = { on -> viewModel.update { it.copy(repeatWeekly = on) } },
                    )
                }
                if (form.repeatWeekly) {
                    OutlinedTextField(
                        value = if (form.weeks == 0) "" else form.weeks.toString(),
                        onValueChange = { text ->
                            val n = (text.filter { c -> c.isDigit() }.toIntOrNull() ?: 0)
                                .coerceAtMost(ClassForm.MAX_WEEKS)
                            viewModel.update { it.copy(weeks = n) }
                        },
                        label = { Text(stringResource(R.string.class_repeat_weeks)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
            initial = form.timeFrom ?: LocalTime.of(15, 0),
            onConfirm = { time ->
                viewModel.setTimeFrom(time)
                showFromPicker = false
            },
            onDismiss = { showFromPicker = false },
        )
    }
    if (showToPicker) {
        TimePickerModal(
            initial = form.timeTo ?: form.timeFrom?.plusHours(1) ?: LocalTime.of(16, 0),
            onConfirm = { time ->
                viewModel.setTimeTo(time)
                showToPicker = false
            },
            onDismiss = { showToPicker = false },
        )
    }
}

private fun LocalTime.formatHm(): String = "%02d:%02d".format(hour, minute)

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
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
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
