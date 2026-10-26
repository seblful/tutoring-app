package com.seblful.klas.ui.schedule.booking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.data.settings.SettingsDefaults
import com.seblful.klas.domain.schedule.DayTimeline
import com.seblful.klas.domain.schedule.TimeConflict
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDay
import com.seblful.klas.ui.common.DatePickerModal
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TimePickerModal
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.schedule.timeConflictText
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasSemantics

/*
 * The booking form's «When» section — see [WhenSection].
 */

/**
 * When: the day, stepped a day at a time or picked in the date dialog, and the start, stepped
 * [STEP_MINUTES] at a time or typed in the time dialog. The day usually comes from the schedule the booking
 * was opened on, so neither needs a calendar on the form. How long, and whether it repeats, are rows of
 * [DetailsSection].
 */
@Composable
internal fun WhenSection(state: ClassFormState, viewModel: ClassEditViewModel) {
    var pickingDay by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    DayRow(
        state,
        onPrevious = { viewModel.moveDay(-1) },
        onNext = { viewModel.moveDay(1) },
        onPick = { pickingDay = true },
    )
    Stepper(state, onStep = viewModel::step, onPick = { pickingTime = true })
    // Before a start is set, what the first press does is said: it takes the first free time, not 15 minutes.
    if (state.slot == null) {
        state.stepped(later = true)?.let { first ->
            Text(
                stringResource(R.string.class_step_first_free, first.timeText()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    state.conflict?.let { conflict -> ConflictLine(conflict, state) }

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
}

/**
 * «‹ Friday, 23 October ›» over the day's lessons and its free time written out — «2 lessons · free 09:00–11:00,
 * from 19:30» — so a slot is found by reading, not by stepping to it. The date itself opens the dialog.
 */
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
                val minutes = state.form.durationMinutes
                    ?: state.slot?.durationMinutes
                    ?: SettingsDefaults.LESSON_MINUTES
                Text(
                    dayLine(day, minutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        NamedIconButton(KlasIcons.Filled.ChevronRight, stringResource(R.string.class_day_next), onNext)
    }
}

/** The day's lessons, then where a lesson of [minutes] still fits — at most [SHOWN_WINDOWS] stretches of it. */
@Composable
private fun dayLine(day: DayTimeline, minutes: Int): String {
    val others = day.booked.size
    if (others == 0) return stringResource(R.string.class_day_free)
    val resources = LocalResources.current
    // `map`, being inline, may read the clock format; `joinToString`'s transform may not.
    val windows = day.freeFor(minutes).take(SHOWN_WINDOWS).map { (from, to) ->
        if (to == day.end) {
            resources.getString(R.string.class_day_free_from, from.timeText())
        } else {
            "${from.timeText()}–${to.timeText()}"
        }
    }.joinToString(", ")
    return bulletJoin(
        pluralStringResource(R.plurals.lessons_count, others, others),
        if (windows.isEmpty()) {
            stringResource(R.string.class_day_full)
        } else {
            stringResource(R.string.class_day_free_list, windows)
        },
    )
}

private const val SHOWN_WINDOWS = 3

/**
 * The start and end in large numbers between a step earlier and a step later — see
 * [ClassFormState.stepped]. A tap on the numbers opens the time dialog. Red while the time is taken.
 */
@Composable
private fun Stepper(state: ClassFormState, onStep: (later: Boolean) -> Unit, onPick: () -> Unit) {
    val slot = state.slot
    val colors = MaterialTheme.colorScheme
    val pick = stringResource(R.string.class_time_pick)
    val earlier = state.stepped(later = false)
    val later = state.stepped(later = true)
    StepRow(
        // Named by where a press goes, which is not always 15 minutes on: past a lesson in the way, or to the
        // first free time while there is no start yet ([ClassFormState.stepped]).
        lessName = earlier?.let { stringResource(R.string.class_step_earlier_to, it.timeText()) }
            ?: stringResource(R.string.class_step_earlier),
        moreName = later?.let { stringResource(R.string.class_step_later_to, it.timeText()) }
            ?: stringResource(R.string.class_step_later),
        onLess = state.stepped(later = false)?.let { { onStep(false) } },
        onMore = state.stepped(later = true)?.let { { onStep(true) } },
        size = StartButtonSize,
        caption = STEP_MINUTES.toString(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = pick, role = Role.Button, onClick = onPick)
                .padding(vertical = Spacing.tight)
                .testTag(TIME_TAG),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val style = if (slot == null) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum")
            }
            Text(
                slot?.let { "${it.start.timeText()}–${it.end.timeText()}" } ?: pick,
                style = style,
                color = when {
                    slot == null -> colors.primary
                    state.conflictBlocksSave -> klasSemantics.validation
                    else -> colors.onSurface
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                // «15:30–16:30» outgrew the room between the buttons on a 360 dp phone; a 12-hour clock is longer.
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = with(LocalDensity.current) { MinTimeSize.toSp() },
                    maxFontSize = style.fontSize,
                ),
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
            klasSemantics.validation
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

internal const val DAY_TAG = "booking_day"
internal const val TIME_TAG = "booking_time"

private val MinTimeSize = 16.dp
