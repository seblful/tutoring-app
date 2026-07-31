package com.seblful.klas.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.format.MoneyField
import com.seblful.klas.ui.theme.klasColors

/**
 * The app's domain vocabulary in words and colours — the one place a `LessonStatus`, a `ClassMode`, a balance
 * or a `MoneyField.Error` becomes something a screen can print.
 *
 * These are `@Composable` because they read `stringResource` and the theme, not because they draw anything.
 * The *decisions* behind them are elsewhere and tested: `MoneyField.error` classifies which money rule broke,
 * `KlasColors` owns the green that means taught. What is here is the mapping onto words.
 */

/**
 * What a money field says when what was typed will not do — the wording for [MoneyField.Error].
 *
 * The classification is `MoneyField`'s and is unit-tested; only the sentence is here. Returns null when
 * there is nothing wrong, which is exactly what an `OutlinedTextField`'s `supportingText` wants: use it
 * with [fieldSupport] so a field that is merely *required* still has something to say.
 */
@Composable
fun moneyErrorText(field: MoneyField): String? = field.error?.let { error ->
    stringResource(
        when (error) {
            MoneyField.Error.UNREADABLE -> R.string.money_error_unreadable
            MoneyField.Error.ZERO_PAYMENT -> R.string.money_error_zero_payment
        }
    )
}
/**
 * A field's supporting line: the reason it is **wrong** if it is, otherwise its ordinary hint.
 *
 * One slot, one line, and **the error wins** — that ordering is the whole point. A field that shows its
 * hint while red is telling the tutor how it works instead of what to fix.
 *
 * Returns null when there is nothing to say, so a field with neither reserves no line — which is why it
 * hands back the whole `supportingText` slot rather than a string.
 */
fun supportingTextOf(error: String? = null, hint: String? = null): (@Composable () -> Unit)? =
    (error ?: hint)?.let { text -> { Text(text) } }
/**
 * Human-readable balance line: debt, credit, or settled.
 *
 * Takes the case and not the amount: which of the three it is was decided by [Balance.of] and tested there,
 * so this holds only the sentence. Both arms print [Balance.Credit.amount] and [Balance.Owed.amount] as
 * given — they are already positive, so nothing here flips a sign.
 */
@Composable
fun balanceDescription(balance: Balance): String = when (balance) {
    Balance.Settled -> stringResource(R.string.balance_settled)
    is Balance.Credit -> stringResource(R.string.balance_credit, balance.amount.text())
    is Balance.Owed -> stringResource(R.string.balance_owes, balance.amount.text())
}
/** Colour for a balance: red for debt, theme primary for credit, muted when settled. */
@Composable
fun balanceColor(balance: Balance): Color = when (balance) {
    Balance.Settled -> MaterialTheme.colorScheme.onSurfaceVariant
    is Balance.Credit -> MaterialTheme.colorScheme.primary
    is Balance.Owed -> MaterialTheme.colorScheme.error
}
@Composable
fun classStatusLabel(status: LessonStatus): String = stringResource(
    when (status) {
        LessonStatus.SCHEDULED -> R.string.class_status_scheduled
        LessonStatus.COMPLETED -> R.string.class_status_completed
        LessonStatus.CANCELLED -> R.string.class_status_cancelled
    }
)
/**
 * The tint a status is said in: the brand colour for a lesson still to come, **green for one taught**,
 * **the theme's error red for a cancelled one** — the same red a debt is written in ([balanceColor]),
 * red meaning "look here" in both places.
 *
 * Taught was `tertiary`, this palette's teal, which read as a second blue beside the indigo of a lesson
 * still to come — the palette's `md_success` carries the whole argument. Green is what "done" is said in
 * outside the app, and it now says it everywhere inside: this badge and the panel the schedule's swipe
 * reveals are the two tones of the one colour.
 *
 * Cancelled was `onSurfaceVariant` before, which left grey saying two different things: "cancelled" and
 * the «Разные» badge a group lesson got when its members' statuses disagreed. That badge is gone — a
 * lesson now answers with one of these three ([ClassSession.status]) — so grey is nobody's status. The
 * lesson *card* still recedes when it is off — dimmed, its time struck through — so a cancellation stays
 * quiet in a list of lessons while remaining findable in it.
 */
@Composable
fun classStatusColor(status: LessonStatus): Color = when (status) {
    LessonStatus.SCHEDULED -> MaterialTheme.colorScheme.primary
    LessonStatus.COMPLETED -> klasColors.success
    LessonStatus.CANCELLED -> MaterialTheme.colorScheme.error
}
/** Localized label for a class mode: «Очно» / «Удалённо». */
@Composable
fun classModeLabel(mode: ClassMode): String = stringResource(
    when (mode) {
        ClassMode.IN_PERSON -> R.string.class_mode_in_person
        ClassMode.REMOTE -> R.string.class_mode_remote
    }
)
