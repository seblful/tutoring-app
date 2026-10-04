package com.seblful.klas.ui.subjects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.ContentCard
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.FabClearance
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.ScreenSearch
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TextFieldDialog
import com.seblful.klas.ui.common.rememberMessages

@Composable
fun SubjectsScreen(
    onBack: () -> Unit,
    viewModel: SubjectsViewModel = hiltViewModel(),
) {
    val subjectsState by viewModel.subjects.collectAsState()
    val query by viewModel.query.collectAsState()
    var editing by remember { mutableStateOf<Subject?>(null) } // non-null while dialog open
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Subject?>(null) }

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

    KlasScreen(
        title = stringResource(R.string.subjects_title),
        onBack = onBack,
        messages = messages,
        search = ScreenSearch(
            query = query,
            placeholder = stringResource(R.string.subjects_search_hint),
            onQueryChange = viewModel::setQuery,
        ),
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, stringResource(R.string.subjects_add))
            }
        },
    ) { modifier ->
        Box(modifier) {
            // «Пока нет предметов» offers to create the first one, so it must not be shown about
            // subjects that have merely not been read yet — see [Loadable].
            when (val state = subjectsState) {
                Loadable.Loading -> ListSkeleton(rowHeight = SubjectRowHeight)

                is Loadable.Ready -> {
                    val subjects = state.value
                    when {
                        subjects.isEmpty() && query.isNotBlank() -> EmptyState(
                            title = stringResource(R.string.search_no_results),
                            detail = stringResource(R.string.search_no_results_detail),
                            icon = Icons.Outlined.SearchOff,
                        )

                        subjects.isEmpty() -> EmptyState(
                            title = stringResource(R.string.subjects_empty),
                            detail = stringResource(R.string.subjects_empty_detail),
                            icon = Icons.AutoMirrored.Outlined.MenuBook,
                            actionLabel = stringResource(R.string.subjects_add),
                            onAction = { adding = true },
                        )

                        // Cards, not `ListItem`s: this is a **top-level browsable list**, the same kind
                        // of thing as the students and groups lists, and the rule is now stated by
                        // nesting depth rather than by screen (see `ContentCard`). A row here was the
                        // odd one out — and it had no room for its two icon buttons, which is why they
                        // sat crushed against the trailing edge.
                        else -> LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = Spacing.gutter,
                                top = Spacing.gutter,
                                end = Spacing.gutter,
                                // Past the add button, so it never sits on the last row.
                                bottom = FabClearance,
                            ),
                            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
                        ) {
                            items(subjects, key = { it.id }) { subject ->
                                ContentCard(
                                    onClick = { editing = subject },
                                    modifier = Modifier.animateItem(),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.AutoMirrored.Outlined.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            subject.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = Spacing.leading),
                                        )
                                        // The bin stays a row control here, deliberately: a subject has
                                        // no card of its own to put a lifecycle action on. Renaming is
                                        // the card's tap, so the pencil is gone.
                                        IconButton(onClick = { deleting = subject }) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                // Named, so a screen reader says which one it deletes.
                                                stringResource(R.string.subject_delete_named, subject.name),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        TextFieldDialog(
            title = stringResource(R.string.subject_add_title),
            label = stringResource(R.string.subject_name_label),
            capitalization = KeyboardCapitalization.Sentences,
            initial = "",
            onConfirm = { viewModel.save(0, it) },
            onDismiss = { adding = false },
        )
    }
    editing?.let { subject ->
        TextFieldDialog(
            title = stringResource(R.string.subject_edit_title),
            label = stringResource(R.string.subject_name_label),
            capitalization = KeyboardCapitalization.Sentences,
            initial = subject.name,
            onConfirm = { viewModel.save(subject.id, it) },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { subject ->
        ConfirmDialog(
            text = stringResource(R.string.subject_delete_confirm),
            destructive = true,
            onConfirm = { viewModel.delete(subject) },
            onDismiss = { deleting = null },
        )
    }
}

/**
 * A subject's card — a name beside a pair of 48 dp icon buttons, 12 dp in from its top and bottom — which
 * is what the skeleton stands in for. It said 56, a `ListItem`'s, from before the rows were cards, and the
 * list jumped when the subjects arrived.
 */
private val SubjectRowHeight = 72.dp
