package com.seblful.klas.ui.homework

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.domain.model.HomeworkResult
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDate
import com.seblful.klas.ui.common.ContentCard
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.SegmentedChoice
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.dateTimeText
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.swipeBetweenSegments

/** The tab's two questions: what do I check, and what have I handed out. */
private enum class HomeworkSide(val labelRes: Int) {
    TO_CHECK(R.string.homework_side_to_check),
    HANDED_OUT(R.string.homework_side_handed_out),
}

/**
 * «Задания» — every student's homework, as **a list of things to do** rather than an archive.
 *
 * «Проверить» is the reason the tab exists: homework whose lesson is today, then homework whose lesson
 * came and went without anybody marking it, each with its verdicts right on the card. «Выдано» is what
 * is set and not due yet, with the button that sends it. What a student has done over the term is not
 * here — that is a reading about one person, and it is on their card.
 *
 * Homework is **set on a lesson**, never here: it belongs to the hour it was given at, and the schedule is
 * the one place that knows which hour that is. So the tab has no add button, and an empty one offers the
 * schedule instead.
 */
@Composable
fun HomeworkScreen(
    onOpenLesson: (Long) -> Unit,
    onOpenSchedule: () -> Unit,
    viewModel: HomeworkViewModel = hiltViewModel(),
) {
    var side by rememberSaveable { mutableStateOf(HomeworkSide.TO_CHECK) }
    val state by viewModel.lists.collectAsState()

    val messages = rememberMessages()
    val failed = stringResource(R.string.action_failed)
    ShowMessagesFor(viewModel.events, messages) { failed }

    KlasScreen(title = stringResource(R.string.nav_homework), messages = messages) { modifier ->
        // The same two-sided shape as «Люди»: segments on the page, and a drag across the body turns it.
        Column(modifier.swipeBetweenSegments(HomeworkSide.entries, side) { side = it }) {
            SegmentedChoice(
                options = HomeworkSide.entries,
                selected = side,
                onSelect = { side = it },
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.inline),
                label = { entry -> sideLabel(entry, (state as? Loadable.Ready)?.value) },
            )
            Box(Modifier.fillMaxSize()) {
                when (val loaded = state) {
                    Loadable.Loading -> ListSkeleton()
                    is Loadable.Ready -> HomeworkList(
                        side = side,
                        lists = loaded.value,
                        messages = messages,
                        onOpenLesson = onOpenLesson,
                        onOpenSchedule = onOpenSchedule,
                        onResult = viewModel::setResult,
                    )
                }
            }
        }
    }
}

/** A side's name, with how many are on it once there is a count to give. */
@Composable
private fun sideLabel(side: HomeworkSide, lists: HomeworkLists?): String {
    val name = stringResource(side.labelRes)
    val count = when (side) {
        HomeworkSide.TO_CHECK -> lists?.toCheck
        HomeworkSide.HANDED_OUT -> lists?.handedOut
    }
    return if (count != null && count > 0) stringResource(R.string.homework_side_count, name, count) else name
}

@Composable
private fun HomeworkList(
    side: HomeworkSide,
    lists: HomeworkLists,
    messages: Messages,
    onOpenLesson: (Long) -> Unit,
    onOpenSchedule: () -> Unit,
    onResult: (TutoringClass, HomeworkResult) -> Unit,
) {
    val empty = when (side) {
        HomeworkSide.TO_CHECK -> lists.toCheck == 0
        HomeworkSide.HANDED_OUT -> lists.handedOut == 0
    }
    if (empty) {
        EmptyState(
            title = stringResource(
                if (side == HomeworkSide.TO_CHECK) R.string.homework_empty_to_check
                else R.string.homework_empty_handed_out,
            ),
            detail = stringResource(R.string.homework_empty_detail),
            icon = if (side == HomeworkSide.TO_CHECK) Icons.Outlined.AssignmentTurnedIn else Icons.Outlined.Inbox,
            actionLabel = stringResource(R.string.homework_open_schedule),
            onAction = onOpenSchedule,
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        when (side) {
            HomeworkSide.TO_CHECK -> {
                section(R.string.homework_section_today, lists.today, checking = true, messages, onOpenLesson, onResult)
                section(R.string.homework_section_overdue, lists.overdue, checking = true, messages, onOpenLesson, onResult)
            }
            HomeworkSide.HANDED_OUT -> {
                // Booked ones need no heading: each card says the day it is for.
                section(null, lists.waiting, checking = false, messages, onOpenLesson, onResult)
                section(R.string.homework_section_unbooked, lists.unbooked, checking = false, messages, onOpenLesson, onResult)
            }
        }
    }
}

/**
 * One section of cards under an optional heading, drawn only when it has any.
 *
 * @param checking whether its cards carry the verdicts — the «Проверить» side's. «Выдано» leaves them
 *   off: homework not due yet has usually not been done yet either, and a row of verdicts under every
 *   card would invite marking it early by mistake.
 */
private fun LazyListScope.section(
    headingRes: Int?,
    rows: List<HomeworkRow>,
    checking: Boolean,
    messages: Messages,
    onOpenLesson: (Long) -> Unit,
    onResult: (TutoringClass, HomeworkResult) -> Unit,
) {
    if (rows.isEmpty()) return
    if (headingRes != null) {
        item(key = "heading:$headingRes") {
            Text(
                stringResource(headingRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.inline),
            )
        }
    }
    items(rows, key = { "homework:${it.assignment.set.lessonId}" }) { row ->
        HomeworkCard(
            row = row,
            checking = checking,
            messages = messages,
            onOpen = { onOpenLesson(row.lessonClassId) },
            onResult = onResult,
            modifier = Modifier.animateItem(),
        )
    }
}

/**
 * One lesson's homework: who and what, when it is for and when it was set, the text itself — selectable,
 * since the next thing done with «§ 12, упр. 4–7» is often pasting it somewhere — and, on the side that
 * checks, a verdict per student. Tapping the card opens the lesson it was set at, where it is edited.
 */
@Composable
private fun HomeworkCard(
    row: HomeworkRow,
    checking: Boolean,
    messages: Messages,
    onOpen: () -> Unit,
    onResult: (TutoringClass, HomeworkResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    ContentCard(onClick = onOpen, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(bulletJoin(row.title, row.subjectName), style = MaterialTheme.typography.titleMedium)
                Text(
                    bulletJoin(
                        row.dueAt?.let { stringResource(R.string.homework_due, it.dateTimeText()) }.orEmpty(),
                        stringResource(R.string.homework_set_on, row.setAt.formatDate()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SendHomeworkButton(row, messages)
        }
        SelectionContainer {
            Text(
                row.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = Spacing.inline),
            )
        }
        if (checking) HomeworkVerdicts(row = row, onResult = onResult)
    }
}
