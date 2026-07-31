package com.seblful.klas.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.domain.Attendance
import com.seblful.klas.format.formatHours
import com.seblful.klas.ui.common.Avatar
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.LoadingDetailScreen
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.Stat
import com.seblful.klas.ui.common.StatRow
import com.seblful.klas.ui.common.StickyTabRow
import com.seblful.klas.ui.common.TitleSize
import com.seblful.klas.ui.common.dateTimeText
import com.seblful.klas.ui.common.lessonsStat
import com.seblful.klas.ui.common.classStatusColor
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.text

/**
 * The two things a group's card is about. Payments are **not** among them: a balance belongs to a
 * student and spans all of their lessons, so it is read on the student's card — which is exactly
 * where a member row leads.
 */
private enum class GroupTab(val labelRes: Int) {
    STUDENTS(R.string.group_students_title),
    LESSONS(R.string.group_lessons_title),
}

/**
 * A group's card: what it is taught and for how much, how much of it has happened, who is in it and
 * which lessons they attended.
 *
 * Opening a group used to open its **edit form**, which answered "how do I change this?" and nothing
 * about "how is this going?". Editing now sits behind the pencil, where it does for a student.
 *
 * Booking is **not** offered here. A lesson is booked from the schedule, which is the one place that
 * knows what else the hour holds — there is one tutor, so a booking is a claim on their day rather
 * than an entry in a group's ledger. The schedule's add button asks who the lesson is for.
 *
 * @param onEdit opens the group form — the only place a group's roster, subject or rate changes.
 * @param onMemberClick opens a member's own card, which is where their money lives.
 */
