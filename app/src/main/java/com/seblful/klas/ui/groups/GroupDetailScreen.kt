package com.seblful.klas.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.model.Attendance
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.ui.common.ArchiveMenuItem
import com.seblful.klas.ui.common.ArchivedBadge
import com.seblful.klas.ui.common.Avatar
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.DetailCard
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.LoadingDetailScreen
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.common.OpensScreenMark
import com.seblful.klas.ui.common.OverflowMenu
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.Stat
import com.seblful.klas.ui.common.StatusBadge
import com.seblful.klas.ui.common.TitleSize
import com.seblful.klas.ui.common.TrailingAmount
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.classStatusColor
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.common.dateTimeText
import com.seblful.klas.ui.common.detailCard
import com.seblful.klas.ui.common.earnedStat
import com.seblful.klas.ui.common.hoursStat
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.lessonsStat
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.homework.CardHomeworkDialogs
import com.seblful.klas.ui.homework.homeworkItems
import com.seblful.klas.ui.homework.homeworkUndo
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

private enum class GroupTab(val labelRes: Int, val icon: ImageVector) {
    LESSONS(R.string.group_lessons_title, KlasIcons.Outlined.CalendarMonth),

    /** The group's own homework, current and done — where it is set for the whole group. */
    HOMEWORK(R.string.student_homework_title, KlasIcons.Outlined.Assignment),
    STUDENTS(R.string.group_students_title, KlasIcons.Outlined.Groups),
}

private data class GroupNavigation(
    val back: () -> Unit,
    val edit: () -> Unit,
    val member: (Long) -> Unit,
    val lesson: (Long) -> Unit,
    val book: () -> Unit,
)

