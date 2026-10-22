package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDate
import com.seblful.klas.format.formatHours
import com.seblful.klas.queries.students.StudentDetails
import com.seblful.klas.ui.common.HoldToName
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.durationLabel
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.people.lessonLineText
import com.seblful.klas.ui.theme.KlasIcons

/**
 * When the tutor next sees them, and the way to book another: the thing a card opened before a lesson is
 * opened for. Tapped, it opens that lesson.
 */
@Composable
internal fun NextLessonCard(
    next: NextLesson?,
    onOpen: (Long) -> Unit,
    onBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    val label = if (next?.running == true) R.string.class_status_in_progress else R.string.student_next_lesson
    val content: @Composable ColumnScope.() -> Unit = {
        Row(
            CardInset,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
                if (next == null) {
                    Text(
                        stringResource(R.string.student_no_lesson_booked),
                        style = MaterialTheme.typography.titleMedium,
                    )
                } else {
                    NextLessonLines(next)
                }
            }
            val book = stringResource(R.string.class_book)
            HoldToName(book) {
                FilledIconButton(onClick = onBook, modifier = Modifier.padding(start = Spacing.inline)) {
                    Icon(KlasIcons.Filled.Add, contentDescription = book)
                }
            }
        }
    }
    if (next != null) {
        val open = { onOpen(next.row.klass.id) }
        Card(onClick = open, modifier = modifier.fillMaxWidth(), colors = colors, content = content)
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, content = content)
    }
}

/** When it is, in one line that shrinks a step before it breaks, then what and for how long. */
@Composable
private fun NextLessonLines(next: NextLesson) {
    Text(
        lessonLineText(next.line, timeAlways = true),
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(
            minFontSize = with(LocalDensity.current) { MinHeadlineSize.toSp() },
            maxFontSize = MaterialTheme.typography.titleLarge.fontSize,
        ),
    )
    val row = next.row
    Text(
        bulletJoin(row.subjectName, durationLabel(row.klass.durationMinutes), row.groupName.orEmpty()),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Where they stand — owed in red, as everywhere a debt is said — with when they last paid, what they have
 * brought in, and the way to record the next payment.
 */
@Composable
internal fun BalanceCard(state: StudentDetails, onAddPayment: () -> Unit, modifier: Modifier = Modifier) {
    val balance = state.balance
    val totals = state.totals
    SectionCard(modifier) {
        // The button beside the amount, not beside all four lines: there the lines below get the card's whole
        // width, and a large font does not fold «Last payment …» into a column of single words.
        Column(CardInset, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            Text(
                stringResource(if (balance is Balance.Credit) R.string.stat_credit else R.string.stat_debt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    balance.amount.text(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (balance is Balance.Owed) MaterialTheme.colorScheme.error else Color.Unspecified,
                    maxLines = 1,
                    // «12 450,50 BYN» at a large font scale must not wrap its currency onto a line of its own.
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = with(LocalDensity.current) { MinHeadlineSize.toSp() },
                        maxFontSize = MaterialTheme.typography.headlineSmall.fontSize,
                    ),
                    modifier = Modifier.weight(1f),
                )
                AddPaymentButton(onAddPayment)
            }
            // Not the balance again in words — the label and the number say it — but when the money last came
            // in, which a tutor deciding whether to chase it wants next.
            Text(
                state.lastPaymentAt?.let { stringResource(R.string.student_last_payment, it.formatDate()) }
                    ?: stringResource(R.string.payments_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (totals.minutes > 0) {
                Text(
                    bulletJoin(
                        stringResource(R.string.student_earned, totals.earned.text()),
                        stringResource(R.string.hours_value, formatHours(totals.minutes)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** «+ Payment», read out in full as «Add a payment». */
@Composable
private fun AddPaymentButton(onClick: () -> Unit) {
    val addPayment = stringResource(R.string.add_payment)
    FilledTonalButton(
        onClick = onClick,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        modifier = Modifier.padding(start = Spacing.inline).semantics { contentDescription = addPayment },
    ) {
        Icon(KlasIcons.Filled.Add, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
        Text(stringResource(R.string.payment_add_short), Modifier.padding(start = Spacing.inline))
    }
}

private val MinHeadlineSize = 18.dp

/** A headline card's padding: its trailing button sits closer to the edge than the text does. */
private val CardInset = Modifier
    .fillMaxWidth()
    .padding(start = Spacing.gutter, end = Spacing.itemGap, top = Spacing.gutter, bottom = Spacing.gutter)
