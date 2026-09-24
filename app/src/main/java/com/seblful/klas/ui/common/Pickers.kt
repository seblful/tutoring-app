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
import androidx.compose.material3.TimePicker
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/*
 * All three modals in this file confirm with «Готово» (`action_done`), never «Сохранить». A picker
 * hands a value back to the form that opened it and writes nothing; the form's own «Сохранить» is
 * what commits. They all said «Сохранить» once, which put two of them in a row on the booking
 * form — see the note at `action_done` in `strings.xml`.
 */

/**
 * Date picker. [onConfirm] reports the chosen date and [onDismiss] reports a cancel; the two are
 * separate so a multi-step flow (e.g. move = date then time) can advance on confirm without the
 * caller's cancel handler firing. The caller closes the dialog itself (in [onConfirm] or by
 * removing this composable). They are never both invoked for one interaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
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
 * **The dial reads 12- or 24-hour from the phone** — which is what `rememberTimePickerState` does when
 * `is24Hour` is left out. It used to be pinned to `true`, so a phone set to a 12-hour clock was still
 * handed a 24-hour dial to book a lesson on: the app answering a question the phone had already
 * answered. The text the lesson is then read back as follows the same setting ([timeText]), so a time is
 * set and shown on one clock rather than two. `ClockFormatTest` keeps both from drifting.
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
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(state.hour, state.minute))
            }) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = { TimePicker(state = state) },
    )
}

/**
 * How long something lasts, in hours and minutes — the booking form's «Другая» length.
 *
 * **A length, not an end time.** The form asks how long a lesson runs, and the answer to that cannot be
 * an impossible lesson: there is no reversed range to interpret, so nothing downstream needs a ceiling
 * on how long a lesson may be to tell one from a mis-tap. The end this implies is shown back on the form
 * (`ClassForm.ends`), midnight or no midnight. What used to open here was a clock dial setting the end
 * directly, and a 23:00 start with the dial left at 00:00 gave the form a negative lesson.
 *
 * The fields bound themselves: hours below a day and minutes below an hour, so the longest length this
 * can return is 23 ч 59 мин. Confirm stays disabled at zero, because a lesson of no length is not one.
 *
 * Seeded **once**, deliberately: from the moment it opens the fields are the tutor's, so the lesson
 * length arriving from settings a moment later cannot move them under their fingers.
 */
@Composable
fun DurationPickerModal(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    // What length is being asked about. Defaulted to the lesson's, which is what this was written for;
    // Settings asks the same hours-and-minutes question about how far ahead a reminder should arrive.
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

/**
 * How many lessons a repeating booking writes — the booking form's «Другое».
 *
 * The same shape as [DurationPickerModal] and for the same reason: the chips carry the answers a tutor
 * actually gives (a month, two months, a term) and this asks for the rest. [max] is a real ceiling rather
 * than a formality — it is how many rows one «Сохранить» may put in the database, each with a calendar
 * event and a place in the reminder plan — so it is stated in the field's own label instead of being
 * discovered by typing into a field that silently stops accepting digits.
 *
 * Seeded once, deliberately, as the duration dialog is: from the moment it opens the field is the
 * tutor's.
 */
@Composable
fun CountPickerModal(
    title: String,
    label: String,
    initial: Int,
    max: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var count by rememberSaveable { mutableStateOf(initial.toString()) }
    val total = count.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = KeepTypedInput,
        title = { Text(title) },
        text = {
            CountField(
                value = count,
                onValue = { count = it },
                label = label,
                max = max,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(total) },
                // Zero lessons is not a booking, exactly as a lesson of no length is not a lesson.
                enabled = total > 0,
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * A small whole-number field, bounded at [max].
 *
 * Out-of-range and non-numeric keystrokes are **dropped** rather than shown and then complained about:
 * the value being edited is a count with an obvious ceiling, so there is nothing to explain and no
 * invalid state worth being in. Empty is allowed, so the field can be cleared and retyped.
 */
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
