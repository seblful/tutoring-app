package com.seblful.klas.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.reports.Taught
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatHours
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.ui.common.AddRow
import com.seblful.klas.ui.common.AddRowInSection
import com.seblful.klas.ui.common.ArchiveMenuItem
import com.seblful.klas.ui.common.ArchivedBadge
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.FactsLine
import com.seblful.klas.ui.common.HeadlineCardInset
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.LoadingDetailScreen
import com.seblful.klas.ui.common.MenuItem
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.MinHeadlineSize
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.common.OverflowMenu
import com.seblful.klas.ui.common.OverviewSection
import com.seblful.klas.ui.common.SectionCard
import com.seblful.klas.ui.common.SectionNote
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TitleSize
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.archivedMessage
import com.seblful.klas.ui.common.plusInset
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.sectionRowColors
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.homework.CardHomeworkDialogs
import com.seblful.klas.ui.homework.HomeworkSection
import com.seblful.klas.ui.homework.homeworkUndo
import com.seblful.klas.ui.people.NextLessonCard
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasSemantics
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

private data class GroupNavigation(
    val back: () -> Unit,
    /** Whether the bar draws [back] as an arrow; not beside the list the card was opened from. */
    val backArrow: Boolean,
    val edit: () -> Unit,
    val member: (Long) -> Unit,
    val lesson: (Long) -> Unit,
    val book: () -> Unit,
    val seeAll: (GroupSection) -> Unit,
)

