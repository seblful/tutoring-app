package com.seblful.klas.ui.schedule.booking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.schedule.Recurrence
import com.seblful.klas.domain.schedule.Repeat
import com.seblful.klas.format.MoneyField
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDate
import com.seblful.klas.ui.common.CountPickerModal
import com.seblful.klas.ui.common.DurationPickerModal
import com.seblful.klas.ui.common.MoneyTextField
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.SegmentedChoice
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.classModeLabel
import com.seblful.klas.ui.common.durationLabel
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.designsystem.component.KlasDivider

/*
 * The booking form's last card — see [DetailsSection].
 */

/** The rows of [DetailsSection]; the one open, if any, is the card's to keep. */
private enum class Detail { LENGTH, REPEAT, PRICE, FORMAT }

/**
 * How long, whether it repeats, for how much and where: one card of rows folded to what each is set to,
 * opened in place one at a time — never a dialog or a sheet over the form. Opened, each is a value between a
 * step less and a step more ([Steps]), or a segmented choice. Repeat is there only while booking: repeating
 * is a property of the act, not of a lesson already in the database, and offering it on an edit would read
 * as turning one lesson into twelve.
 */
@Composable
internal fun DetailsSection(state: ClassFormState, viewModel: ClassEditViewModel, isNew: Boolean) {
    var open by rememberSaveable { mutableStateOf<Detail?>(null) }
    val toggle = { detail: Detail, opened: Boolean -> open = detail.takeIf { opened } }
    SectionCard {
        Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.inline)) {
            LengthRow(state, viewModel, open == Detail.LENGTH) { toggle(Detail.LENGTH, it) }
            if (isNew) {
                KlasDivider()
                RepeatRow(state.form, viewModel, open == Detail.REPEAT) { toggle(Detail.REPEAT, it) }
            }
            KlasDivider()
            PriceRow(state, viewModel, open == Detail.PRICE) { toggle(Detail.PRICE, it) }
            KlasDivider()
            FormatRow(state.form, viewModel, open == Detail.FORMAT) { toggle(Detail.FORMAT, it) }
        }
    }
}

/**
 * How long, not when it ends — a length is what the lesson is stored as; the end is its consequence. Stepped
 * [Steps.LENGTH_MINUTES] at a time, or typed in hours and minutes from a tap on the length.
 */
@Composable
private fun LengthRow(
    state: ClassFormState,
    viewModel: ClassEditViewModel,
    opened: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    var typing by rememberSaveable { mutableStateOf(false) }
    val minutes = state.form.durationMinutes
    val lastsFor = { to: Int -> viewModel.change(BookingIntent.LastsFor(to)) }
    Fold(
        label = { RowLabel(stringResource(R.string.class_duration_label)) },
        summary = lengthSummary(minutes, state.ending),
        opened = opened,
        onToggle = onToggle,
    ) {
        StepRow(
            lessName = stringResource(R.string.class_length_shorter),
            moreName = stringResource(R.string.class_length_longer),
            onLess = minutes?.let { Steps.length(it, longer = false) }?.let { to -> { lastsFor(to) } },
            onMore = Steps.length(minutes ?: 0, longer = true)?.let { to -> { lastsFor(to) } },
            caption = Steps.LENGTH_MINUTES.toString(),
        ) {
            StepValue(
                minutes?.let { durationLabel(it) }.orEmpty(),
                state.ending?.let { endsAtText(it) },
                onPick = { typing = true },
                pickLabel = stringResource(R.string.class_length_pick),
                tag = LENGTH_TAG,
            )
        }
    }
    if (typing) {
        DurationPickerModal(
            initialMinutes = viewModel.durationPickerDefault(),
            onConfirm = { to -> lastsFor(to); typing = false },
            onDismiss = { typing = false },
        )
    }
}

/**
 * How many lessons, one being a lesson that does not repeat, and how often when there are more. Stepped one
 * at a time, or typed from a tap on the count.
 */
