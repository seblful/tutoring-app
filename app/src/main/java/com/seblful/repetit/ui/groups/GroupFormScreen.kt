package com.seblful.repetit.ui.groups

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.repetit.R
import com.seblful.repetit.ui.common.ConfirmDialog
import com.seblful.repetit.ui.common.ModeSelector
import com.seblful.repetit.ui.common.constrainedContent

/**
 * Create or edit one group: name, note, default mode and roster — plus, for a saved group, booking a
 * lesson for it and retiring it (archive) or removing it (delete, refused once it has lessons).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupFormScreen(
    onBack: () -> Unit,
    onBookLesson: (Long) -> Unit,
    viewModel: GroupFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val form = state.form
    var confirmingDelete by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val deleteBlockedMsg = stringResource(R.string.group_delete_blocked)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (viewModel.isNew) R.string.group_add_title else R.string.group_edit_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save { onBack() } }, enabled = state.isValid) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .constrainedContent()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text(stringResource(R.string.group_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.note,
                onValueChange = { v -> viewModel.update { it.copy(note = v) } },
                label = { Text(stringResource(R.string.student_note_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.student_mode_label), style = MaterialTheme.typography.labelLarge)
            ModeSelector(selected = form.mode, onSelect = { m -> viewModel.update { it.copy(mode = m) } })

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(stringResource(R.string.group_members_title), style = MaterialTheme.typography.titleMedium)
            if (state.candidates.isEmpty()) {
                Text(
                    stringResource(R.string.group_no_students),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    stringResource(R.string.group_members_required),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                state.candidates.forEach { student ->
                    val selected = student.id in form.memberIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = selected,
                                onValueChange = { on -> viewModel.toggleMember(student.id, on) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = selected, onCheckedChange = null)
                        Text(student.name, Modifier.padding(start = 8.dp))
                    }
                }
            }

            // What the roster can be booked for, shown while it is being built: a group lesson bills
            // each member at their own rate, so only a subject *everyone* has a price for is bookable.
            if (form.memberIds.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    stringResource(R.string.group_bookable_subjects_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.bookableSubjects.isEmpty()) {
                    Text(
                        stringResource(R.string.group_no_shared_subjects),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        state.bookableSubjects.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!viewModel.isNew) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                FilledTonalButton(
                    // Save first, then book: the booking form reads the *stored* roster, so an edit
                    // left unsaved here would otherwise book the wrong set of students.
                    onClick = { viewModel.save { id -> onBookLesson(id) } },
                    enabled = state.isValid && state.bookableSubjects.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.EventAvailable, contentDescription = null)
                    Text(
                        stringResource(R.string.group_book_lesson),
                        Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.setArchived(!state.archived) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (state.archived) R.string.action_unarchive else R.string.action_archive
                        )
                    )
                }
                TextButton(
                    onClick = { confirmingDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (confirmingDelete) {
        ConfirmDialog(
            text = stringResource(R.string.group_delete_confirm),
            onConfirm = {
                viewModel.delete { deleted ->
                    if (deleted) onBack()
                    else Toast.makeText(context, deleteBlockedMsg, Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}
