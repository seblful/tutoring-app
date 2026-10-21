package com.seblful.klas.ui.schedule.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.schedule.Recurrence
import com.seblful.klas.domain.schedule.Repeat
import com.seblful.klas.format.formatDate
import com.seblful.klas.ui.common.CountPickerModal
import com.seblful.klas.ui.common.DatePickerModal
import com.seblful.klas.ui.common.DurationPickerModal
import com.seblful.klas.ui.common.PickerField
import com.seblful.klas.ui.common.PresetChips
import com.seblful.klas.ui.common.SegmentedChoice
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TimePickerModal
import com.seblful.klas.ui.common.durationLabel
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.schedule.timeConflictText
import kotlinx.datetime.LocalDate

/*
 * The booking form's «When» section — see [WhenSection] — and the pickers it opens.
 */

private enum class WhenPicker { DATE, START, LENGTH, COUNT }

/** When: the day and the start, how long, and — only while booking — how many times. */
@Composable
internal fun WhenSection(state: ClassFormState, viewModel: ClassEditViewModel) {
    val form = state.form
    val durations by viewModel.durationOptions.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<WhenPicker?>(null) }
    // Padded down so the outlined fields' floating labels clear the section title.
    Row(Modifier.padding(top = Spacing.tight), horizontalArrangement = Arrangement.spacedBy(Spacing.gutter)) {
        PickerField(
            value = form.date?.formatDate().orEmpty(),
            label = stringResource(R.string.class_date_label),
            onClick = { picker = WhenPicker.DATE },
            modifier = Modifier.weight(1.4f),
        )
        // Marked as the field that is wrong — a start and a length cannot be impossible, so a taken hour is
        // the only thing that can be. The sentence goes full-width under the row: in this narrow field it
        // wrapped to five lines.
        PickerField(
            value = form.timeFrom?.timeText().orEmpty(),
            label = stringResource(R.string.class_time_from_label),
            onClick = { picker = WhenPicker.START },
            modifier = Modifier.weight(1f),
            isError = state.conflictBlocksSave,
        )
    }
    state.conflict?.let { conflict ->
        Text(
            if (form.repeat.repeats) {
                stringResource(R.string.class_series_availability, state.availableOccurrences)
            } else {
                timeConflictText(conflict)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.conflictBlocksSave) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }

    // How long, not when it ends — a length is what the lesson is stored as. The end is its consequence.
    DurationPicker(
        options = durations,
        selected = form.durationMinutes,
        ends = state.ending,
        onSelect = { minutes -> viewModel.change(BookingIntent.LastsFor(minutes)) },
        onCustom = { picker = WhenPicker.LENGTH },
    )

    // **Only while booking**: repeating is a property of the act, not of a lesson already in the database,
    // and offering it there would read as an invitation to turn one lesson into twelve by editing it.
    if (viewModel.isNew) {
        RepeatPicker(
            repeat = form.repeat,
            count = form.repeatCount,
            until = form.repeatsUntil,
            onRepeat = { repeat -> viewModel.change(BookingIntent.Edit { it.copy(repeat = repeat) }) },
            onCount = { count -> viewModel.change(BookingIntent.Edit { it.copy(repeatCount = count) }) },
            onCustomCount = { picker = WhenPicker.COUNT },
        )
        if (form.repeat.repeats) {
            Text(
                stringResource(R.string.class_repeat_independent_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    WhenPickerDialog(picker, viewModel, onDone = { picker = null })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationPicker(
    options: List<Int>,
    selected: Int?,
    ends: ClassForm.Ending?,
    onSelect: (Int) -> Unit,
    onCustom: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        // A flow, so the consequence moves to a line of its own whole rather than being squeezed into a
        // column beside the label — «Длительность · до 00:00 (следующего дня)» at a large font scale.
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.class_duration_label), style = MaterialTheme.typography.labelLarge)
            if (ends != null) {
                Text(
                    " · " + endsAtText(ends),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PresetChips(
            presets = options,
            selected = selected,
            label = { minutes -> durationLabel(minutes) },
            customLabel = stringResource(R.string.class_duration_custom),
            onSelect = onSelect,
            onCustom = onCustom,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatPicker(
    repeat: Repeat,
    count: Int,
    until: LocalDate?,
    onRepeat: (Repeat) -> Unit,
    onCount: (Int) -> Unit,
    onCustomCount: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.inline)) {
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.class_repeat_label), style = MaterialTheme.typography.labelLarge)
            // «по 24 сентября» — the last day the run reaches, shown once there is a date to count from.
            if (until != null) {
                Text(
                    " · " + stringResource(R.string.class_repeat_until, until.formatDate()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SegmentedChoice(
            options = Repeat.entries,
            selected = repeat,
            onSelect = onRepeat,
            label = { option -> repeatLabel(option) },
        )
        if (repeat.repeats) {
            Text(stringResource(R.string.class_repeat_count_label), style = MaterialTheme.typography.labelLarge)
            PresetChips(
                presets = Recurrence.COMMON_COUNTS,
                selected = count,
                label = { option -> option.toString() },
                customLabel = stringResource(R.string.class_repeat_count_custom),
                onSelect = onCount,
                onCustom = onCustomCount,
            )
        }
    }
}

@Composable
internal fun repeatLabel(repeat: Repeat): String = stringResource(
    when (repeat) {
        Repeat.NONE -> R.string.class_repeat_none
        Repeat.WEEKLY -> R.string.class_repeat_weekly
        Repeat.FORTNIGHTLY -> R.string.class_repeat_fortnightly
    },
)

/** The picker the section has open, if any — each a dialog over the form. */
@Composable
private fun WhenPickerDialog(picker: WhenPicker?, viewModel: ClassEditViewModel, onDone: () -> Unit) {
    when (picker) {
        WhenPicker.DATE -> DatePickerModal(
            initial = viewModel.datePickerDefault(),
            onConfirm = { date -> viewModel.change(BookingIntent.Edit { it.copy(date = date) }); onDone() },
            onDismiss = onDone,
        )
        WhenPicker.START -> TimePickerModal(
            initial = viewModel.startPickerDefault(),
            onConfirm = { time -> viewModel.change(BookingIntent.StartAt(time)); onDone() },
            onDismiss = onDone,
        )
        WhenPicker.LENGTH -> DurationPickerModal(
            initialMinutes = viewModel.durationPickerDefault(),
            onConfirm = { minutes -> viewModel.change(BookingIntent.LastsFor(minutes)); onDone() },
            onDismiss = onDone,
        )
        WhenPicker.COUNT -> CountPickerModal(
            title = stringResource(R.string.class_repeat_count_title),
            label = stringResource(R.string.class_repeat_count_field, Recurrence.MAX_COUNT),
            initial = viewModel.repeatCountPickerDefault(),
            max = Recurrence.MAX_COUNT,
            onConfirm = { count -> viewModel.change(BookingIntent.Edit { it.copy(repeatCount = count) }); onDone() },
            onDismiss = onDone,
        )
        null -> Unit
    }
}
