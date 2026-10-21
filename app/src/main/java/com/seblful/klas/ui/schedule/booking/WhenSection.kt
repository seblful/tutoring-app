package com.seblful.klas.ui.schedule.booking

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.schedule.DayTimeline
import com.seblful.klas.domain.schedule.TimeConflict
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.ui.common.DurationPickerModal
import com.seblful.klas.ui.common.PresetChips
import com.seblful.klas.ui.common.durationLabel
import com.seblful.klas.ui.schedule.WeekNavigator
import com.seblful.klas.ui.schedule.WeekStrip
import com.seblful.klas.ui.schedule.timeConflictText
import kotlinx.datetime.LocalDate

/*
 * The booking form's «When» section — see [WhenSection].
 */

/**
 * When: the day from a week strip, the start from that day's timeline, and how long — folded to a line.
 * Whether it repeats is a section of its own, [RepeatSection].
 */
@Composable
internal fun WhenSection(state: ClassFormState, viewModel: ClassEditViewModel) {
    val form = state.form
    val durations by viewModel.durationOptions.collectAsStateWithLifecycle()
    var customLength by remember { mutableStateOf(false) }
    WeekChoice(viewModel, selected = form.date)
    state.timeline?.let { day -> DayOnTimeline(day, state, viewModel) } ?: Text(
        stringResource(R.string.class_timeline_pick_day),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
    if (customLength) {
        DurationPickerModal(
            initialMinutes = viewModel.durationPickerDefault(),
            onConfirm = { minutes -> viewModel.change(BookingIntent.LastsFor(minutes)); customLength = false },
            onDismiss = { customLength = false },
        )
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

/** The strip of days the lesson can be on, a week at a time — the schedule's own strip and navigator. */
@Composable
private fun WeekChoice(viewModel: ClassEditViewModel, selected: LocalDate?) {
    val week by viewModel.week.collectAsStateWithLifecycle()
    WeekNavigator(
        days = week.days,
        onPrevious = { viewModel.showWeek(-1) },
        onNext = { viewModel.showWeek(1) },
        onToday = viewModel::showThisWeek,
    )
    WeekStrip(
        days = week.days,
        loads = week.loads,
        selected = selected,
        today = week.today,
        onSelect = { date -> viewModel.change(BookingIntent.Edit { it.copy(date = date) }) },
    )
}

/** The chosen day as one bar — see [BookingTimeline]. */
@Composable
private fun DayOnTimeline(day: DayTimeline, state: ClassFormState, viewModel: ClassEditViewModel) {
    BookingTimeline(
        timeline = day,
        chosen = state.slot,
        length = state.form.durationMinutes ?: DEFAULT_LESSON_MINUTES,
        // The day drawn, not the run: a repeating booking's later clash is said in the text below.
        clashes = state.slot?.let(day::clashesWith) == true,
        onStart = viewModel::startAt,
        suggestion = state.suggestion,
        mine = state.form.assignee,
    )
}

/** «1 h · until 15:30». */
@Composable
private fun lengthSummary(minutes: Int?, ends: ClassForm.Ending?): String =
    bulletJoin(minutes?.let { durationLabel(it) }.orEmpty(), ends?.let { endsAtText(it) }.orEmpty())
