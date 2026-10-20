package com.seblful.klas.ui.subjects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.seblful.klas.R
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.ContentCard
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.FabListPadding
import com.seblful.klas.ui.common.IconAvatar
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.NoSearchResults
import com.seblful.klas.ui.common.ScreenSearch
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TextFieldDialog
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
fun SubjectsScreen(
    onBack: () -> Unit,
    viewModel: SubjectsViewModel = metroViewModel(),
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
                Icon(KlasIcons.Filled.Add, stringResource(R.string.subjects_add))
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
                        subjects.isEmpty() && query.isNotBlank() -> NoSearchResults()

                        subjects.isEmpty() -> EmptyState(
                            title = stringResource(R.string.subjects_empty),
                            detail = stringResource(R.string.subjects_empty_detail),
                            icon = KlasIcons.Outlined.MenuBook,
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
                            // Past the add button, so it never sits on the last row.
                            contentPadding = FabListPadding,
                            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
                        ) {
                            items(subjects, key = { it.id }) { subject ->
                                ContentCard(
                                    onClick = { editing = subject },
                                    modifier = animateInPlace(),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconAvatar(KlasIcons.Outlined.MenuBook)
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
                                                KlasIcons.Filled.Delete,
                                                // Named, so a screen reader says which one it deletes.
                                                stringResource(R.string.subject_delete_named, subject.name),
                                                // A row's own controls are a rung quieter than its name,
                                                // as `ListItem` draws them everywhere else: a black bin
                                                // was the heaviest mark on every card of this list.
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        SubjectNameDialog(onConfirm = { viewModel.save(0, it) }, onDismiss = { adding = false })
    }
    editing?.let { subject ->
        SubjectNameDialog(
            onConfirm = { viewModel.save(subject.id, it) },
            onDismiss = { editing = null },
            renaming = subject.name,
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
 * Naming a subject — a new one, or [renaming] an existing one. The same question wherever a subject can be
 * made: this list, the student form's «Добавить предмет» and first run's subjects step; the three spelled
 * out the same title, label and capitalisation, the last of them being a subject's name is written as one.
 */
@Composable
fun SubjectNameDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit, renaming: String? = null) {
    TextFieldDialog(
        title = stringResource(if (renaming == null) R.string.subject_add_title else R.string.subject_edit_title),
        label = stringResource(R.string.subject_name_label),
        capitalization = KeyboardCapitalization.Sentences,
        initial = renaming.orEmpty(),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

private val SubjectRowHeight = 72.dp
