package com.seblful.klas.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.seblful.klas.R
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.ui.common.ArchivedBadge
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.FabListPadding
import com.seblful.klas.ui.common.IconAvatar
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.NoSearchResults
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.plusInset
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.designsystem.component.SlicePosition
import com.seblful.klas.ui.people.PeopleCard
import com.seblful.klas.ui.people.lessonLineText
import com.seblful.klas.ui.theme.KlasIcons

/**
 * The groups list — a **body**, sharing a scaffold with the students list (see
 * `ui/people/PeopleScreen.kt`). A group is students seen as a roster, so the two are one tab.
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
    bottomInset: PaddingValues = PaddingValues(),
    openId: Long? = null,
    chips: @Composable () -> Unit = {},
) {
    // Above the list rather than in it, so a filter that leaves nothing can still be taken off.
    Column(modifier) {
        chips()
        GroupRows(state, searching, onGroupClick, onAddGroup, Modifier.weight(1f), bottomInset, openId)
    }
}

@Composable
private fun GroupRows(
    state: Loadable<List<GroupRow>>,
    searching: Boolean,
    onGroupClick: (Long) -> Unit,
    onAddGroup: () -> Unit,
    modifier: Modifier,
    bottomInset: PaddingValues,
    openId: Long?,
) {
    val rows = when (state) {
        Loadable.Loading -> {
            ListSkeleton(modifier.padding(bottomInset))
            return
        }
        is Loadable.Ready -> state.value
    }
    if (rows.isEmpty()) {
        if (searching) {
            NoSearchResults(modifier.padding(bottomInset))
        } else {
            EmptyState(
                title = stringResource(R.string.groups_empty),
                detail = stringResource(R.string.groups_empty_detail),
                icon = KlasIcons.Outlined.Groups,
                actionLabel = stringResource(R.string.groups_add),
                onAction = onAddGroup,
                modifier = modifier.padding(bottomInset),
            )
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        // Past the add button, so it never sits on the last row.
        contentPadding = FabListPadding.plusInset(bottomInset),
    ) {
        itemsIndexed(rows, key = { _, row -> row.group.id }) { index, row ->
            GroupCard(
                row,
                onClick = { onGroupClick(row.group.id) },
                modifier = animateInPlace(),
                selected = row.group.id == openId,
                position = SlicePosition.of(index, rows.size),
            )
        }
    }
}

/** One group on the list: what it is taught, how many are in it, and when it next meets. */
@Composable
internal fun GroupCard(
    row: GroupRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    position: SlicePosition = SlicePosition.ONLY,
) {
    PeopleCard(
        title = row.group.name,
        // The rate is the group page's: it is not what a group is picked by. A row of monograms was here too —
        // «Д М П Т» names nobody, so it said only how many, which the count says shorter.
        supporting = bulletJoin(
            lessonLineText(row.lessons),
            row.subjectName.orEmpty(),
            if (row.memberNames.isEmpty()) {
                stringResource(R.string.group_no_members)
            } else {
                pluralStringResource(R.plurals.students_count, row.memberNames.size, row.memberNames.size)
            },
        ),
        onClick = onClick,
        leading = { IconAvatar(KlasIcons.Outlined.Groups, selected = selected) },
        modifier = modifier,
        selected = selected,
        position = position,
    ) {
        if (row.group.archived) ArchivedBadge()
    }
}
