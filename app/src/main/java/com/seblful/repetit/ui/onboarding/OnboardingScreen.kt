package com.seblful.repetit.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.repetit.R
import com.seblful.repetit.ui.backup.BACKUP_MIME_TYPES
import com.seblful.repetit.storage.DocumentRef
import com.seblful.repetit.ui.backup.BackupResult
import com.seblful.repetit.ui.backup.BackupViewModel
import com.seblful.repetit.ui.common.ConfirmDialog
import com.seblful.repetit.ui.common.EmptyState
import com.seblful.repetit.ui.common.TextFieldDialog
import com.seblful.repetit.ui.common.constrainedContent
import com.seblful.repetit.ui.students.StudentFormContent
import com.seblful.repetit.ui.students.StudentFormViewModel

private enum class Step { WELCOME, SUBJECTS, STUDENT }

/**
 * First run. It opens on a welcome step that offers three ways out — walk the setup (step 1:
 * subjects, step 2: the first student with subjects + prices), restore a backup, or skip it — so
 * setup is offered rather than imposed. Finishing or skipping marks onboarding complete;
 * [onFinished] lets the host refresh. A **restore** ends first run through
 * `BackupRestore` instead, which is why this screen has nothing to do on success: the flag it flips
 * swaps this screen for the main tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    var step by remember { mutableStateOf(Step.WELCOME) }
    val subjects by viewModel.subjects.collectAsState()
    var addingSubject by remember { mutableStateOf(false) }
    var confirmRestoreUri by remember { mutableStateOf<Uri?>(null) }
    val snackbarHost = remember { SnackbarHostState() }

    val restoreError = stringResource(R.string.backup_error)
    LaunchedEffect(Unit) {
        // Only a failure needs saying — a successful restore leaves this screen behind.
        backupViewModel.events.stream.collect { result ->
            if (result == BackupResult.ERROR) snackbarHost.showSnackbar(restoreError)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // Nothing to replace on a fresh install, so no confirmation — unless the user already
        // added subjects in step 1 and came back here, which a restore would wipe.
        if (uri != null) {
            if (subjects.isEmpty()) backupViewModel.import(DocumentRef(uri.toString())) else confirmRestoreUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (step) {
                                Step.WELCOME -> R.string.onboarding_welcome_title
                                Step.SUBJECTS -> R.string.onboarding_subjects_title
                                Step.STUDENT -> R.string.onboarding_student_title
                            }
                        )
                    )
                },
                navigationIcon = {
                    // Every step but the first goes back one, so the welcome step's choices
                    // (restore, skip) stay reachable after starting the setup.
                    val back = when (step) {
                        Step.WELCOME -> null
                        Step.SUBJECTS -> Step.WELCOME
                        Step.STUDENT -> Step.SUBJECTS
                    }
                    if (back != null) {
                        IconButton(onClick = { step = back }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).constrainedContent()) {
            when (step) {
                Step.WELCOME -> {
                    Text(
                        stringResource(R.string.onboarding_welcome_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { step = Step.SUBJECTS },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.onboarding_start)) }
                        OutlinedButton(
                            onClick = { restoreLauncher.launch(BACKUP_MIME_TYPES) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Download, null)
                            Text(stringResource(R.string.onboarding_restore), Modifier.padding(start = 8.dp))
                        }
                        Caption(stringResource(R.string.onboarding_restore_desc))
                        TextButton(
                            onClick = { viewModel.complete(); onFinished() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.onboarding_skip)) }
                        Caption(stringResource(R.string.onboarding_skip_desc))
                    }
                }

                Step.SUBJECTS -> {
                    Text(
                        stringResource(R.string.onboarding_subjects_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                    OutlinedButton(
                        onClick = { addingSubject = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Icon(Icons.Filled.Add, null)
                        Text(stringResource(R.string.subject_add_title), Modifier.padding(start = 8.dp))
                    }
                    if (subjects.isEmpty()) {
                        EmptyState(stringResource(R.string.subjects_empty), Modifier.weight(1f))
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(subjects, key = { it.id }) { subject ->
                                ListItem(headlineContent = { Text(subject.name) })
                            }
                        }
                    }
                    Button(
                        onClick = { step = Step.STUDENT },
                        enabled = subjects.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) { Text(stringResource(R.string.onboarding_continue)) }
                }

                Step.STUDENT -> {
                    val studentVm: StudentFormViewModel = hiltViewModel()
                    val studentState by studentVm.state.collectAsState()
                    StudentFormContent(
                        state = studentState,
                        viewModel = studentVm,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    Button(
                        onClick = { studentVm.save { viewModel.complete(); onFinished() } },
                        enabled = studentState.isValid,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) { Text(stringResource(R.string.onboarding_finish)) }
                }
            }
        }
    }

    if (addingSubject) {
        TextFieldDialog(
            title = stringResource(R.string.subject_add_title),
            label = stringResource(R.string.subject_name_label),
            initial = "",
            onConfirm = { viewModel.addSubject(it) },
            onDismiss = { addingSubject = false },
        )
    }

    confirmRestoreUri?.let { uri ->
        ConfirmDialog(
            text = stringResource(R.string.backup_import_confirm),
            confirmLabel = stringResource(R.string.backup_import),
            onConfirm = { backupViewModel.import(DocumentRef(uri.toString())) },
            onDismiss = { confirmRestoreUri = null },
        )
    }
}

/** The one-line explanation under a welcome-step action. */
@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
