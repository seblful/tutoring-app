package com.seblful.klas.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seblful.klas.R
import com.seblful.klas.ui.common.AvatarStack
import com.seblful.klas.ui.common.ContentCard
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.FabClearance
import com.seblful.klas.ui.common.IconAvatar
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.StatusBadge
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.text

/**
 * The groups list — a **body**, sharing a scaffold with the students list (see
 * `ui/people/PeopleScreen.kt`). A group is students seen as a roster, so the two are one tab.
 *
 * As the students list, three states: a group list that has not been read is a skeleton, not an
 * invitation to create the first group. See [Loadable].
 *
 * @param searching whether an empty list means "nothing matched" rather than "there are none".
 */
@Composable
fun GroupsList(
    state: Loadable<List<GroupRow>>,
    searching: Boolean,
    onGroupClick: (Long) -> Unit,
    onAddGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = when (state) {
        Loadable.Loading -> {
            ListSkeleton(modifier)
            return
        }
        is Loadable.Ready -> state.value
    }
    if (rows.isEmpty()) {
        if (searching) {
            EmptyState(
                title = stringResource(R.string.search_no_results),
                detail = stringResource(R.string.search_no_results_detail),
                icon = Icons.Outlined.SearchOff,
                modifier = modifier,
            )
        } else {
            EmptyState(
                title = stringResource(R.string.groups_empty),
                detail = stringResource(R.string.groups_empty_detail),
                icon = Icons.Outlined.Groups,
                actionLabel = stringResource(R.string.groups_add),
                onAction = onAddGroup,
                modifier = modifier,
            )
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.gutter,
            top = Spacing.gutter,
            end = Spacing.gutter,
            // Past the add button, so it never sits on the last row.
            bottom = FabClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        items(rows, key = { it.group.id }) { row ->
            ContentCard(
                onClick = { onGroupClick(row.group.id) },
                modifier = animateInPlace(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconAvatar(Icons.Outlined.Groups)
                    Spacer(Modifier.width(Spacing.leading))
                    Column(Modifier.weight(1f)) {
                        Text(row.group.name, style = MaterialTheme.typography.titleMedium)
                        // What the group is taught, when it says. A group from before the subject
                        // existed shows nothing here rather than a placeholder — it genuinely has no
                        // answer, and inventing one would be a lie.
                        row.subjectName?.let { subject ->
                            // The students list's second line is `bodyMedium`, so this is too: two
                            // sides of one tab, one row anatomy.
                            Text(
                                subject,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Faces, not a comma-separated sentence. The roster used to be printed in
                        // full, so a group of eight became a paragraph inside a list row and the list
                        // stopped being scannable at exactly the point there were enough groups to
                        // need scanning. Monograms answer "which group is this" on one line whatever
                        // the size, and the overflow count carries the rest.
                        Spacer(Modifier.height(Spacing.tight))
                        if (row.memberNames.isEmpty()) {
                            Text(
                                stringResource(R.string.group_no_members),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            AvatarStack(names = row.memberNames, ids = row.memberIds)
                        }
                    }
                    // **The rate moved out to the trailing edge**, and with it the row's fourth line.
                    // A group was four stacked lines against the students list's two — twice the height
                    // for the same nesting level, in a list the two sides of one tab swap between, so
                    // switching to «Группы» read as a change of density rather than of subject. Money
                    // also belongs at the trailing edge on the app's own evidence: it is where a
                    // student's balance and a settings amount already sit.
                    Spacer(Modifier.width(Spacing.inline))
                    // Capped, because a `Row` measures an unweighted child at its intrinsic width
                    // *before* the weighted name column gets what is left: «1 000 000,00 BYN/час» at a
                    // large font scale would take the name's room, which is the failure the student
                    // form's fixed-width price box was just fixed for. Ellipsised rather than clipped —
                    // a cut-off amount should read as cut off.
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.widthIn(max = TrailingRateWidth),
                    ) {
                        Text(
                            stringResource(R.string.price_per_hour, row.group.price.text()),
                            // As the group's own card says its rate.
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // The badge the students list uses, for the same state — it was an italic grey
                        // word on both.
                        if (row.group.archived) {
                            Spacer(Modifier.height(Spacing.tight))
                            StatusBadge(
                                text = stringResource(R.string.student_archived_badge),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The widest the trailing rate may grow before it starts eating the group's name. Room for
 * «1 250,50 BYN/час» at the default scale; anything longer ellipsises rather than taking the width
 * from the one thing on the row that identifies it. A measurement, not a gap — see `Spacing`.
 */
private val TrailingRateWidth = 132.dp
