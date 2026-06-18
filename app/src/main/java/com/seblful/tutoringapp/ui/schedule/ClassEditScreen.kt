package com.seblful.tutoringapp.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import com.seblful.tutoringapp.ui.common.TimePickerModal
import com.seblful.tutoringapp.format.formatDate
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
    var showTimePicker by remember { mutableStateOf(false) }

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
                onSelect = { id -> viewModel.update { it.copy(studentId = id) } },
            )
            DropdownField(
                label = stringResource(R.string.class_subject_label),
                selectedText = subjects.firstOrNull { it.id == form.subjectId }?.name ?: "",
                options = subjects.map { it.id to it.name },
                onSelect = { id -> viewModel.update { it.copy(subjectId = id) } },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ReadOnlyPickerField(
                    value = form.date.atStartOfDay(ZoneId.systemDefault()).toInstant().formatDate(),
                    label = stringResource(R.string.class_date_label),
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                )
                ReadOnlyPickerField(
                    value = "%02d:%02d".format(form.time.hour, form.time.minute),
                    label = stringResource(R.string.class_time_label),
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = if (form.durationMinutes == 0) "" else form.durationMinutes.toString(),
                onValueChange = { text ->
                    viewModel.update { it.copy(durationMinutes = text.filter { c -> c.isDigit() }.toIntOrNull() ?: 0) }
                },
                label = { Text(stringResource(R.string.class_duration_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.location,
                onValueChange = { text -> viewModel.update { it.copy(location = text) } },
                label = { Text(stringResource(R.string.class_location_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

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
                            viewModel.update { it.copy(weeks = text.filter { c -> c.isDigit() }.toIntOrNull() ?: 0) }
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
            initial = form.date,
            onConfirm = { date -> viewModel.update { it.copy(date = date) } },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        TimePickerModal(
            initial = form.time,
            onConfirm = { time -> viewModel.update { it.copy(time = time) } },
            onDismiss = { showTimePicker = false },
        )
    }
}

/** A non-editable field that opens a picker when tapped (disabled field can't catch clicks itself). */
@Composable
private fun ReadOnlyPickerField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Box(modifier.clickable { onClick() }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
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
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(id); expanded = false },
                )
            }
        }
    }
}
