package com.seblful.klas.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhonelinkLock
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.ui.backup.BACKUP_MIME_TYPES
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.ui.backup.BackupResult
import com.seblful.klas.ui.backup.messageIn
import com.seblful.klas.ui.backup.BackupViewModel
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.TextFieldDialog
import com.seblful.klas.ui.common.constrainedContent
import com.seblful.klas.ui.students.StudentFormContent
import com.seblful.klas.ui.students.StudentFormViewModel

/**
 * The first run, as a sequence.
 *
 * [number] is what the step indicator counts: the welcome screen is not a step of the setup, it is
 * the offer of one, so the setup reads «Шаг 1 из 2» and «Шаг 2 из 2» rather than making the tutor
 * guess how much is left. The count comes from the enum, so adding a step cannot leave the label
 * saying otherwise.
 */
private enum class Step(val number: Int) {
    WELCOME(0),
    SUBJECTS(1),
    STUDENT(2);

    companion object {
        /** How many steps the setup actually has — the welcome screen is not one of them. */
        val total: Int = entries.count { it.number > 0 }
    }
}

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

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        // Only a failure needs saying — a successful restore leaves this screen behind. It says which
        // failure: someone restoring on a fresh install has the least context for a bare «Не удалось».
        backupViewModel.events.stream.collect { result ->
            if (result is BackupResult.Failed) {
                snackbarHost.showSnackbar(result.failure.messageIn(context))
            }
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
                    // The welcome screen names the app in its own body, under the mark — repeating it
                    // in the bar would be the same word twice on a screen with three lines of text.
                    when (step) {
                        Step.WELCOME -> Unit
                        Step.SUBJECTS -> Text(stringResource(R.string.onboarding_subjects_title))
                        Step.STUDENT -> Text(stringResource(R.string.onboarding_student_title))
                    }
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
            // Which step, and how many are left. Without it the setup was a sequence of screens with
            // no visible end, which is the difference between "this takes a minute" as a claim and as
            // something the tutor can see.
            if (step.number > 0) {
                Text(
                    stringResource(R.string.onboarding_step, step.number, Step.total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
            }
            when (step) {
                Step.WELCOME -> {
                    // The first thing a new tutor sees, and it used to be a grey paragraph of
                    // instructions with the buttons pushed to the bottom by a spacer — the app
                    // explaining its setup before saying what it was. The mark and three lines of
                    // value proposition come first now; the setup is offered underneath them.
                    Column(
                        Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_logo),
                            contentDescription = null,
                            modifier = Modifier.size(96.dp),
                            tint = Color.Unspecified,
                        )
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            stringResource(R.string.onboarding_tagline),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        ValueLine(Icons.Filled.CalendarMonth, stringResource(R.string.onboarding_value_schedule))
                        ValueLine(Icons.Filled.Payments, stringResource(R.string.onboarding_value_money))
                        ValueLine(Icons.Filled.PhonelinkLock, stringResource(R.string.onboarding_value_offline))
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { step = Step.SUBJECTS },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.onboarding_start)) }
                        // Each of the three ways out carries its own one-line consequence, so the
                        // choice is made with the outcome in view rather than after a paragraph
                        // above the buttons that has to describe all three at once.
                        Caption(stringResource(R.string.onboarding_welcome_desc))
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
                        EmptyState(
                            title = stringResource(R.string.subjects_empty),
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            modifier = Modifier.weight(1f),
                        )
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

/** One thing the app does, on the welcome screen: an icon and a line, left-aligned as a list. */
@Composable
private fun ValueLine(icon: ImageVector, text: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