/**
 * A group's card, laid out as the student's is: what it is taught and for how much, then **an overview** —
 * its next lesson, what it has earned, the latest of its lessons and homework (each opening in full,
 * [GroupSection]) and everybody in it.
 *
 * @param backArrow false beside the People list the card was opened from: the list is the way to another.
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
    onSeeAll: (GroupSection) -> Unit = {},
    initialHomework: Boolean = false,
    backArrow: Boolean = true,
) {
    when (val overview = viewModel.overview.collectAsStateWithLifecycle().value) {
        null -> LoadingDetailScreen(onBack.takeIf { backArrow })
        else -> GroupCard(
            overview,
            viewModel,
            GroupNavigation(onBack, backArrow, onEdit, onMemberClick, onLessonClick, onBookLesson, onSeeAll),
            initialHomework,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupCard(
    overview: GroupOverview,
    viewModel: GroupDetailViewModel,
    navigation: GroupNavigation,
    initialHomework: Boolean,
) {
    val state = overview.details
    val group = state.group

    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingArchive by remember { mutableStateOf(false) }
    // Taken when the dialog opens — see `GroupDetailViewModel.homeworkChoice`.
    var addingHomework by remember { mutableStateOf<AssigneeChoice?>(null) }
    var openedHomework by remember { mutableStateOf<HomeworkRow?>(null) }
    HomeworkEntry(initialHomework, group?.id) { addingHomework = viewModel.homeworkChoice() }

    val messages = rememberMessages()
    ShowGroupMessages(viewModel, messages)
    val homeworkUndo = groupHomeworkUndo(viewModel, messages)

    // The group's name is the subject of the screen, as a student's is on theirs — the same large,
    // collapsing title, so the two cards read as one pattern.
    KlasScreen(
        title = group?.name,
        onBack = navigation.back.takeIf { navigation.backArrow },
        titleSize = TitleSize.Large,
        messages = messages,
        actions = {
            // Booking is the next lesson's «+», in the card it books into.
            NamedIconButton(KlasIcons.Outlined.Edit, stringResource(R.string.action_edit), onClick = navigation.edit)
            // Retiring and removing the group, behind the overflow — where a student's are.
            if (group != null) {
                GroupOverflowMenu(
                    archived = group.archived,
                    // Archiving calls the planned lessons off, so it asks first when there are any — as a
                    // student's archive does. Restoring takes nothing away and never asks.
                    onArchive = {
                        if (!group.archived && state.plannedLessons > 0) confirmingArchive = true
                        else viewModel.setArchived(!group.archived)
                    },
                    onDelete = { confirmingDelete = true }.takeIf { state.canDelete },
                )
            }
        },
    ) { modifier, bottomInset ->
        val cardModifier = Modifier.padding(horizontal = Spacing.gutter)
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = Spacing.sectionGap).plusInset(bottomInset),
            // Separate cards, each a section of its own, rather than parts of one surface.
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            item(key = GroupDetailKeys.HEADER, contentType = GroupDetailKeys.HEADER) {
                GroupHeader(state, Modifier.padding(horizontal = Spacing.gutter))
            }
            // An archived group's planned lessons were called off, and the form books no archived group.
            if (group != null && !group.archived) {
                item(key = GroupDetailKeys.NEXT, contentType = GroupDetailKeys.NEXT) {
                    NextLessonCard(
                        next = overview.next,
                        onOpen = navigation.lesson,
                        onBook = navigation.book,
                        modifier = animateInPlace(cardModifier),
                    )
                }
            }
            item(key = GroupDetailKeys.EARNED, contentType = GroupDetailKeys.EARNED) {
                EarnedCard(state.totals, modifier = animateInPlace(cardModifier))
            }
            // Members before lessons: who is in it, who comes and who owes is what a tutor checks first.
            item(key = GroupDetailKeys.MEMBERS, contentType = GroupDetailKeys.MEMBERS) {
                MembersSection(
                    members = state.members,
                    onMemberClick = navigation.member,
                    onEditMembers = navigation.edit,
                    modifier = animateInPlace(cardModifier),
                )
            }
            item(key = GroupDetailKeys.LESSONS, contentType = GroupDetailKeys.LESSONS) {
                LessonsSection(
                    overview = overview,
                    onLessonClick = navigation.lesson,
                    onSeeAll = { navigation.seeAll(GroupSection.LESSONS) },
                    modifier = animateInPlace(cardModifier),
                )
            }
            item(key = GroupDetailKeys.HOMEWORK, contentType = GroupDetailKeys.HOMEWORK) {
                HomeworkSection(
                    owner = group?.let { Assignee.Group(it.id) },
                    lists = state.homework,
                    messages = messages,
                    onAdd = { addingHomework = viewModel.homeworkChoice() },
                    onOpen = { openedHomework = it },
                    onDone = homeworkUndo::swiped,
                    onSeeAll = { navigation.seeAll(GroupSection.HOMEWORK) },
                    modifier = animateInPlace(cardModifier),
                )
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

    if (confirmingArchive) {
        val parts = listOf(
            pluralStringResource(R.plurals.student_archive_lessons_part, state.plannedLessons, state.plannedLessons),
            stringResource(R.string.student_archive_question),
        )
        ConfirmDialog(
            text = parts.joinToString(" "),
            confirmLabel = stringResource(R.string.action_archive),
            onConfirm = { viewModel.setArchived(true) },
            onDismiss = { confirmingArchive = false },
        )
    }

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

/** A hand collector rather than `ShowMessagesFor`: an archive is said with its Undo. */
@Composable
internal fun ShowGroupMessages(viewModel: GroupDetailViewModel, messages: Messages) {
    val resources by rememberUpdatedState(LocalResources.current)
    LaunchedEffect(viewModel) {
        viewModel.events.stream.collect { event ->
            when (event) {
                GroupDetailViewModel.Event.DeleteBlockedHasLessons ->
                    messages.show(resources.getString(R.string.group_delete_blocked))
                is GroupDetailViewModel.Event.Archived -> messages.show(
                    resources.archivedMessage(event.cancelled.size),
                    resources.getString(R.string.action_undo),
                ) { viewModel.undoArchive(event) }
                is GroupDetailViewModel.Event.ArchiveUndoKept -> messages.show(
                    resources.getQuantityString(R.plurals.archive_undo_kept, event.count, event.count),
                )
                GroupDetailViewModel.Event.WriteFailed -> messages.show(resources.getString(R.string.action_failed))
            }
        }
    }
}

@Composable
internal fun groupHomeworkUndo(viewModel: GroupDetailViewModel, messages: Messages) =
    homeworkUndo(
        messages = messages,
        setDone = viewModel::setHomeworkDone,
        putBack = viewModel::putHomeworkBack,
        delete = viewModel::deleteHomework,
        restore = viewModel::restoreHomework,
    )

