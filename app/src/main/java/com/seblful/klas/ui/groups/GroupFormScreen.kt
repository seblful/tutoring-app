package com.seblful.klas.ui.groups

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.domain.model.Student
import com.seblful.klas.ui.common.Avatar
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.DropdownField
import com.seblful.klas.ui.common.LeaveIntent
import com.seblful.klas.ui.common.ModeSelector
import com.seblful.klas.ui.common.UnsavedChangesDialog
import com.seblful.klas.ui.common.constrainedContent
import com.seblful.klas.ui.common.leaveIntent

/**
 * Create or edit one group: **subject**, name, note, hourly rate, default mode and roster — plus, for
 * a saved group, retiring it (archive) or removing it (delete, refused once it has lessons).
 *
 * Subject comes before the roster because it decides it: pick one and the member list narrows to the
 * students priced for it, since a group only ever teaches that subject. Leaving it unset is allowed
 * only so that a group without one keeps working; those list every active student instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupFormScreen(
    onBack: () -> Unit,
    viewModel: GroupFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val form = state.form
    var confirmingDelete by remember { mutableStateOf(false) }

    // Back — the arrow or the system gesture — asks before throwing away edits to a group.
    var leaving by remember { mutableStateOf<LeaveIntent?>(null) }
    fun requestLeave() {
        when (val intent = leaveIntent(dirty = state.isDirty, canSave = state.isValid)) {
            LeaveIntent.LEAVE -> onBack()
            else -> leaving = intent
        }
    }
    BackHandler(enabled = state.isDirty) { requestLeave() }

    val context = LocalContext.current
    val deleteBlockedMsg = stringResource(R.string.group_delete_blocked)
    val notPricedMsg = stringResource(R.string.group_members_not_priced)
    LaunchedEffect(Unit) {
        viewModel.events.stream.collect { event ->
            val message = when (event) {
                GroupFormViewModel.Event.DELETE_BLOCKED_HAS_LESSONS -> deleteBlockedMsg
                GroupFormViewModel.Event.MEMBERS_NOT_PRICED_FOR_SUBJECT -> notPricedMsg
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

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
                    IconButton(onClick = ::requestLeave) {
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
            // Subject first: it decides who may be in the group. 0 is the "not chosen" row — a real
            // option rather than a hidden state, because a group legitimately has none and the
            // tutor needs to see that rather than guess.
            DropdownField(
                label = stringResource(R.string.group_subject_label),
                selectedText = state.subjects.firstOrNull { it.id == form.subjectId }?.name
                    ?: stringResource(R.string.group_subject_none),
                options = listOf(0L to stringResource(R.string.group_subject_none)) +
                    state.subjects.map { it.id to it.name },
                onSelect = { id -> viewModel.setSubject(id.takeIf { it != 0L }) },
            )
            Text(
                stringResource(
                    if (form.subjectId == null) R.string.group_subject_none_hint
                    else R.string.group_subject_hint
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = form.note,
                onValueChange = { v -> viewModel.update { it.copy(note = v) } },
                label = { Text(stringResource(R.string.student_note_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            // The group's own rate: what each member of its lessons is billed, whatever the subject.
            // Required — a group prices its own lessons, so it has to say what they cost — and shown
            // plainly rather than behind a tap, because it is the substance of the group, not a detail.
            OutlinedTextField(
                value = form.price.text,
                onValueChange = { v -> viewModel.update { it.copy(price = it.price.typed(v)) } },
                label = { Text(stringResource(R.string.group_price_label)) },
                isError = form.price.isError,
                supportingText = { Text(stringResource(R.string.group_price_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.student_mode_label), style = MaterialTheme.typography.labelLarge)
            ModeSelector(selected = form.mode, onSelect = { m -> viewModel.update { it.copy(mode = m) } })

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.group_members_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                // Counts what would actually be saved, not what is ticked: switching subject can
                // strand a tick on someone the new subject excludes.
                if (state.selectedMemberIds.isNotEmpty()) {
                    Text(
                        stringResource(R.string.group_members_selected, state.selectedMemberIds.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            when {
                // "Nobody is priced for this subject" is a different problem from "you have no
                // students at all", and only the first is fixed by changing the subject.
                !state.hasStudents -> Text(
                    stringResource(
                        if (form.subjectId == null) R.string.group_no_students
                        else R.string.group_no_students_for_subject
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.selectedMemberIds.isEmpty() -> Text(
                    stringResource(R.string.group_members_required),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.candidates.forEach { student ->
                MemberRow(
                    student = student,
                    selected = student.id in form.memberIds,
                    onToggle = { on -> viewModel.toggleMember(student.id, on) },
                )
            }

            if (!viewModel.isNew) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
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
                viewModel.delete(onDeleted = onBack)
            },
            onDismiss = { confirmingDelete = false },
        )
    }

    leaving?.let { intent ->
        UnsavedChangesDialog(
            intent = intent,
            onSave = { viewModel.save { onBack() } },
            onDiscard = { leaving = null; onBack() },
            onKeepEditing = { leaving = null },
        )
    }
}

/**
 * One tappable person in the member picker. Just who they are: the group's lessons are billed at the
 * group's rate, so there is no per-person amount to show here any more.
 */
@Composable
private fun MemberRow(
    student: Student,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .toggleable(value = selected, onValueChange = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(student.name, student.id)
        Text(
            student.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        Checkbox(checked = selected, onCheckedChange = null)
    }
}
