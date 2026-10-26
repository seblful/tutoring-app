package com.seblful.klas.ui.subjects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.ui.common.AddRow
import com.seblful.klas.ui.common.AddRowInSection
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.NoSearchResults
import com.seblful.klas.ui.common.ScreenSearch
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TextFieldDialog
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.plusInset
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.designsystem.component.DialogAction
import com.seblful.klas.ui.designsystem.component.KlasRow
import com.seblful.klas.ui.designsystem.component.RowEnd
import com.seblful.klas.ui.designsystem.component.SlicePosition
import com.seblful.klas.ui.designsystem.component.SurfaceRow
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * The subjects, as a Settings page (D1): each says how it is used — which is also why a used one cannot go —
 * and opens its editor, which renames it and, while nothing holds it, deletes it with Undo (R4: no bins on
 * rows). No add button floating over the list: «Add a subject» is its last row, as in the student form.
 */
@Composable
fun SubjectsScreen(
    onBack: () -> Unit,
    viewModel: SubjectsViewModel = metroViewModel(),
) {
    val subjectsState by viewModel.subjects.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SubjectRow?>(null) } // non-null while dialog open
    var adding by remember { mutableStateOf(false) }

    val messages = rememberMessages()
    val deleteBlockedMsg = stringResource(R.string.subject_delete_blocked)
    val taughtToGroupMsg = stringResource(R.string.subject_delete_blocked_group)
    val takenMsg = stringResource(R.string.subject_delete_blocked_students)
    val failedMsg = stringResource(R.string.action_failed)
    val takenNameMsg = stringResource(R.string.subject_name_taken)
    ShowMessagesFor(viewModel.events, messages) { event ->
        when (event) {
            SubjectsViewModel.Event.DELETE_BLOCKED_HAS_CLASSES -> deleteBlockedMsg
            SubjectsViewModel.Event.DELETE_BLOCKED_TAUGHT_TO_GROUP -> taughtToGroupMsg
            SubjectsViewModel.Event.DELETE_BLOCKED_TAKEN_BY_STUDENTS -> takenMsg
            SubjectsViewModel.Event.NAME_TAKEN -> takenNameMsg
            SubjectsViewModel.Event.WRITE_FAILED -> failedMsg
        }
    }
    // No confirm: only an unused subject can be deleted, so the Undo can always put it back.
    val deletedMsg = stringResource(R.string.subject_deleted)
    LaunchedEffect(viewModel, deletedMsg) {
        viewModel.deletions.stream.collect { subject ->
            messages.undoable(deletedMsg) { viewModel.restore(subject) }
        }
    }

    KlasScreen(
        title = stringResource(R.string.subjects_title),
        onBack = onBack,
        messages = messages,
        search = ScreenSearch(
            query = query,
            placeholder = stringResource(R.string.subjects_search_hint),
            onQueryChange = viewModel::setQuery,
        ),
    ) { modifier, bottomInset ->
        Box(modifier) {
            // «Пока нет предметов» offers to create the first one, so it must not be shown about
            // subjects that have merely not been read yet — see [Loadable].
            when (val state = subjectsState) {
                Loadable.Loading -> ListSkeleton(Modifier.padding(bottomInset), rowHeight = SubjectRowHeight)

                is Loadable.Ready -> {
                    val rows = state.value
                    when {
                        rows.isEmpty() && query.isNotBlank() -> NoSearchResults(Modifier.padding(bottomInset))

                        rows.isEmpty() -> EmptyState(
                            title = stringResource(R.string.subjects_empty),
                            detail = stringResource(R.string.subjects_empty_detail),
                            icon = KlasIcons.Outlined.MenuBook,
                            actionLabel = stringResource(R.string.subjects_add),
                            onAction = { adding = true },
                            modifier = Modifier.padding(bottomInset),
                        )

                        else -> SubjectRows(rows, onOpen = { editing = it }, onAdd = { adding = true }, bottomInset)
                    }
                }
            }
        }
    }

    if (adding) {
        SubjectNameDialog(onConfirm = { viewModel.save(0, it) }, onDismiss = { adding = false })
    }
    editing?.let { row ->
        SubjectNameDialog(
            onConfirm = { viewModel.save(row.subject.id, it) },
            onDismiss = { editing = null },
            renaming = row.subject.name,
            onDelete = { viewModel.delete(row.subject) }.takeIf { row.unused },
        )
    }
}

/** The subjects as one surface of rows, «Add a subject» the last of them. */
@Composable
private fun SubjectRows(
    rows: List<SubjectRow>,
    onOpen: (SubjectRow) -> Unit,
    onAdd: () -> Unit,
    bottomInset: PaddingValues,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.gutter).plusInset(bottomInset),
        verticalArrangement = Arrangement.Top,
    ) {
        itemsIndexed(rows, key = { _, row -> row.subject.id }, contentType = { _, _ -> "subject" }) { index, row ->
            SurfaceRow(
                position = SlicePosition.of(index, rows.size + 1),
                onClick = { onOpen(row) },
                modifier = animateInPlace(),
                contentPadding = NoPadding,
            ) {
                KlasRow(title = row.subject.name, supporting = usageLine(row), end = RowEnd.Opens)
            }
        }
        item(key = "add", contentType = "add") {
            SurfaceRow(position = SlicePosition.LAST, onClick = onAdd, contentPadding = AddPadding) {
                AddRow(stringResource(R.string.subjects_add), onClick = onAdd, AddRowInSection)
            }
        }
    }
}

/** The rows bring their own inset, as a card's do; see [SurfaceRow]'s `contentPadding`. */
private val NoPadding = PaddingValues()
private val AddPadding = PaddingValues(vertical = Spacing.tight)

/** «6 students · 1 group · 12–25 $/h», or that nothing uses it yet. */
@Composable
private fun usageLine(row: SubjectRow): String {
    if (row.unused) return stringResource(R.string.subject_unused)
    val low = row.lowest
    val high = row.highest
    val rates = when {
        low == null || high == null -> ""
        low == high -> stringResource(R.string.price_per_hour, low.text())
        else -> stringResource(R.string.price_per_hour, stringResource(R.string.money_range, low.text(), high.text()))
    }
    return bulletJoin(
        if (row.students > 0) pluralStringResource(R.plurals.students_count, row.students, row.students) else "",
        if (row.groups > 0) pluralStringResource(R.plurals.subject_groups_count, row.groups, row.groups) else "",
        rates,
    )
}

/**
 * Naming a subject — a new one, or [renaming] an existing one. The same question wherever a subject can be
 * made: this list, the student form's «Добавить предмет» and first run's subjects step; the three spelled
 * out the same title, label and capitalisation, the last of them being a subject's name is written as one.
 *
 * @param onDelete offered beside Cancel while nothing holds the subject; deleting closes the dialog.
 */
@Composable
fun SubjectNameDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    renaming: String? = null,
    onDelete: (() -> Unit)? = null,
) {
    val delete = stringResource(R.string.action_delete)
    TextFieldDialog(
        title = stringResource(if (renaming == null) R.string.subject_add_title else R.string.subject_edit_title),
        label = stringResource(R.string.subject_name_label),
        capitalization = KeyboardCapitalization.Sentences,
        initial = renaming.orEmpty(),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        extra = onDelete?.let { DialogAction(delete, onClick = { it(); onDismiss() }, destructive = true) },
    )
}

private val SubjectRowHeight = 72.dp
