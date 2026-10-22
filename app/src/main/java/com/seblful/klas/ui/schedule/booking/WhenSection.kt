package com.seblful.klas.ui.schedule.booking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.schedule.TimeConflict
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDay
import com.seblful.klas.ui.common.DatePickerModal
import com.seblful.klas.ui.common.DurationPickerModal
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.common.PresetChips
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TimePickerModal
import com.seblful.klas.ui.common.durationLabel
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.schedule.timeConflictText
import com.seblful.klas.ui.theme.KlasIcons

/*
 * The booking form's «When» section — see [WhenSection].
 */

/**
 * When: the day, stepped a day at a time or picked in the date dialog; the start, stepped
 * [STEP_MINUTES] at a time or typed in the time dialog; and how long, folded to a line. The day usually
 * comes from the schedule the booking was opened on, so neither needs a calendar on the form. Whether it
 * repeats is a section of its own, [RepeatSection].
 */
@Composable
internal fun WhenSection(state: ClassFormState, viewModel: ClassEditViewModel) {
    val form = state.form
    val durations by viewModel.durationOptions.collectAsStateWithLifecycle()
    var pickingDay by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var customLength by remember { mutableStateOf(false) }

    DayRow(
        state,
        onPrevious = { viewModel.moveDay(-1) },
        onNext = { viewModel.moveDay(1) },
        onPick = { pickingDay = true },
    )
    Stepper(state, onStep = viewModel::step, onPick = { pickingTime = true })
    state.conflict?.let { conflict -> ConflictLine(conflict, state) }

    // How long, not when it ends — a length is what the lesson is stored as. The end is its consequence.
    Fold(
        label = { Text(stringResource(R.string.class_duration_label), style = MaterialTheme.typography.labelLarge) },
        summary = lengthSummary(form.durationMinutes, state.ending),
    ) {
        PresetChips(
            presets = durations,
            selected = form.durationMinutes,
            label = { minutes -> durationLabel(minutes) },
            customLabel = stringResource(R.string.class_duration_custom),
            onSelect = { minutes -> viewModel.change(BookingIntent.LastsFor(minutes)) },
            onCustom = { customLength = true },
        )
    }

    if (pickingDay) {
        DatePickerModal(
            initial = viewModel.datePickerDefault(),
            onConfirm = { date ->
                viewModel.change(BookingIntent.Edit { it.copy(date = date) })
                pickingDay = false
            },
            onDismiss = { pickingDay = false },
        )
    }
    if (pickingTime) {
        TimePickerModal(
            initial = viewModel.timePickerDefault(),
            onConfirm = { time -> viewModel.change(BookingIntent.StartAt(time)); pickingTime = false },
            onDismiss = { pickingTime = false },
        )
    }
    if (customLength) {
        DurationPickerModal(
            initialMinutes = viewModel.durationPickerDefault(),
            onConfirm = { minutes -> viewModel.change(BookingIntent.LastsFor(minutes)); customLength = false },
            onDismiss = { customLength = false },
        )
    }
}

/** «‹ Friday, 23 October ›» over how many other lessons the day holds; the date itself opens the dialog. */
@Composable
private fun DayRow(state: ClassFormState, onPrevious: () -> Unit, onNext: () -> Unit, onPick: () -> Unit) {
    val date = state.form.date
    val pick = stringResource(R.string.class_day_pick)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NamedIconButton(KlasIcons.Filled.ChevronLeft, stringResource(R.string.class_day_previous), onPrevious)
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = MinTouchTarget)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = pick, role = Role.Button, onClick = onPick)
                .testTag(DAY_TAG),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                date?.formatDay() ?: pick,
                style = MaterialTheme.typography.titleMedium,
                color = if (date == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            state.timeline?.let { day ->
                val others = day.booked.size
                Text(
                    if (others == 0) {
                        stringResource(R.string.class_day_free)
                    } else {
                        pluralStringResource(R.plurals.lessons_count, others, others)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        NamedIconButton(KlasIcons.Filled.ChevronRight, stringResource(R.string.class_day_next), onNext)
    }
}

/**
 * The start and end in large numbers between a step earlier and a step later — see
 * [ClassFormState.stepped]. A tap on the numbers opens the time dialog. Red while the time is taken.
 */
@Composable
private fun Stepper(state: ClassFormState, onStep: (later: Boolean) -> Unit, onPick: () -> Unit) {
    val slot = state.slot
    val colors = MaterialTheme.colorScheme
    val pick = stringResource(R.string.class_time_pick)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.inline),
    ) {
        StepButton(
            KlasIcons.Filled.Remove,
            stringResource(R.string.class_step_earlier),
            enabled = state.stepped(later = false) != null,
            onClick = { onStep(false) },
        )
        Column(
            Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = pick, role = Role.Button, onClick = onPick)
                .padding(vertical = Spacing.tight)
                .testTag(TIME_TAG),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                slot?.let { "${it.start.timeText()}–${it.end.timeText()}" } ?: pick,
                style = if (slot == null) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum")
                },
                color = when {
                    slot == null -> colors.primary
                    state.conflictBlocksSave -> colors.error
                    else -> colors.onSurface
                },
                maxLines = 1,
            )
        }
        StepButton(
            KlasIcons.Filled.Add,
            stringResource(R.string.class_step_later),
            enabled = state.stepped(later = true) != null,
            onClick = { onStep(true) },
        )
    }
}

/** A round tonal button: [icon] over the step's minutes, named [name] for whoever cannot see it. */
@Composable
private fun StepButton(icon: ImageVector, name: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick, Modifier.size(StepButtonSize), enabled = enabled) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = name, Modifier.size(StepIconSize))
            Text(
                STEP_MINUTES.toString(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/** What stands in the chosen time's way — red only while it stops the save. */
@Composable
private fun ConflictLine(conflict: TimeConflict, state: ClassFormState) {
    Text(
        if (state.form.repeat.repeats) {
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

/** «1 h · until 15:30». */
@Composable
private fun lengthSummary(minutes: Int?, ends: ClassForm.Ending?): String =
    bulletJoin(minutes?.let { durationLabel(it) }.orEmpty(), ends?.let { endsAtText(it) }.orEmpty())

internal const val DAY_TAG = "booking_day"
internal const val TIME_TAG = "booking_time"

private val StepButtonSize = 56.dp
private val StepIconSize = 20.dp