@Composable
private fun RepeatRow(form: ClassForm, viewModel: ClassEditViewModel, opened: Boolean, onToggle: (Boolean) -> Unit) {
    var typing by rememberSaveable { mutableStateOf(false) }
    val lessons = form.lessons
    val writes = { count: Int -> viewModel.change(BookingIntent.Edit { it.withLessons(count) }) }
    Fold(
        label = { RowLabel(stringResource(R.string.class_repeat_label)) },
        summary = repeatSummary(form),
        opened = opened,
        onToggle = onToggle,
    ) {
        StepRow(
            lessName = stringResource(R.string.class_lessons_fewer),
            moreName = stringResource(R.string.class_lessons_more),
            onLess = Steps.lessons(lessons, more = false)?.let { to -> { writes(to) } },
            onMore = Steps.lessons(lessons, more = true)?.let { to -> { writes(to) } },
            caption = "1",
        ) {
            StepValue(
                pluralStringResource(R.plurals.class_series_lessons, lessons, lessons),
                if (form.repeat.repeats) repeatSummary(form, counted = false) else repeatLabel(Repeat.NONE),
                onPick = { typing = true },
                pickLabel = stringResource(R.string.class_lessons_pick),
                tag = LESSONS_TAG,
            )
        }
        if (form.repeat.repeats) {
            SegmentedChoice(
                options = listOf(Repeat.WEEKLY, Repeat.FORTNIGHTLY),
                selected = form.repeat,
                onSelect = { repeat -> viewModel.change(BookingIntent.Edit { it.copy(repeat = repeat) }) },
                label = { repeatLabel(it) },
            )
            Text(
                stringResource(R.string.class_repeat_independent_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (typing) {
        CountPickerModal(
            title = stringResource(R.string.class_repeat_count_title),
            label = stringResource(R.string.class_repeat_count_field, Recurrence.MAX_COUNT),
            initial = lessons,
            max = Recurrence.MAX_COUNT,
            onConfirm = { to -> writes(to); typing = false },
            onDismiss = { typing = false },
        )
    }
}

/**
 * The rate — typed, or stepped by an amount that suits it ([Steps.rateStep]); folded, in red while it is
 * what the booking still needs ([ClassFormState.rateNeeded]).
 */
@Composable
private fun PriceRow(
    state: ClassFormState,
    viewModel: ClassEditViewModel,
    opened: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val form = state.form
    val rate = form.price.amount?.takeIf { form.price.isValid }
    val setRate = { to: Money ->
        viewModel.change(BookingIntent.Edit { it.copy(price = MoneyField.of(to, it.price.kind)) })
    }
    Fold(
        label = { RowLabel(stringResource(R.string.class_section_price)) },
        summary = if (state.rateNeeded) {
            stringResource(R.string.class_terms_price_needed)
        } else {
            rate?.let { stringResource(R.string.price_per_hour, it.text()) }.orEmpty()
        },
        opened = opened,
        onToggle = onToggle,
        wanting = state.rateNeeded,
    ) {
        StepRow(
            lessName = stringResource(R.string.class_rate_lower),
            moreName = stringResource(R.string.class_rate_higher),
            onLess = rate?.let { Steps.rate(it, higher = false) }?.let { to -> { setRate(to) } },
            onMore = Steps.rate(rate ?: Money.ZERO, higher = true)
                ?.let { to -> { setRate(to) } },
        ) {
            // The label differs because the question does: a group's rate is the group's, a student's is
            // theirs for this subject.
            MoneyTextField(
                field = form.price,
                onValueChange = { text ->
                    viewModel.change(BookingIntent.Edit { it.copy(price = it.price.typed(text)) })
                },
                label = stringResource(
                    if (form.target == ClassTarget.GROUP) R.string.class_group_price_label else R.string.price_label,
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** In person or remote, as segments. */
@Composable
private fun FormatRow(form: ClassForm, viewModel: ClassEditViewModel, opened: Boolean, onToggle: (Boolean) -> Unit) {
    Fold(
        label = { RowLabel(stringResource(R.string.class_section_format)) },
        summary = classModeLabel(form.mode),
        opened = opened,
        onToggle = onToggle,
    ) {
        SegmentedChoice(
            options = ClassMode.entries,
            selected = form.mode,
            onSelect = { mode -> viewModel.change(BookingIntent.Edit { it.copy(mode = mode) }) },
            label = { classModeLabel(it) },
        )
    }
}

internal const val LENGTH_TAG = "booking_length"
internal const val LESSONS_TAG = "booking_lessons"

@Composable
private fun RowLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
}

/** «1 h · until 15:30». */
@Composable
private fun lengthSummary(minutes: Int?, ends: ClassForm.Ending?): String =
    bulletJoin(minutes?.let { durationLabel(it) }.orEmpty(), ends?.let { endsAtText(it) }.orEmpty())

/** «Once», or «Every week · 8 lessons · until 24 Sep» — without the count where the value above says it. */
@Composable
private fun repeatSummary(form: ClassForm, counted: Boolean = true): String {
    if (!form.repeat.repeats) return repeatLabel(form.repeat)
    return bulletJoin(
        repeatLabel(form.repeat),
        if (counted) pluralStringResource(R.plurals.class_series_lessons, form.repeatCount, form.repeatCount) else "",
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
