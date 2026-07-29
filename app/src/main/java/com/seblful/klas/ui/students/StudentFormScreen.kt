package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.format.MoneyField
import com.seblful.klas.ui.common.LeaveIntent
import com.seblful.klas.ui.common.ModeSelector
import com.seblful.klas.ui.common.UnsavedChangesDialog
import com.seblful.klas.ui.common.constrainedContent
import com.seblful.klas.ui.common.leaveIntent
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.ShowMessagesFor

/** Full-screen "add student" with required subjects + prices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentFormScreen(
    onBack: () -> Unit,
    viewModel: StudentFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // A save that failed must not look like one that went through: the form stays open and says so.
    val messages = rememberMessages()
    val saveFailedMsg = stringResource(R.string.save_failed)
    ShowMessagesFor(viewModel.events, messages) { saveFailedMsg }

    // Back — the arrow or the system gesture — asks before throwing a filled-in form away.
    var leaving by remember { mutableStateOf<LeaveIntent?>(null) }
    fun requestLeave() {
        when (val intent = leaveIntent(state)) {
            LeaveIntent.LEAVE -> onBack()
            else -> leaving = intent
        }
    }
    BackHandler(enabled = state.isDirty) { requestLeave() }

    Scaffold(
        snackbarHost = { SnackbarHost(messages.hostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.student_add_title)) },
                navigationIcon = {
                    IconButton(onClick = ::requestLeave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onBack) }, enabled = state.canSave) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        StudentFormContent(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }

    leaving?.let { intent ->
        UnsavedChangesDialog(
            intent = intent,
            onSave = { viewModel.save(onBack) },
            onDiscard = { leaving = null; onBack() },
            onKeepEditing = { leaving = null },
        )
    }
}

/** The form body, without a scaffold, so it can also be embedded (e.g. in onboarding). */
@Composable
fun StudentFormContent(
    state: StudentFormViewModel.UiState,
    viewModel: StudentFormViewModel,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    Column(
        modifier
            .constrainedContent()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { v -> viewModel.update { it.copy(name = v) } },
            label = { Text(stringResource(R.string.student_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.phone,
            onValueChange = { v -> viewModel.update { it.copy(phone = v) } },
            label = { Text(stringResource(R.string.student_phone_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.email,
            onValueChange = { v -> viewModel.update { it.copy(email = v) } },
            label = { Text(stringResource(R.string.student_email_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
        Text(
            stringResource(R.string.student_form_subjects_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.allSubjects.isEmpty()) {
            Text(
                stringResource(R.string.student_form_no_subjects),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                stringResource(R.string.student_form_subjects_required),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            state.allSubjects.forEach { subject ->
                val selected = subject.id in state.selectedSubjectIds
                val price = form.subjectPrices[subject.id] ?: MoneyField()
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .toggleable(
                                value = selected,
                                onValueChange = { on -> viewModel.toggleSubject(subject.id, on) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = selected, onCheckedChange = null)
                        Text(subject.name, Modifier.padding(start = 8.dp))
                    }
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = price.text,
                            onValueChange = { viewModel.setPriceText(subject.id, it) },
                            label = { Text(stringResource(R.string.price_label)) },
                            isError = price.isError,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(140.dp),
                        )
                    }
                }
            }
        }
    }
}
