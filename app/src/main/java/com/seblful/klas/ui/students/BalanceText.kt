package com.seblful.klas.ui.students

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.ui.common.text

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
