package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonDefaults
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
import com.seblful.klas.R
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDate
import com.seblful.klas.format.formatHours
import com.seblful.klas.queries.students.StudentDetails
import com.seblful.klas.ui.common.MinHeadlineSize
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.designsystem.component.ButtonKind
import com.seblful.klas.ui.designsystem.component.HeaderedCard
import com.seblful.klas.ui.designsystem.component.KlasButton
import com.seblful.klas.ui.designsystem.component.SectionAction
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasSemantics

/**
 * Where they stand — owed in red, as everywhere a debt is said — with when they last paid, what they have
 * brought in, and the way to record the next payment. A section like the lessons and payments below it, its
 * header carries «Statement» (R1): every lesson and payment with the balance each left, to send.
 */
@Composable
internal fun BalanceCard(
    state: StudentDetails,
    onAddPayment: () -> Unit,
    onStatement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val balance = state.balance
    val totals = state.totals
    HeaderedCard(
        title = stringResource(R.string.student_balance_title),
        modifier = modifier,
        action = SectionAction(stringResource(R.string.student_statement), opens = true, onClick = onStatement),
    ) {
        // The button beside the amount, not beside all four lines: there the lines below get the card's whole
        // width, and a large font does not fold «Last payment …» into a column of single words.
        Column(BalanceInset, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            // Settled, there is no amount to label: «Owes $0» read as a debt, and the People list shows none.
            if (balance !is Balance.Settled) {
                Text(
                    stringResource(if (balance is Balance.Credit) R.string.stat_credit else R.string.stat_debt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (balance is Balance.Settled) balanceDescription(balance) else balance.amount.text(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (balance is Balance.Owed) klasSemantics.debt else Color.Unspecified,
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

/** A headline card's inset under a header, which has already spaced the top. */
private val BalanceInset = Modifier
    .fillMaxWidth()
    .padding(start = Spacing.gutter, end = Spacing.itemGap, bottom = Spacing.itemGap)

/** «+ Payment», read out in full as «Add a payment». */
@Composable
private fun AddPaymentButton(onClick: () -> Unit) {
    val addPayment = stringResource(R.string.add_payment)
    KlasButton(
        onClick = onClick,
        modifier = Modifier.padding(start = Spacing.inline).semantics { contentDescription = addPayment },
        kind = ButtonKind.Tonal,
        withIcon = true,
    ) {
        Icon(KlasIcons.Filled.Add, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
        Text(stringResource(R.string.payment_add_short), Modifier.padding(start = Spacing.inline))
    }
}
