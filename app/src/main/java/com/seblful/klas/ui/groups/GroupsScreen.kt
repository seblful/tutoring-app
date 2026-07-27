package com.seblful.klas.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.ui.common.AvatarStack
import com.seblful.klas.ui.common.ContentCard
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.constrainedContent
import com.seblful.klas.ui.common.text

/**
 * The groups list — a **body**, sharing a scaffold with the students list (see
 * `ui/people/PeopleScreen.kt`). A group is students seen as a roster, so the two are one tab.
 *
 * @param searching whether an empty list means "nothing matched" rather than "there are none".
 */
@Composable
fun GroupsList(
    rows: List<GroupRow>,
    searching: Boolean,
    onGroupClick: (Long) -> Unit,
    onAddGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) {
        if (searching) {
            EmptyState(
                title = stringResource(R.string.search_no_results),
                detail = stringResource(R.string.search_no_results_detail),
                icon = Icons.Filled.SearchOff,
                modifier = modifier,
            )
        } else {
            EmptyState(
                title = stringResource(R.string.groups_empty),
                detail = stringResource(R.string.groups_empty_detail),
                icon = Icons.Filled.Groups,
                actionLabel = stringResource(R.string.groups_add),
                onAction = onAddGroup,
                modifier = modifier,
            )
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize().constrainedContent(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(rows, key = { it.group.id }) { row ->
            ContentCard(
                onClick = { onGroupClick(row.group.id) },
                modifier = Modifier.animateItem(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.group.name, style = MaterialTheme.typography.titleMedium)
                        // What the group is taught, when it says. A group from before the subject
                        // existed shows nothing here rather than a placeholder — it genuinely has no
                        // answer, and inventing one would be a lie.
                        row.subjectName?.let { subject ->
                            Text(
                                subject,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Faces, not a comma-separated sentence. The roster used to be printed in
                        // full, so a group of eight became a paragraph inside a list row and the list
                        // stopped being scannable at exactly the point there were enough groups to
                        // need scanning. Monograms answer "which group is this" on one line whatever
                        // the size, and the overflow count carries the rest.
                        Spacer(Modifier.height(6.dp))
                        if (row.memberNames.isEmpty()) {
                            Text(
                                stringResource(R.string.group_no_members),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            AvatarStack(names = row.memberNames, ids = row.memberIds)
                        }
                        Spacer(Modifier.height(6.dp))
                        // The rate belongs on the row now that the group carries it: it is what every
                        // one of its lessons bills each member.
                        Text(
                            stringResource(R.string.price_per_hour, row.group.price.text()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (row.group.archived) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.student_archived_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
            }
        }
    }
}
