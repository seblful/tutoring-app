package com.seblful.klas.ui.groups

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.queries.groups.GroupDetails
import com.seblful.klas.queries.groups.GroupLessonRow
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.LoadingDetailScreen
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.plusInset
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.homework.CardHomeworkDialogs
import com.seblful.klas.ui.homework.homeworkItems
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import kotlin.time.Instant

/** One of a group's lists in full — what «See all» under a section of its card opens. */
@Composable
fun GroupListScreen(
    section: GroupSection,
    onBack: () -> Unit,
    onLessonClick: (Long) -> Unit,
    viewModel: GroupDetailViewModel = assistedMetroViewModel(),
) {
    when (val screenState = viewModel.state.collectAsStateWithLifecycle().value) {
        Loadable.Loading -> LoadingDetailScreen(onBack)
        is Loadable.Ready -> GroupList(screenState.value, section, viewModel, onBack, onLessonClick)
    }
}

@Composable
private fun GroupList(
    state: GroupDetails,
    section: GroupSection,
    viewModel: GroupDetailViewModel,
    onBack: () -> Unit,
    onLessonClick: (Long) -> Unit,
) {
    // Taken when the dialog opens — see `GroupDetailViewModel.homeworkChoice`.
    var addingHomework by remember { mutableStateOf<AssigneeChoice?>(null) }
    var openedHomework by remember { mutableStateOf<HomeworkRow?>(null) }
    // A value here, not a delegate the list's builder reads — see docs/ui-conventions.md.
    val now = viewModel.minute.collectAsStateWithLifecycle().value

    val messages = rememberMessages()
    ShowGroupMessages(viewModel, messages)
    val homeworkUndo = groupHomeworkUndo(viewModel, messages)

    KlasScreen(
        title = stringResource(section.titleRes),
        onBack = onBack,
        messages = messages,
    ) { modifier, bottomInset ->
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = Spacing.sectionGap).plusInset(bottomInset),
        ) {
            when (section) {
                GroupSection.LESSONS -> lessons(state.lessons, now, onLessonClick)
                GroupSection.HOMEWORK -> homeworkItems(
                    owner = state.group?.let { Assignee.Group(it.id) },
                    lists = state.homework,
                    messages = messages,
                    onAdd = { addingHomework = viewModel.homeworkChoice() },
                    onOpen = { openedHomework = it },
                    onDone = homeworkUndo::swiped,
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
}

private fun LazyListScope.lessons(rows: List<GroupLessonRow>, now: Instant, onLessonClick: (Long) -> Unit) {
    if (rows.isEmpty()) {
        item(key = GroupDetailKeys.LESSONS_EMPTY, contentType = GroupDetailKeys.LESSONS_EMPTY) {
            // No action: a lesson is booked from the schedule's form, reached from the card's next lesson.
            EmptyState(
                title = stringResource(R.string.group_lessons_empty),
                icon = KlasIcons.Outlined.EventBusy,
                compact = true,
            )
        }
        return
    }
    items(rows, key = { GroupDetailKeys.lesson(it.session.first.id) }, contentType = { "lesson" }) { row ->
        GroupLessonItem(row, now, onClick = { onLessonClick(row.session.first.id) }, modifier = animateInPlace())
    }
}