@Composable
fun GroupDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMemberClick: (Long) -> Unit,
    onLessonClick: (Long) -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    // As the student's card: `GroupDetailState()` is an empty group rather than an unread one, so the
    // wait is its own screen instead of a card claiming no members and no lessons. See [Loadable].
    when (val screenState = viewModel.state.collectAsState().value) {
        Loadable.Loading -> LoadingDetailScreen(onBack)
        is Loadable.Ready ->
            GroupCard(screenState.value, viewModel, onBack, onEdit, onMemberClick, onLessonClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupCard(
    state: GroupDetailState,
    viewModel: GroupDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMemberClick: (Long) -> Unit,
    onLessonClick: (Long) -> Unit,
) {
    val group = state.group

    var tab by rememberSaveable { mutableStateOf(GroupTab.STUDENTS) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val messages = rememberMessages()
    val deleteBlockedMsg = stringResource(R.string.group_delete_blocked)
    ShowMessagesFor(viewModel.events, messages) { deleteBlockedMsg }

    // The group's name is the subject of the screen, as a student's is on theirs — the same large,
    // collapsing title, so the two cards read as one pattern.
    KlasScreen(
        title = group?.name,
        onBack = onBack,
        titleSize = TitleSize.Large,
        messages = messages,
        actions = {
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, stringResource(R.string.action_edit))
            }
            // Retiring and removing the group, behind the overflow — where a student's already were.
            // They used to be a pair of outlined buttons under a «danger zone» heading at the foot of
            // the group's *form*, so the same lifecycle verb lived on the card for one entity and on
            // the form for the other, and reaching the group's meant scrolling past its whole roster.
            if (group != null) {
                GroupOverflowMenu(
                    archived = group.archived,
                    onArchive = { viewModel.setArchived(!group.archived) },
                    onDelete = { confirmingDelete = true },
                )
            }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = Spacing.sectionGap),
        ) {
            // Identity, then the numbers, then the tabs — the same three in the same order as the
            // student's card. The two used to disagree: the student led with the stat row and put the
            // "what is this" block second.
            item(key = GroupDetailKeys.HEADER) {
                GroupHeader(
                    state = state,
                    modifier = Modifier.padding(
                        horizontal = Spacing.gutter,
                        vertical = Spacing.inline,
                    ),
                )
            }

            item(key = GroupDetailKeys.STATS) {
                StatRow(
                    stats = groupStats(state),
                    modifier = Modifier.padding(
                        horizontal = Spacing.gutter,
                        vertical = Spacing.inline,
                    ),
                )
            }

            stickyHeader(key = GroupDetailKeys.TABS) {
                StickyTabRow(selectedTabIndex = tab.ordinal) {
                    GroupTab.entries.forEach { entry ->
                        Tab(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            text = { Text(stringResource(entry.labelRes)) },
                        )
                    }
                }
            }

            when (tab) {
                GroupTab.STUDENTS -> studentsTab(state, onMemberClick, onEditMembers = onEdit)
                GroupTab.LESSONS -> lessonsTab(state, onLessonClick)
            }
        }
    }

    if (confirmingDelete) {
        ConfirmDialog(
            text = stringResource(R.string.group_delete_confirm),
            onConfirm = { viewModel.delete(onDeleted = onBack) },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/**
 * What the group is: its subject, whether it is retired, its note and its rate.
 *
 * A group with no subject says so rather than showing an empty line: groups made before the subject
 * was fixed on the group genuinely have none, and «Предмет не выбран» is the prompt to set one.
 *
 * **The rate's history is not here any more.** It was an «История цены ⌄» expander under the rate — a
 * 28 dp toggle held up to the touch minimum, on the one screen that cannot change the number it was
 * annotating. History is context for the number being *typed*, which is the argument the student's
 * reprice dialog already made in its own KDoc; the group's rate is typed on the group's form, so that
 * is where the log went.
 */
@Composable
private fun GroupHeader(state: GroupDetailState, modifier: Modifier = Modifier) {
    val group = state.group ?: return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.subjectName ?: stringResource(R.string.group_no_subject),
                style = MaterialTheme.typography.titleMedium,
                color = if (state.subjectName == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            if (group.archived) {
                Text(
                    stringResource(R.string.student_archived_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(start = Spacing.itemGap),
                )
            }
        }
        if (group.note.isNotBlank()) {
            Text(group.note, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            stringResource(R.string.price_per_hour, group.price.text()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Retiring or removing the group — the same overflow the student's card carries, in the same place.
 *
 * Delete is tinted with the error colour and sits below archive, because archiving is the one the app
 * pushes you towards: a group with lessons cannot be deleted at all (its classes must keep naming it),
 * and the refusal says so.
 */
@Composable
private fun GroupOverflowMenu(archived: Boolean, onArchive: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (archived) R.string.action_unarchive else R.string.action_archive
                        )
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Inventory2, contentDescription = null) },
                onClick = { expanded = false; onArchive() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

/**
 * What the group has cost the tutor's week, and what it paid: lessons and hours count **sessions**
 * (a lesson for three is one lesson, not three), while the money sums every member's charge — see
 * `Taught.byGroup`.
 */
@Composable
private fun groupStats(state: GroupDetailState): List<Stat> {
    val totals = state.totals
    return listOf(
        lessonsStat(totals),
        Stat(label = stringResource(R.string.stat_hours), value = formatHours(totals.minutes)),
        Stat(label = stringResource(R.string.stat_earned), value = totals.earned.text()),
    )
}

/**
 * The roster, each member with how many of the group's lessons they were actually in — which a list
 * of names alone does not say, since someone left out week after week is still a member.
 */
private fun LazyListScope.studentsTab(
    state: GroupDetailState,
    onMemberClick: (Long) -> Unit,
    onEditMembers: () -> Unit,
) {
    if (state.members.isEmpty()) {
        item(key = GroupDetailKeys.STUDENTS_EMPTY) {
            // With the way out. This tab named its emptiness and offered nothing, while the student
            // card's subjects tab offered «Добавить предмет» — the asymmetry was which file `AddRow`
            // happened to live in, not a decision. A roster is changed on the group's form, so that is
            // where this leads.
            EmptyState(
                title = stringResource(R.string.group_no_members),
                icon = Icons.Outlined.Groups,
                actionLabel = stringResource(R.string.group_edit_members),
                onAction = onEditMembers,
                compact = true,
            )
        }
        return
    }
    items(state.members, key = { GroupDetailKeys.member(it.student.id) }) { row ->
        ListItem(
            leadingContent = { Avatar(row.student.name, row.student.id) },
            headlineContent = { Text(row.student.name) },
            supportingContent = {
                Text(
                    pluralStringResource(R.plurals.lessons_count, row.attended, row.attended),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.group_member_open),
                )
            },
            modifier = Modifier.clickable { onMemberClick(row.student.id) }.animateItem(),
        )
    }
}

/**
 * The group's lessons, newest first — one row per **lesson**, not per member's class, which is what
 * the schedule shows too. Tapping one opens it, as tapping it in the schedule would.
 */
private fun LazyListScope.lessonsTab(state: GroupDetailState, onLessonClick: (Long) -> Unit) {
    if (state.lessons.isEmpty()) {
        item(key = GroupDetailKeys.LESSONS_EMPTY) {
            // No action, for the same reason the student card's lessons tab has none: a lesson is
            // booked from the schedule.
            EmptyState(
                title = stringResource(R.string.group_lessons_empty),
                icon = Icons.Outlined.EventBusy,
                compact = true,
            )
        }
        return
    }
    items(state.lessons, key = { GroupDetailKeys.lesson(it.session.first.id) }) { row ->
        val status = row.session.status
        ListItem(
            headlineContent = { Text(row.session.start.dateTimeText()) },
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
                    Text(
                        // What happened to the lesson. How many of the group were in it is the count
                        // beside this, which is the part that speaks for the members.
                        text = classStatusLabel(status),
                        color = classStatusColor(status),
                    )
                    // How many of the group were in it — the part that speaks for the members, and the
                    // only place their attendance is visible. Said as "who came" only for a lesson that
                    // happened: until then everybody is expected, and a count of that is just the roster.
                    Text(
                        when (val attendance = row.attendance) {
                            is Attendance.All ->
                                pluralStringResource(
                                    R.plurals.group_lesson_attended,
                                    attendance.count,
                                    attendance.count,
                                )
                            is Attendance.Some ->
                                stringResource(
                                    R.string.group_lesson_present,
                                    attendance.present,
                                    attendance.of,
                                )
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingContent = { if (!row.charge.isZero) Text(row.charge.text()) },
            modifier = Modifier.clickable { onLessonClick(row.session.first.id) }.animateItem(),
        )
    }
}

/**
 * Stable keys for the group card's one `LazyColumn`. Same rule as the student card's
 * (see [com.seblful.klas.ui.students.StudentDetailKeys]): a student id and a class id both start
 * at 1, so keys are namespaced by kind, and the card's own furniture gets fixed names of its own.
 */
private object GroupDetailKeys {
    const val HEADER = "header"
    const val STATS = "stats"
    const val TABS = "tabs"
    const val STUDENTS_EMPTY = "students:empty"
    const val LESSONS_EMPTY = "lessons:empty"

    fun member(id: Long): String = "member:$id"
    fun lesson(id: Long): String = "lesson:$id"
}
