package com.seblful.klas.ui.students

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.text.Strings
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.theme.klasSemantics

/** Human-readable balance line: debt, credit, or settled. */
@Composable
fun balanceDescription(balance: Balance): String = when (balance) {
    Balance.Settled -> stringResource(Strings.balance_settled)
    is Balance.Credit -> stringResource(Strings.balance_credit, balance.amount.text())
    is Balance.Owed -> stringResource(Strings.balance_owes, balance.amount.text())
}
/** Colour for a balance: red for debt, ink for credit (an ordinary amount), muted when settled. */
@Composable
fun balanceColor(balance: Balance): Color = when (balance) {
    Balance.Settled -> MaterialTheme.colorScheme.onSurfaceVariant
    is Balance.Credit -> MaterialTheme.colorScheme.onSurface
    is Balance.Owed -> klasSemantics.debt
}
