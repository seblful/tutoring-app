package com.seblful.klas.ui.homework

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.queries.homework.HomeworkLists
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.queries.homework.HomeworkSection
import com.seblful.klas.queries.homework.bySection
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.FabClearance
import com.seblful.klas.ui.common.FilterChipRow
import com.seblful.klas.ui.common.FilterMenuAction
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.LessonKindIcon
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.PickBackHandler
import com.seblful.klas.ui.common.ScrollsToTopOnReselect
import com.seblful.klas.ui.common.SectionTitle
import com.seblful.klas.ui.common.SegmentedPage
import com.seblful.klas.ui.common.SettingsGear
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.constrainedContent
import com.seblful.klas.ui.common.plusInset
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.titleOrPicked
import com.seblful.klas.ui.designsystem.component.KlasFab
import com.seblful.klas.ui.theme.KlasIcons
import com.seblful.klas.ui.theme.klasSemantics
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
    onSettings: () -> Unit = {},
    viewModel: HomeworkViewModel = metroViewModel(),
) {
    var side by rememberSaveable { mutableStateOf(HomeworkSide.CURRENT) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Current homework picked by a long-press: while any is, the bar is theirs.
    val picked by viewModel.batch.selected.collectAsStateWithLifecycle()
    PickBackHandler(picked.size, viewModel.batch::clear)
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
        title = titleOrPicked(stringResource(R.string.nav_homework), picked.size),
        onBack = viewModel.batch::clear.takeIf { picked.isNotEmpty() },
        page = side,
        messages = messages,
        actions = {
            if (picked.isNotEmpty()) {
                MarkPickedDone(viewModel.batch, messages)
                return@KlasScreen
            }
            loaded?.let { HomeworkFilterAction(it, viewModel::setFilter) }
            SettingsGear(onSettings)
        },
        // The cards lay themselves out in columns (see [HomeworkList]); the tabs above them keep a column's width.
        maxContentWidth = Dp.Infinity,
        floatingActionButton = {
            // While homework is picked the bar is theirs, and giving more waits.
            if (picked.isEmpty()) KlasFab(stringResource(R.string.homework_add), onClick = ::add)
        },
    ) { modifier, bottomInset ->
        // The same two-sided shape as «Люди»: segments on the page, and a drag across the body turns it.
        SegmentedPage(
            options = HomeworkSide.entries,
            selected = side,
            onSelect = {
                side = it
                viewModel.batch.clear()
            },
            label = { entry -> sideLabel(entry, loaded?.lists) },
            modifier = modifier,
            header = {
                // Above both lists and outside their scroll, as the schedule's: a narrowed tab says so.
                loaded?.filterName?.let { name -> FilterChipRow(name, onClear = { viewModel.setFilter(null) }) }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                when (val ready = state) {
                    Loadable.Loading -> ListSkeleton(Modifier.constrainedContent().padding(bottomInset))
                    is Loadable.Ready -> HomeworkList(
                        side = side,
                        lists = ready.value.lists,
                        filterName = ready.value.filterName,
                        messages = messages,
                        undo = undo,
                        picking = HomeworkPicking(picked, enabled = side == HomeworkSide.CURRENT) {
                            viewModel.batch.toggle(it.homework.id)
                        },
                        onAdd = ::add,
                        onOpen = { row -> scope.launch { opened = Opened(row, viewModel.choicesFor(row)) } },
                        bottomInset = bottomInset,
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

@Composable
private fun HomeworkFilterAction(loaded: HomeworkState, onSelect: (Assignee?) -> Unit) {
    // Keep the clear action available if the last homework for the selected filter is deleted.
    if (loaded.filterAssignees.isEmpty() && loaded.filter == null) return
    FilterMenuAction(
        name = stringResource(R.string.homework_filter),
        allLabel = stringResource(R.string.homework_filter_all),
        options = loaded.filterAssignees,
        selected = loaded.filter,
        optionLabel = loaded.captions::titleOf,
        onSelect = onSelect,
        leading = { who ->
            LessonKindIcon(if (who is Assignee.Group) LessonKind.GROUP else LessonKind.INDIVIDUAL)
        },
    )
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
    picking: HomeworkPicking,
    onAdd: () -> Unit,
    onOpen: (HomeworkRow) -> Unit,
    bottomInset: PaddingValues,
) {
    val rows = if (side == HomeworkSide.CURRENT) lists.current else lists.archive
    if (rows.isEmpty()) {
        HomeworkEmpty(side, filterName, onAdd, Modifier.padding(bottomInset))
        return
    }
    // Cards side by side once the window holds two: a column of them on a tablet is a strip down the middle.
    val gridState = rememberLazyGridState()
    ScrollsToTopOnReselect { gridState.animateScrollToItem(0) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MinCardWidth),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(
            start = Spacing.gutter,
            end = Spacing.gutter,
            top = Spacing.inline,
            bottom = FabClearance,
        ).plusInset(bottomInset),
        horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
    ) {
        val card: @Composable LazyGridItemScope.(HomeworkRow) -> Unit = { row ->
            SwipeableHomework(
                row = row,
                onSwipe = { undo.swiped(row.homework, done = side == HomeworkSide.CURRENT) },
                onClick = { onOpen(row) },
                messages = messages,
                nested = false,
                modifier = animateInPlace(),
                picking = picking,
            )
        }
        if (side == HomeworkSide.ARCHIVE) {
            items(rows, key = { it.homework.id }) { card(it) }
        } else {
            // By the lesson it is for: past lessons' first, then today's — the order the tutor works through it.
            bySection(rows).forEach { (section, inSection) ->
                item(key = section.name, span = { GridItemSpan(maxLineSpan) }, contentType = "section") {
                    SectionTitle(
                        sectionLabel(section),
                        modifier = animateInPlace().padding(top = Spacing.inline),
                        color = if (section == HomeworkSection.OVERDUE) klasSemantics.overdue else Color.Unspecified,
                    )
                }
                items(inSection, key = { it.homework.id }) { card(it) }
            }
        }
    }
}

@Composable
private fun HomeworkEmpty(side: HomeworkSide, filterName: String?, onAdd: () -> Unit, modifier: Modifier) {
    when {
        // The way out is the chip above, which is where the tutor put the filter.
        filterName != null -> EmptyState(
            title = stringResource(R.string.homework_filter_empty, filterName),
            detail = stringResource(R.string.homework_filter_empty_detail),
            icon = KlasIcons.Outlined.Assignment,
            modifier = modifier,
        )
        side == HomeworkSide.CURRENT -> EmptyState(
            title = stringResource(R.string.homework_empty_current),
            detail = stringResource(R.string.homework_empty_current_detail),
            icon = KlasIcons.Outlined.Assignment,
            actionLabel = stringResource(R.string.homework_add),
            onAction = onAdd,
            modifier = modifier,
        )
        else -> EmptyState(
            title = stringResource(R.string.homework_empty_archive),
            detail = stringResource(R.string.homework_empty_archive_detail),
            icon = KlasIcons.Outlined.Inventory2,
            modifier = modifier,
        )
    }
}

@Composable
private fun sectionLabel(section: HomeworkSection): String = stringResource(
    when (section) {
        HomeworkSection.OVERDUE -> R.string.homework_section_past
        HomeworkSection.TODAY -> R.string.homework_section_today
        HomeworkSection.NEXT_7_DAYS -> R.string.homework_section_next_7_days
        HomeworkSection.LATER -> R.string.homework_section_later
        HomeworkSection.UNBOOKED -> R.string.homework_unbooked
    },
)

/** The narrowest a homework card gets before the grid drops a column: its share button and tick still fit. */
private val MinCardWidth = 340.dp
