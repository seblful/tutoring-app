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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.domain.model.RateChange
import com.seblful.klas.format.formatDate

/**
 * A rate's recorded history, newest first — shared by a student's subject rows and a group's rate,
 * because "what did this cost before?" is one question however it is owned.
 *
 * @param max how many rows to draw before collapsing the remainder into a count.
 */
@Composable
fun RateHistoryList(changes: List<RateChange>, modifier: Modifier = Modifier, max: Int = MaxRateRows) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        if (changes.isEmpty()) {
            Text(
                stringResource(R.string.rate_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            changes.take(max).forEach { RateChangeRow(it) }
            val older = changes.size - max
            if (older > 0) {
                Text(
                    pluralStringResource(R.plurals.rate_history_older, older, older),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * «История цены» over its [RateHistoryList] — the block both places a rate is typed carry under the field:
 * the student's reprice dialog and the group's form. History is context for the number being typed, so it
 * sits where the typing is and nowhere else; the two used to title it apart, a step further from the list
 * on one than on the other.
 */
@Composable
fun RateHistory(changes: List<RateChange>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        Text(
            stringResource(R.string.rate_history_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RateHistoryList(changes)
    }
}

private const val MaxRateRows = 5

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
            // Takes what the date leaves, so a long «1 250,50 BYN → 1 500 BYN» wraps rather than pushing
            // the date off the row.
            modifier = Modifier.weight(1f),
        )
        Text(
            text = change.at?.formatDate() ?: stringResource(R.string.rate_history_unknown_date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.inline),
        )
    }
}
