package com.seblful.klas.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.Attendance
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.queries.groups.GroupLessonRow
import com.seblful.klas.queries.groups.GroupMemberRow
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.Strings
import com.seblful.klas.text.pluralStringResource
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.ArchivedBadge
import com.seblful.klas.ui.common.Avatar
import com.seblful.klas.ui.common.BadgeLine
import com.seblful.klas.ui.common.OpensScreenMark
import com.seblful.klas.ui.common.StatusBadge
import com.seblful.klas.ui.common.TrailingAmount
import com.seblful.klas.ui.common.dateTimeText
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.lessonBadgeColor
import com.seblful.klas.ui.common.lessonBadgeLabel
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.students.balanceColor
import com.seblful.klas.ui.students.balanceDescription
import kotlin.time.Instant

/*
 * The rows a group's lessons and members are listed in — the same on its card, inside its section cards, and
 * on the page «See all» opens.
 */

/** One of the group's lessons: when, what happened to it, who was in it, and what it came to. Opens it. */
@Composable
fun GroupLessonItem(
    row: GroupLessonRow,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ListItemColors = ListItemDefaults.colors(),
) {
    val status = row.session.status
    val phase = LessonPhase.of(row.session, now)
    ListItem(
        headlineContent = { Text(row.session.start.dateTimeText()) },
        supportingContent = {
            BadgeLine(badge = { StatusBadge(lessonBadgeLabel(status, phase), lessonBadgeColor(status, phase)) }) {
                // Said as "who came" only for a lesson that happened: until then everybody is expected, and a
                // count of that is just the roster. See [Attendance].
                Text(
                    when (val attendance = row.attendance) {
                        is Attendance.All ->
                            pluralStringResource(Plurals.group_lesson_attended, attendance.count, attendance.count)
                        is Attendance.Some ->
                            stringResource(Strings.group_lesson_present, attendance.present, attendance.of)
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
    val openMemberLabel = stringResource(Strings.group_member_open)
    ListItem(
        leadingContent = { Avatar(row.student.name, row.student.id) },
        headlineContent = { Text(row.student.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val attended = if (row.held == 0) {
                stringResource(Strings.group_member_no_lessons)
            } else {
                stringResource(Strings.group_member_present, row.present, row.held)
            }
            Column {
                // An archived member still explains the group's past lessons, so they stay listed — and say so.
                if (row.student.archived) {
                    BadgeLine(badge = { ArchivedBadge() }, badgeFirst = false) {
                        Text(attended, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(attended, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Only a debt: the group's card answers "who owes", and credit is the student's own business.
                (row.balance as? Balance.Owed)?.let {
                    Text(balanceDescription(it), color = balanceColor(it))
                }
            }
        },
        trailingContent = { OpensScreenMark() },
        colors = colors,
        modifier = modifier.insetRowPress {
            Modifier.clickable(it, indication = null, onClickLabel = openMemberLabel, onClick = onClick)
        },
    )
}
