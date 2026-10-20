package com.seblful.klas.ui.homework

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.queries.homework.HomeworkLists
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.FabClearance
import com.seblful.klas.ui.common.FilterChipRow
import com.seblful.klas.ui.common.FilterMenuAction
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.LessonKindIcon
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.SegmentedPage
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch

private enum class HomeworkSide(val labelRes: Int) {
    CURRENT(R.string.homework_side_current),
    ARCHIVE(R.string.homework_side_archive),
}

/** Задания» — every student's and group's homework: **what is current, and the archive**. */
@Composable
fun HomeworkScreen(
    /** The way out of «Сначала добавьте ученика» — the step it names, as the schedule's message carries. */
    onAddStudent: () -> Unit,
    viewModel: HomeworkViewModel = metroViewModel(),
) {
    var side by rememberSaveable { mutableStateOf(HomeworkSide.CURRENT) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The dialog's choices are taken when it opens — see `HomeworkViewModel.choices`.
    var adding by remember { mutableStateOf<List<AssigneeChoice>?>(null) }
    var opened by remember { mutableStateOf<Opened?>(null) }
    val scope = rememberCoroutineScope()

    val messages = rememberMessages()
    val failed = stringResource(R.string.action_failed)
    ShowMessagesFor(viewModel.events, messages) { failed }
    val undo = homeworkUndo(
        messages = messages,
        setDone = viewModel::setDone,
        putBack = viewModel::putBack,
        delete = viewModel::delete,
        restore = viewModel::restore,
    )
    val needStudent = stringResource(R.string.homework_need_student)
    val addStudentLabel = stringResource(R.string.schedule_add_student)

    fun add() {
        // Before the tab has loaded there is no answer yet, and the tap waits for the list like any other.
        val choices = viewModel.choices() ?: return
        if (choices.isEmpty()) messages.show(needStudent, addStudentLabel, onAddStudent) else adding = choices
    }

    val loaded = (state as? Loadable.Ready)?.value
    KlasScreen(
        title = stringResource(R.string.nav_homework),
        page = side,
        messages = messages,
        actions = {
            // Keep the clear action available if the last homework for the selected filter is deleted.
            if (loaded != null && (loaded.filterAssignees.isNotEmpty() || loaded.filter != null)) {
                FilterMenuAction(
                    name = stringResource(R.string.homework_filter),
                    allLabel = stringResource(R.string.homework_filter_all),
                    options = loaded.filterAssignees,
                    selected = loaded.filter,
                    optionLabel = loaded.captions::titleOf,
                    onSelect = viewModel::setFilter,
                    leading = { who ->
                        LessonKindIcon(if (who is Assignee.Group) LessonKind.GROUP else LessonKind.INDIVIDUAL)
                    },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = ::add) {
                Icon(KlasIcons.Filled.Add, stringResource(R.string.homework_add))
            }
        },
    ) { modifier ->
        // The same two-sided shape as «Люди»: segments on the page, and a drag across the body turns it.
        SegmentedPage(
            options = HomeworkSide.entries,
            selected = side,
            onSelect = { side = it },
            label = { entry -> sideLabel(entry, loaded?.lists) },
            modifier = modifier,
            header = {
                // Above both lists and outside their scroll, as the schedule's: a narrowed tab says so.
                loaded?.filterName?.let { name -> FilterChipRow(name, onClear = { viewModel.setFilter(null) }) }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                when (val ready = state) {
                    Loadable.Loading -> ListSkeleton()
                    is Loadable.Ready -> HomeworkList(
                        side = side,
                        lists = ready.value.lists,
                        filterName = ready.value.filterName,
                        messages = messages,
                        undo = undo,
                        onAdd = ::add,
                        onOpen = { row -> scope.launch { opened = Opened(row, viewModel.choicesFor(row)) } },
                    )
                }
            }
        }
    }

    adding?.let { choices ->
        HomeworkDialog(
            choices = choices,
            onSave = viewModel::give,
            onDismiss = { adding = null },
        )
    }
    opened?.let { (row, choices) ->
        HomeworkDialog(
            choices = choices,
            editing = row.homework,
            onSave = { assignee, text -> viewModel.edit(row.homework, assignee, text) },
            onDelete = { undo.deleted(row.homework) },
            onDismiss = { opened = null },
        )
    }
}

private data class Opened(val row: HomeworkRow, val choices: List<AssigneeChoice>)

@Composable
private fun sideLabel(side: HomeworkSide, lists: HomeworkLists?): String {
    val name = stringResource(side.labelRes)
    val count = lists?.current?.size?.takeIf { side == HomeworkSide.CURRENT && it > 0 }
    return if (count != null) stringResource(R.string.homework_side_count, name, count) else name
}

@Composable
private fun HomeworkList(
    side: HomeworkSide,
    lists: HomeworkLists,
    filterName: String?,
    messages: Messages,
    undo: HomeworkUndo,
    onAdd: () -> Unit,
    onOpen: (HomeworkRow) -> Unit,
) {
    val rows = if (side == HomeworkSide.CURRENT) lists.current else lists.archive
    if (rows.isEmpty() && filterName != null) {
        // The way out is the chip above, which is where the tutor put the filter.
        EmptyState(
            title = stringResource(R.string.homework_filter_empty, filterName),
            detail = stringResource(R.string.homework_filter_empty_detail),
            icon = KlasIcons.Outlined.Assignment,
        )
        return
    }
    if (rows.isEmpty()) {
        when (side) {
            HomeworkSide.CURRENT -> EmptyState(
                title = stringResource(R.string.homework_empty_current),
                detail = stringResource(R.string.homework_empty_current_detail),
                icon = KlasIcons.Outlined.Assignment,
                actionLabel = stringResource(R.string.homework_add),
                onAction = onAdd,
            )
            HomeworkSide.ARCHIVE -> EmptyState(
                title = stringResource(R.string.homework_empty_archive),
                detail = stringResource(R.string.homework_empty_archive_detail),
                icon = KlasIcons.Outlined.Inventory2,
            )
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.gutter,
            end = Spacing.gutter,
            top = Spacing.inline,
            bottom = FabClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        items(rows, key = { it.homework.id }) { row ->
            SwipeableHomework(
                row = row,
                onSwipe = { undo.swiped(row.homework, done = side == HomeworkSide.CURRENT) },
                onClick = { onOpen(row) },
                messages = messages,
                nested = false,
                modifier = animateInPlace(),
            )
        }
    }
}
