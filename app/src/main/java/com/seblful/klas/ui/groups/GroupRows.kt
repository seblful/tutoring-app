package com.seblful.klas.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.seblful.klas.R
import com.seblful.klas.domain.model.Attendance
import com.seblful.klas.ui.common.ArchivedBadge
import com.seblful.klas.ui.common.Avatar
import com.seblful.klas.ui.common.BadgeLine
import com.seblful.klas.ui.common.OpensScreenMark
import com.seblful.klas.ui.common.StatusBadge
import com.seblful.klas.ui.common.TrailingAmount
import com.seblful.klas.ui.common.classStatusColor
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.common.dateTimeText
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.text

/*
 * The rows a group's lessons and members are listed in — the same on its card, inside its section cards, and
 * on the page «See all» opens.
 */

/** One of the group's lessons: when, what happened to it, who was in it, and what it came to. Opens it. */
@Composable
internal fun GroupLessonItem(
    row: GroupLessonRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ListItemColors = ListItemDefaults.colors(),
) {
    val status = row.session.status
    ListItem(
        headlineContent = { Text(row.session.start.dateTimeText()) },
        supportingContent = {
            BadgeLine(badge = { StatusBadge(classStatusLabel(status), classStatusColor(status)) }) {
                // Said as "who came" only for a lesson that happened: until then everybody is expected, and a
                // count of that is just the roster. See [Attendance].
                Text(
                    when (val attendance = row.attendance) {
                        is Attendance.All ->
                            pluralStringResource(R.plurals.group_lesson_attended, attendance.count, attendance.count)
                        is Attendance.Some ->
                            stringResource(R.string.group_lesson_present, attendance.present, attendance.of)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = { row.charge?.let { TrailingAmount(it.text()) } },
        colors = colors,
        modifier = modifier.insetRowPress { Modifier.clickable(it, indication = null, onClick = onClick) },
    )
}

/** A member, with how much of the group they attended. Opens their own card, which is where their money is. */
@Composable
internal fun GroupMemberItem(
    row: GroupMemberRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ListItemColors = ListItemDefaults.colors(),
) {
    val openMemberLabel = stringResource(R.string.group_member_open)
    ListItem(
        leadingContent = { Avatar(row.student.name, row.student.id) },
        headlineContent = { Text(row.student.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            // An archived member still explains the group's past lessons, so they stay listed — and say so.
            val attended = pluralStringResource(R.plurals.lessons_count, row.attended, row.attended)
            if (row.student.archived) {
                BadgeLine(badge = { ArchivedBadge() }, badgeFirst = false) {
                    Text(attended, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(attended, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = { OpensScreenMark() },
        colors = colors,
        modifier = modifier.insetRowPress {
            Modifier.clickable(it, indication = null, onClickLabel = openMemberLabel, onClick = onClick)
        },
    )
}
