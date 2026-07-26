package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.domain.model.RateChange
import com.seblful.klas.format.formatDate

/**
 * A rate's recorded history, newest first — shared by a student's subject rows and a group's rate,
 * because "what did this cost before?" is one question however it is owned.
 *
 * An empty log is stated rather than left blank: a price that has never moved is a fact about it, and
 * a silently missing section reads as something failing to load.
 */
@Composable
fun RateHistoryList(changes: List<RateChange>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (changes.isEmpty()) {
            Text(
                stringResource(R.string.rate_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            changes.forEach { RateChangeRow(it) }
        }
    }
}

/**
 * One line of the log: what the rate became, and when.
 *
 * A row with no previous rate says «Назначена 20 BYN» instead of pretending to be a change — it is
 * where the history starts. A row with no date says so out loud: those are the rates that already
 * existed when the app began keeping this log (see `RateChange.at`), and «дата неизвестна» is the
 * honest thing to print where inventing the upgrade date would not be.
 */
@Composable
private fun RateChangeRow(change: RateChange) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Branching on `from` itself rather than on `isInitial`, which is the same test one step
        // removed: the compiler follows the null check and the previous rate needs no assertion.
        val from = change.from
        Text(
            text = if (from == null) {
                stringResource(R.string.rate_history_initial, change.to.text())
            } else {
                stringResource(R.string.rate_history_change, from.text(), change.to.text())
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = change.at?.formatDate() ?: stringResource(R.string.rate_history_unknown_date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
