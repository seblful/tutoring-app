package com.seblful.klas.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.format.MoneyField
import com.seblful.klas.format.labelRes
import com.seblful.klas.ui.students.balanceColor
import com.seblful.klas.ui.theme.klasColors

/**
 * The app's domain vocabulary in words and colours — the one place a `LessonStatus`, a `ClassMode`, a balance
 * or a `MoneyField.Error` becomes something a screen can print.
 *
 * These are `@Composable` because they read `stringResource` and the theme, not because they draw anything.
 * The *decisions* behind them are elsewhere and tested: `MoneyField.error` classifies which money rule broke,
 * `KlasColors` owns the green that means taught. What is here is the mapping onto words.
 */

/** hat a money field says when what was typed will not do — the wording for [MoneyField.Error]. */
@Composable
fun moneyErrorText(field: MoneyField): String? = field.error?.let { error ->
    stringResource(
        when (error) {
            MoneyField.Error.UNREADABLE -> R.string.money_error_unreadable
            MoneyField.Error.ZERO_PAYMENT -> R.string.money_error_zero_payment
        },
    )
}
/**  field's supporting line: the reason it is **wrong** if it is, otherwise its ordinary hint. */
fun supportingTextOf(error: String? = null, hint: String? = null): (@Composable () -> Unit)? =
    (error ?: hint)?.let { text -> { Text(text) } }
@Composable
fun classStatusLabel(status: LessonStatus): String = stringResource(
    when (status) {
        LessonStatus.SCHEDULED -> R.string.class_status_scheduled
        LessonStatus.COMPLETED -> R.string.class_status_completed
        LessonStatus.CANCELLED -> R.string.class_status_cancelled
    },
)
/**
 * The tint a status is said in: the brand colour for a lesson still to come, **green for one taught**,
 * **the theme's error red for a cancelled one** — the same red a debt is written in ([balanceColor]),
 * red meaning "look here" in both places.
 */
@Composable
fun classStatusColor(status: LessonStatus): Color = when (status) {
    LessonStatus.SCHEDULED -> MaterialTheme.colorScheme.primary
    LessonStatus.COMPLETED -> klasColors.success
    LessonStatus.CANCELLED -> MaterialTheme.colorScheme.error
}

/** hat a lesson's badge **says**: its status, or «Идёт» while the lesson is being taught. */
@Composable
fun lessonBadgeLabel(status: LessonStatus, running: Boolean): String =
    if (running) stringResource(R.string.class_status_in_progress) else classStatusLabel(status)

/** The tint [lessonBadgeLabel] is said in — amber for a lesson in progress, see `KlasColors.live`. */
@Composable
fun lessonBadgeColor(status: LessonStatus, running: Boolean): Color =
    if (running) klasColors.live else classStatusColor(status)
/** Localized label for a class mode: «Очно» / «Удалённо». */
@Composable
fun classModeLabel(mode: ClassMode): String = stringResource(mode.labelRes())

/** 1 ч», «1,5 ч», «45 мин» — whole and half hours read better than a minute count. */
@Composable
fun durationLabel(minutes: Int): String = when (minutes) {
    60 -> stringResource(R.string.hours_one)
    90 -> stringResource(R.string.hours_one_and_half)
    120 -> stringResource(R.string.hours_two)
    else -> stringResource(R.string.minutes_short, minutes)
}