/** Under the name, as the student's contact line is: what the group is taught, and for how much. */
@Composable
private fun GroupHeader(state: GroupDetailState, modifier: Modifier = Modifier) {
    val group = state.group ?: return
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = MinTouchTarget), verticalAlignment = Alignment.CenterVertically) {
            FactsLine(
                listOf(
                    state.subjectName ?: stringResource(R.string.group_no_subject),
                    stringResource(R.string.price_per_hour_per_student, group.price.text()),
                ),
                modifier = Modifier.weight(1f, fill = false),
            )
            if (group.archived) ArchivedBadge(Modifier.padding(start = Spacing.inline))
        }
        if (group.note.isNotBlank()) {
            Text(
                group.note,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.tight),
            )
        }
    }
}

/**
 * What the group has brought in, the way a student's card heads with their balance: the money first, then how
 * much teaching it was for and how much more is booked.
 */
@Composable
internal fun EarnedCard(totals: Taught.Totals, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        Column(HeadlineCardInset, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            Text(
                stringResource(R.string.stat_earned),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                totals.earned.text(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = with(LocalDensity.current) { MinHeadlineSize.toSp() },
                    maxFontSize = MaterialTheme.typography.headlineSmall.fontSize,
                ),
            )
            Text(
                bulletJoin(
                    pluralStringResource(R.plurals.lessons_count, totals.lessons, totals.lessons),
                    stringResource(R.string.hours_value, formatHours(totals.minutes)),
                    if (totals.upcoming > 0) stringResource(R.string.stat_upcoming, totals.upcoming) else "",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LessonsSection(
    overview: GroupOverview,
    onLessonClick: (Long) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = overview.details.lessons
    OverviewSection(
        title = stringResource(R.string.group_lessons_title),
        count = all.size,
        onSeeAll = onSeeAll.takeIf { all.size > overview.lessons.size },
        modifier = modifier,
    ) {
        if (all.isEmpty()) SectionNote(stringResource(R.string.group_lessons_empty))
        overview.lessons.forEach { row ->
            GroupLessonItem(row, onClick = { onLessonClick(row.session.first.id) }, colors = sectionRowColors())
        }
    }
}

/** Everybody in it, in place: a group is a handful, so there is no longer list to open. */
@Composable
private fun MembersSection(
    members: List<GroupMemberRow>,
    onMemberClick: (Long) -> Unit,
    onEditMembers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OverviewSection(
        title = stringResource(R.string.group_students_title),
        count = members.size,
        onSeeAll = null,
        modifier = modifier,
    ) {
        if (members.isEmpty()) SectionNote(stringResource(R.string.group_no_members))
        members.forEach { row ->
            GroupMemberItem(row, onClick = { onMemberClick(row.student.id) }, colors = sectionRowColors())
        }
        // A roster is changed on the group's form, so that is where this leads.
        AddRow(stringResource(R.string.group_edit_members), onEditMembers, AddRowInSection)
    }
}

@Composable
private fun GroupOverflowMenu(archived: Boolean, onArchive: () -> Unit, onDelete: (() -> Unit)?) {
    OverflowMenu { close ->
        ArchiveMenuItem(archived) { close(); onArchive() }
        // Only while there is something it can do — see [GroupDetailState.canDelete].
        if (onDelete != null) {
            MenuItem(
                text = {
                    Text(
                        stringResource(R.string.action_delete),
                        color = klasSemantics.destructive,
                    )
                },
                leadingIcon = {
                    Icon(
                        KlasIcons.Outlined.Delete,
                        contentDescription = null,
                        tint = klasSemantics.destructive,
                    )
                },
                onClick = { close(); onDelete() },
            )
        }
    }
}

internal object GroupDetailKeys {
    const val HEADER = "header"
    const val NEXT = "next"
    const val EARNED = "earned"
    const val LESSONS = "lessons"
    const val HOMEWORK = "homework"
    const val MEMBERS = "members"
    const val LESSONS_EMPTY = "lessons:empty"

    fun lesson(id: Long): String = "lesson:$id"
}
