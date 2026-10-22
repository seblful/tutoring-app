package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.seblful.klas.R
import com.seblful.klas.ui.theme.KlasIcons
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/*
 * All three modals in this file confirm with «Готово» (`action_done`), never «Сохранить». A picker
 * hands a value back to the form that opened it and writes nothing; the form's own «Сохранить» is
 * what commits. They all said «Сохранить» once, which put two of them in a row on the booking
 * form — see the note at `action_done` in `strings.xml`.
 */

/**
 * Date picker. [onConfirm] reports the chosen date and [onDismiss] reports a cancel; the two are
 * separate so a multi-step flow (e.g. move = date then time) can advance on confirm without the
 * caller's cancel handler firing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onConfirm(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date)
                    }
                },
                // Clearing the typed date in the picker's text mode leaves nothing to hand back, and a
                // «Готово» that then did nothing read as a dead button.
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/**
 * Time picker. As with [DatePickerModal], [onConfirm] and [onDismiss] are separate and the caller
 * is responsible for closing the dialog, so confirming one step of a flow never triggers the
 * flow's cancel path.
 *
 * Material's time dialog: the dial, or the keyboard for whoever would rather type the time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerModal(
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
    )
    var typing by rememberSaveable { mutableStateOf(false) }
    val mode = if (typing) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime(state.hour, state.minute))
            }) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { TimePickerDialogDefaults.Title(displayMode = mode) },
        // The app's own toggle: Material's draws its icon for the mode it is in but names the one it leads to,
        // so one of the two always disagreed. This shows and says where a tap leads.
        modeToggleButton = {
            NamedIconButton(
                if (typing) KlasIcons.Outlined.Schedule else KlasIcons.Outlined.Keyboard,
                stringResource(if (typing) R.string.time_picker_to_dial else R.string.time_picker_to_keyboard),
                onClick = { typing = !typing },
            )
        },
    ) {
        if (typing) TimeInput(state = state) else TimePicker(state = state)
    }
}

/** How long something lasts, typed in hours and minutes — what a tap on the booking form's length opens. */
@Composable
fun DurationPickerModal(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    // Settings uses the same hours-and-minutes picker for the default lesson length.
    title: String? = null,
) {
    var hours by rememberSaveable { mutableStateOf((initialMinutes / 60).toString()) }
    var minutes by rememberSaveable { mutableStateOf((initialMinutes % 60).toString()) }
    val total = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        // Typed into, so a stray tap beside it keeps what was typed — see [KeepTypedInput].
        properties = KeepTypedInput,
        title = { Text(title ?: stringResource(R.string.class_duration_label)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
                CountField(
                    value = hours,
                    onValue = { hours = it },
                    label = stringResource(R.string.duration_hours_label),
                    max = 23,
                    modifier = Modifier.weight(1f),
                )
                CountField(
                    value = minutes,
                    onValue = { minutes = it },
                    label = stringResource(R.string.duration_minutes_label),
                    max = 59,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(total) },
                enabled = total > 0,
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun CountField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    max: Int,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed ->
            val digits = typed.filter(Char::isDigit)
            val number = digits.toIntOrNull()
            if (digits.isEmpty() || (number != null && number <= max)) onValue(digits)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
