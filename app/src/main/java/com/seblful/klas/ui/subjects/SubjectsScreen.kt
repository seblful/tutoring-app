package com.seblful.klas.ui.subjects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.SearchField
import com.seblful.klas.ui.common.TextFieldDialog
import com.seblful.klas.ui.common.constrainedContent
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.ShowMessagesFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsScreen(
    onBack: () -> Unit,
    viewModel: SubjectsViewModel = hiltViewModel(),
) {
    val subjectsState by viewModel.subjects.collectAsState()
    val query by viewModel.query.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var editing by remember { mutableStateOf<Subject?>(null) } // non-null while dialog open
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Subject?>(null) }
    var searching by remember { mutableStateOf(false) }

    val messages = rememberMessages()
    val deleteBlockedMsg = stringResource(R.string.subject_delete_blocked)
    val taughtToGroupMsg = stringResource(R.string.subject_delete_blocked_group)
    ShowMessagesFor(viewModel.events, messages) { event ->
        when (event) {
            SubjectsViewModel.Event.DELETE_BLOCKED_HAS_CLASSES -> deleteBlockedMsg
            SubjectsViewModel.Event.DELETE_BLOCKED_TAUGHT_TO_GROUP -> taughtToGroupMsg
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(messages.hostState) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    if (searching) {
                        SearchField(
                            query = query,
                            onQueryChange = viewModel::setQuery,
                            placeholder = stringResource(R.string.subjects_search_hint),
                        )
                    } else {
                        Text(stringResource(R.string.subjects_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = { searching = false; viewModel.setQuery("") }) {
                            Icon(Icons.Filled.SearchOff, stringResource(R.string.search_clear))
                        }
                    } else {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Filled.Search, stringResource(R.string.action_search))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, stringResource(R.string.action_add))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // «Пока нет предметов» offers to create the first one, so it must not be shown about
            // subjects that have merely not been read yet — see [Loadable].
            when (val state = subjectsState) {
                Loadable.Loading -> ListSkeleton(
                    Modifier.constrainedContent(),
                    rowHeight = SubjectRowHeight,
                )

                is Loadable.Ready -> {
                    val subjects = state.value
                    when {
                        subjects.isEmpty() && query.isNotBlank() -> EmptyState(
                            title = stringResource(R.string.search_no_results),
                            detail = stringResource(R.string.search_no_results_detail),
                            icon = Icons.Filled.SearchOff,
                        )

                        subjects.isEmpty() -> EmptyState(
                            title = stringResource(R.string.subjects_empty),
                            detail = stringResource(R.string.subjects_empty_detail),
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            actionLabel = stringResource(R.string.subjects_add),
                            onAction = { adding = true },
                        )

                        else -> LazyColumn(Modifier.fillMaxSize().constrainedContent()) {
                            items(subjects, key = { it.id }) { subject ->
                                ListItem(
                                    headlineContent = { Text(subject.name) },
                                    trailingContent = {
                                        Row {
                                            IconButton(onClick = { editing = subject }) {
                                                Icon(Icons.Filled.Edit, stringResource(R.string.action_edit))
                                            }
                                            IconButton(onClick = { deleting = subject }) {
                                                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                                            }
                                        }
                                    },
                                    modifier = Modifier.animateItem(),
                                )
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
            initial = "",
            onConfirm = { viewModel.save(0, it) },
            onDismiss = { adding = false },
        )
    }
    editing?.let { subject ->
        TextFieldDialog(
            title = stringResource(R.string.subject_edit_title),
            label = stringResource(R.string.subject_name_label),
            initial = subject.name,
            onConfirm = { viewModel.save(subject.id, it) },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { subject ->
        ConfirmDialog(
            text = stringResource(R.string.subject_delete_confirm),
            onConfirm = { viewModel.delete(subject) },
            onDismiss = { deleting = null },
        )
    }
}

/** A one-line `ListItem` with a trailing icon pair — what the skeleton stands in for. */
private val SubjectRowHeight = 56.dp