/**
 * A group's card: what it is taught and for how much, how much of it has happened, who is in it and
 * which lessons they attended.
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
    viewModel: GroupDetailViewModel = assistedMetroViewModel(),
    onBookLesson: () -> Unit = {},
    initialHomework: Boolean = false,
) {
    // As the student's card: `GroupDetailState()` is an empty group rather than an unread one, so the
    // wait is its own screen instead of a card claiming no members and no lessons. See [Loadable].
    when (val screenState = viewModel.state.collectAsStateWithLifecycle().value) {
        Loadable.Loading -> LoadingDetailScreen(onBack)
        is Loadable.Ready ->
            GroupCard(
                screenState.value, viewModel,
                GroupNavigation(onBack, onEdit, onMemberClick, onLessonClick, onBookLesson), initialHomework,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupCard(
    state: GroupDetailState,
    viewModel: GroupDetailViewModel,
    navigation: GroupNavigation,
    initialHomework: Boolean,
) {
    val group = state.group

    var tab by rememberSaveable { mutableStateOf(GroupTab.LESSONS) }
    var confirmingDelete by remember { mutableStateOf(false) }
    // Taken when the dialog opens — see `GroupDetailViewModel.homeworkChoice`.
    var addingHomework by remember { mutableStateOf<AssigneeChoice?>(null) }
    var openedHomework by remember { mutableStateOf<HomeworkRow?>(null) }
    HomeworkEntry(initialHomework, group?.id) {
        tab = GroupTab.HOMEWORK
        addingHomework = viewModel.homeworkChoice()
    }

    val messages = rememberMessages()
    val deleteBlockedMsg = stringResource(R.string.group_delete_blocked)
    val writeFailedMsg = stringResource(R.string.action_failed)
    ShowMessagesFor(viewModel.events, messages) { event ->
        when (event) {
            GroupDetailViewModel.Event.DELETE_BLOCKED_HAS_LESSONS -> deleteBlockedMsg
            GroupDetailViewModel.Event.WRITE_FAILED -> writeFailedMsg
        }
    }
    val homeworkUndo = homeworkUndo(
        messages = messages,
        setDone = viewModel::setHomeworkDone,
        putBack = viewModel::putHomeworkBack,
        delete = viewModel::deleteHomework,
        restore = viewModel::restoreHomework,
    )

    // The group's name is the subject of the screen, as a student's is on theirs — the same large,
    // collapsing title, so the two cards read as one pattern.
    KlasScreen(
        title = group?.name,
        onBack = navigation.back,
        titleSize = TitleSize.Large,
        messages = messages,
        actions = {
            if (group != null && !group.archived) {
                NamedIconButton(
                    KlasIcons.Filled.EditCalendar,
                    stringResource(R.string.class_book),
                    onClick = navigation.book,
                )
            }
            NamedIconButton(KlasIcons.Filled.Edit, stringResource(R.string.action_edit), onClick = navigation.edit)
            // Retiring and removing the group, behind the overflow — where a student's already were.
            // They used to be a pair of outlined buttons under a «danger zone» heading at the foot of
            // the group's *form*, so the same lifecycle verb lived on the card for one entity and on
            // the form for the other, and reaching the group's meant scrolling past its whole roster.
            if (group != null) {
                GroupOverflowMenu(
                    archived = group.archived,
                    onArchive = { viewModel.setArchived(!group.archived) },
                    onDelete = { confirmingDelete = true }.takeIf { state.canDelete },
                )
            }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = Spacing.sectionGap),
        ) {
            // Identity, then the numbers, then the tabs — the same three in the same order as the
            // student's card (see [detailCard]). The two used to disagree: the student led with the stat
            // row and put the "what is this" block second.
            detailCard(
                identity = {
                    item(key = GroupDetailKeys.HEADER) {
                        GroupHeader(
                            state = state,
                            modifier = Modifier.padding(
                                horizontal = Spacing.gutter,
                                vertical = Spacing.inline,
                            ),
                        )
                    }
                },
                stats = { groupStats(state) },
                views = GroupTab.entries,
                selected = tab,
                onSelect = { tab = it },
                label = { stringResource(it.labelRes) },
                icon = { it.icon },
                count = { entry ->
                    when (entry) {
                        GroupTab.LESSONS -> state.lessons.size
                        GroupTab.HOMEWORK -> state.homework.current.size
                        GroupTab.STUDENTS -> state.members.size
                    }
                },
            ) { shown ->
                when (shown) {
                    GroupTab.LESSONS -> lessonsTab(state, navigation.lesson)
                    GroupTab.HOMEWORK -> homeworkItems(
                        lists = state.homework,
                        messages = messages,
                        onAdd = { addingHomework = viewModel.homeworkChoice() },
                        onOpen = { openedHomework = it },
                        onDone = homeworkUndo::swiped,
                    )
                    GroupTab.STUDENTS -> studentsTab(state, navigation.member, onEditMembers = navigation.edit)
                }
            }
        }
    }

    CardHomeworkDialogs(
        adding = addingHomework,
        opened = openedHomework,
        onDismiss = { addingHomework = null; openedHomework = null },
        onGive = viewModel::giveHomework,
        onEdit = viewModel::editHomework,
        undo = homeworkUndo,
    )

    if (confirmingDelete) {
        ConfirmDialog(
            text = stringResource(R.string.group_delete_confirm),
            destructive = true,
            onConfirm = { viewModel.delete(onDeleted = navigation.back) },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun HomeworkEntry(requested: Boolean, groupId: Long?, onOpen: () -> Unit) {
    var handled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(groupId) {
        if (requested && !handled && groupId != null) {
            onOpen()
            handled = true
        }
    }
}

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
            // The badge the lists use for the same state — this was the last italic grey word saying it.
            if (group.archived) ArchivedBadge(Modifier.padding(start = Spacing.inline))
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

@Composable
private fun GroupOverflowMenu(archived: Boolean, onArchive: () -> Unit, onDelete: (() -> Unit)?) {
    OverflowMenu { close ->
        ArchiveMenuItem(archived) { close(); onArchive() }
        // Only while there is something it can do — see [GroupDetailState.canDelete].
        if (onDelete != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        KlasIcons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { close(); onDelete() },
            )
        }
    }
}

@Composable
private fun groupStats(state: GroupDetailState): List<Stat> {
    val totals = state.totals
    return listOf(lessonsStat(totals), hoursStat(totals.minutes), earnedStat(totals.earned))
}

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
                icon = KlasIcons.Outlined.Groups,
                actionLabel = stringResource(R.string.group_edit_members),
                onAction = onEditMembers,
                compact = true,
            )
        }
        return
    }
    items(state.members, key = { GroupDetailKeys.member(it.student.id) }) { row ->
        val openMemberLabel = stringResource(R.string.group_member_open)
        ListItem(
            leadingContent = { Avatar(row.student.name, row.student.id) },
            headlineContent = { Text(row.student.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                // An archived member still explains the group's past lessons, so they stay listed — and
                // say so, or the card counted three members where the form offered two.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(R.plurals.lessons_count, row.attended, row.attended),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (row.student.archived) ArchivedBadge(Modifier.padding(start = Spacing.inline))
                }
            },
            // The chevron every row that opens a screen carries. Where it goes is said by the click itself:
            // the headline names the student, not their card, and a description on the glyph was read as a
            // second stop on the row.
            trailingContent = { OpensScreenMark() },
            modifier = Modifier
                .insetRowPress {
                    Modifier.clickable(it, indication = null, onClickLabel = openMemberLabel) {
                        onMemberClick(row.student.id)
                    }
                }
                .then(animateInPlace()),
        )
    }
}

private fun LazyListScope.lessonsTab(state: GroupDetailState, onLessonClick: (Long) -> Unit) {
    if (state.lessons.isEmpty()) {
        item(key = GroupDetailKeys.LESSONS_EMPTY) {
            // No action, for the same reason the student card's lessons tab has none: a lesson is
            // booked from the schedule.
            EmptyState(
                title = stringResource(R.string.group_lessons_empty),
                icon = KlasIcons.Outlined.EventBusy,
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.inline),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // What happened to the lesson, in the pill the schedule and the student card say it
                    // in — this was the last place a lesson's state was a font style.
                    StatusBadge(classStatusLabel(status), classStatusColor(status))
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
            trailingContent = { row.charge?.let { TrailingAmount(it.text()) } },
            modifier = Modifier
                .insetRowPress { Modifier.clickable(it, indication = null) { onLessonClick(row.session.first.id) } }
                .then(animateInPlace()),
        )
    }
}

private object GroupDetailKeys {
    const val HEADER = "header"
    const val STATS = DetailCard.STATS_KEY
    const val TABS = DetailCard.VIEWS_KEY
    const val STUDENTS_EMPTY = "students:empty"
    const val LESSONS_EMPTY = "lessons:empty"

    fun member(id: Long): String = "member:$id"
    fun lesson(id: Long): String = "lesson:$id"
}
