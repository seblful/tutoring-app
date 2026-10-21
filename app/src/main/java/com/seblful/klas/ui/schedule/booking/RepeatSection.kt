package com.seblful.klas.ui.schedule.booking

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.schedule.Recurrence
import com.seblful.klas.domain.schedule.Repeat
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDate
import com.seblful.klas.ui.common.CountPickerModal
import com.seblful.klas.ui.common.PresetChips
import com.seblful.klas.ui.common.SegmentedChoice

/**
 * Repeat: how often, and how many times — a card of its own, folded to «Once» until the tutor opens it.
 *
 * **Only while booking** (the screen leaves it out otherwise): repeating is a property of the act, not of a
 * lesson already in the database, and offering it there would read as an invitation to turn one lesson
 * into twelve by editing it.
 */
@Composable
internal fun RepeatSection(form: ClassForm, viewModel: ClassEditViewModel) {
    var customCount by remember { mutableStateOf(false) }
    FoldedSection(stringResource(R.string.class_repeat_label), repeatSummary(form)) {
        SegmentedChoice(
            options = Repeat.entries,
            selected = form.repeat,
            onSelect = { repeat -> viewModel.change(BookingIntent.Edit { it.copy(repeat = repeat) }) },
            label = { option -> repeatLabel(option) },
        )
        if (form.repeat.repeats) {
            Text(stringResource(R.string.class_repeat_count_label), style = MaterialTheme.typography.labelLarge)
            PresetChips(
                presets = Recurrence.COMMON_COUNTS,
                selected = form.repeatCount,
                label = { option -> option.toString() },
                customLabel = stringResource(R.string.class_repeat_count_custom),
                onSelect = { count -> viewModel.change(BookingIntent.Edit { it.copy(repeatCount = count) }) },
                onCustom = { customCount = true },
            )
            Text(
                stringResource(R.string.class_repeat_independent_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (customCount) {
        CountPickerModal(
            title = stringResource(R.string.class_repeat_count_title),
            label = stringResource(R.string.class_repeat_count_field, Recurrence.MAX_COUNT),
            initial = viewModel.repeatCountPickerDefault(),
            max = Recurrence.MAX_COUNT,
            onConfirm = { count ->
                viewModel.change(BookingIntent.Edit { it.copy(repeatCount = count) })
                customCount = false
            },
            onDismiss = { customCount = false },
        )
    }
}

/** «Once», or «Every week · 8 lessons · until 24 Sep». */
@Composable
private fun repeatSummary(form: ClassForm): String {
    if (!form.repeat.repeats) return repeatLabel(form.repeat)
    return bulletJoin(
        repeatLabel(form.repeat),
        pluralStringResource(R.plurals.class_series_lessons, form.repeatCount, form.repeatCount),
        form.repeatsUntil?.let { stringResource(R.string.class_repeat_until, it.formatDate()) }.orEmpty(),
    )
}

@Composable
internal fun repeatLabel(repeat: Repeat): String = stringResource(
    when (repeat) {
        Repeat.NONE -> R.string.class_repeat_none
        Repeat.WEEKLY -> R.string.class_repeat_weekly
        Repeat.FORTNIGHTLY -> R.string.class_repeat_fortnightly
    },
)
