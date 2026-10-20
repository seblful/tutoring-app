package com.seblful.klas.ui.schedule.booking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.schedule.Repeat
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDate
import com.seblful.klas.ui.common.MenuOptions
import com.seblful.klas.ui.common.TickedMenuItem
import com.seblful.klas.ui.common.classModeLabel
import com.seblful.klas.ui.common.durationLabel
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.schedule.timeConflictText
import com.seblful.klas.ui.settings.settingsRowColors

/*
 * The quick-book sheet's rows — see [QuickBookSheet]. Each is one fact of the booking, read at a glance and
 * changed through a menu under it or a picker it opens.
 */

/** When: the day, the start (with what is in its way), and the length (with where that ends). */
@Composable
internal fun WhenRows(
    state: ClassFormState,
    durations: List<Int>,
    change: (BookingIntent) -> Unit,
    onPick: (QuickPicker) -> Unit,
) {
    val form = state.form
    QuickRow(
        label = stringResource(R.string.class_date_label),
        value = form.date?.formatDate(),
        onClick = { onPick(QuickPicker.DATE) },
    )
    QuickRow(
        label = stringResource(R.string.class_time_from_label),
        value = form.timeFrom?.timeText(),
        note = state.conflict?.let { conflict ->
            if (form.repeat.repeats) {
                stringResource(R.string.class_series_availability, state.availableOccurrences)
            } else {
                timeConflictText(conflict)
            }
        },
        noteIsError = state.conflictBlocksSave,
        onClick = { onPick(QuickPicker.START) },
    )
    MenuRow(
        label = stringResource(R.string.class_duration_label),
        value = form.durationMinutes?.let { minutes ->
            bulletJoin(durationLabel(minutes), state.ending?.let { endsAtText(it) }.orEmpty())
        },
    ) { dismiss ->
        durations.forEach { minutes ->
            TickedMenuItem(
                text = durationLabel(minutes),
                selected = minutes == form.durationMinutes,
                onClick = { change(BookingIntent.LastsFor(minutes)); dismiss() },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.class_duration_custom)) },
            onClick = { dismiss(); onPick(QuickPicker.LENGTH) },
        )
    }
}

/** What and on what terms: the subject, the rate, the place, and whether it repeats. */
@Composable
internal fun TermRows(
    state: ClassFormState,
    change: (BookingIntent) -> Unit,
    onPick: (QuickPicker) -> Unit,
) {
    val form = state.form
    MenuRow(
        label = stringResource(R.string.class_subject_label),
        value = state.subjectName.ifBlank { null },
        enabled = state.subjectPickerEnabled,
        note = state.subjectNotice?.let { notice ->
            stringResource(
                when (notice) {
                    SubjectNotice.FIXED_BY_GROUP -> R.string.class_subject_from_group
                    SubjectNotice.NONE_FOR_GROUP -> R.string.class_no_subjects_for_group
                    SubjectNotice.NONE_FOR_STUDENT -> R.string.class_no_subjects_for_student
                },
            )
        },
        noteIsError = state.subjectNotice != null && state.subjectNotice != SubjectNotice.FIXED_BY_GROUP,
    ) { dismiss ->
        MenuOptions(state.subjects, nameOf = { it.name }) { subject ->
            TickedMenuItem(
                text = subject.name,
                selected = subject.id == form.subjectId,
                onClick = { change(BookingIntent.ChooseSubject(subject.id)); dismiss() },
            )
        }
    }
    QuickRow(
        label = stringResource(priceLabel(form.target)),
        value = form.price.amount?.text(),
        onClick = { onPick(QuickPicker.PRICE) },
    )
    MenuRow(
        label = stringResource(R.string.class_location_label),
        value = classModeLabel(form.mode),
    ) { dismiss ->
        ClassMode.entries.forEach { mode ->
            TickedMenuItem(
                text = classModeLabel(mode),
                selected = mode == form.mode,
                onClick = { change(BookingIntent.Edit { it.copy(mode = mode) }); dismiss() },
            )
        }
    }
    MenuRow(
        label = stringResource(R.string.class_repeat_label),
        value = repeatText(form.repeat, form.repeatCount),
    ) { dismiss ->
        Repeat.entries.forEach { repeat ->
            TickedMenuItem(
                text = repeatLabel(repeat),
                selected = repeat == form.repeat,
                onClick = { change(BookingIntent.Edit { it.copy(repeat = repeat) }); dismiss() },
            )
        }
    }
}

/**
 * One fact of the booking: what it is, what it is set to, and — under it — what is wrong with it or what
 * decides it. A value not chosen yet says so rather than leaving a blank to find.
 */
@Composable
private fun QuickRow(
    label: String,
    value: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    note: String? = null,
    noteIsError: Boolean = false,
) {
    val changeLabel = stringResource(R.string.class_quick_change)
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = note?.let { text ->
            {
                Text(
                    text,
                    color = if (noteIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        trailingContent = {
            Text(
                value ?: stringResource(R.string.class_quick_not_set),
                style = MaterialTheme.typography.bodyLarge,
                color = if (value == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        colors = settingsRowColors(enabled),
        modifier = Modifier.insetRowPress {
            Modifier.clickable(
                it,
                indication = null,
                enabled = enabled,
                onClickLabel = changeLabel,
                onClick = onClick,
            )
        },
    )
}

/** A [QuickRow] whose choices open as a menu under it, given the `dismiss` that closes the menu. */
@Composable
private fun MenuRow(
    label: String,
    value: String?,
    enabled: Boolean = true,
    note: String? = null,
    noteIsError: Boolean = false,
    options: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        QuickRow(label, value, onClick = { open = true }, enabled = enabled, note = note, noteIsError = noteIsError)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options { open = false }
        }
    }
}

@Composable
private fun repeatText(repeat: Repeat, count: Int): String =
    if (repeat.repeats) {
        bulletJoin(repeatLabel(repeat), pluralStringResource(R.plurals.class_quick_lessons, count, count))
    } else {
        repeatLabel(repeat)
    }

@Composable
private fun repeatLabel(repeat: Repeat): String = stringResource(
    when (repeat) {
        Repeat.NONE -> R.string.class_repeat_none
        Repeat.WEEKLY -> R.string.class_repeat_weekly
        Repeat.FORTNIGHTLY -> R.string.class_repeat_fortnightly
    },
)

internal fun priceLabel(target: ClassTarget): Int =
    if (target == ClassTarget.GROUP) R.string.class_group_price_label else R.string.price_label

@Composable
internal fun refusalText(event: ClassEditViewModel.Event): String = stringResource(
    when (event) {
        ClassEditViewModel.Event.SAVE_REFUSED_TIME_TAKEN -> R.string.class_save_refused_time_taken
        ClassEditViewModel.Event.SAVE_REFUSED_NO_MEMBERS -> R.string.class_group_no_members_left
        ClassEditViewModel.Event.SERIES_REFUSED_ALL_TAKEN -> R.string.class_series_refused_all_taken
        ClassEditViewModel.Event.SAVE_FAILED -> R.string.save_failed
    },
)
