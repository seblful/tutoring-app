package com.seblful.tutoringapp.ui.subjects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.seblful.tutoringapp.R
import com.seblful.tutoringapp.domain.model.Subject
import com.seblful.tutoringapp.ui.common.ConfirmDialog
import com.seblful.tutoringapp.ui.common.EmptyState
import com.seblful.tutoringapp.ui.common.TextFieldDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsScreen(
    onBack: () -> Unit,
    viewModel: SubjectsViewModel = hiltViewModel(),
) {
    val subjects by viewModel.subjects.collectAsState()
    var editing by remember { mutableStateOf<Subject?>(null) } // non-null while dialog open
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Subject?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subjects_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
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
            if (subjects.isEmpty()) {
                EmptyState(stringResource(R.string.subjects_empty))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(subjects, key = { it.id }) { subject ->
                        ListItem(
                            headlineContent = { Text(subject.name) },
                            trailingContent = {
                                androidx.compose.foundation.layout.Row {
                                    IconButton(onClick = { editing = subject }) {
                                        Icon(Icons.Filled.Edit, stringResource(R.string.action_edit))
                                    }
                                    IconButton(onClick = { deleting = subject }) {
                                        Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                                    }
                                }
                            },
                        )
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
