package com.seblful.klas.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.ui.backup.BACKUP_MIME_TYPES
import com.seblful.klas.ui.backup.BackupResult
import com.seblful.klas.ui.backup.BackupViewModel
import com.seblful.klas.ui.backup.messageIn
import com.seblful.klas.ui.common.ChoiceRow
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TextFieldDialog
import com.seblful.klas.ui.common.currencyLabel
import com.seblful.klas.ui.common.languageLabel
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.valueOrNull
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

    /**
     * The language and the currency, **first**, because every screen after it is read in the answer.
     *
     * Both arrive pre-set from the phone, so this is a step to nod at rather than one to fill in — which is
     * why it is a step at all and not a question folded into the welcome screen: the guess is right most of
     * the time and wrong in a way the tutor is the only one who can see (a Minsk tutor's English phone
     * names dollars). The language applies on the tap, so the rest of the setup demonstrates the choice.
     */
    LOCALE(1),
    SUBJECTS(2),
    STUDENT(3);

    companion object {
        /** How many steps the setup actually has — the welcome screen is not one of them. */
        val total: Int = entries.count { it.number > 0 }
    }
}

/**
 * First run. It opens on a welcome step that offers three ways out — walk the setup (step 1: language and
 * currency, step 2: subjects, step 3: the first student with subjects + prices), restore a backup, or skip
 * it — so setup is offered rather than imposed. Finishing or skipping marks onboarding complete;
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
    val locale by viewModel.locale.collectAsState()
    var addingSubject by remember { mutableStateOf(false) }
    var confirmRestoreUri by remember { mutableStateOf<Uri?>(null) }
    val messages = rememberMessages()

    val context = LocalContext.current
    // Only a failure needs saying — a successful restore leaves this screen behind. It says which
    // failure: someone restoring on a fresh install has the least context for a bare «Не удалось».
    LaunchedEffect(backupViewModel, messages) {
        backupViewModel.events.stream.collect { result ->
            if (result is BackupResult.Failed) messages.show(result.failure.messageIn(context))
        }
    }

    val removeFailedMsg = stringResource(R.string.onboarding_subject_remove_failed)
    ShowMessagesFor(viewModel.events, messages) { removeFailedMsg }

    // Removing a subject is undone by adding it back: nothing can reference one added during first run,
    // so the name is the whole of it. The row it comes back as is a new one, which matters to nobody —
    // an id here has no history behind it yet.
    //
    // Said **after** the row has gone, not beside the call that removes it. Announcing it up front would
    // be «Предмет удалён» followed by «Не удалось удалить предмет» on a refusal — and a «Вернуть» offered
    // in between would add a *second* subject of the same name, which nothing refuses.
    val undoLabel = stringResource(R.string.action_undo)
    val subjectRemovedMsg = stringResource(R.string.onboarding_subject_removed)
    fun removeSubject(subject: Subject) {
        viewModel.removeSubject(subject) {
            messages.show(subjectRemovedMsg, undoLabel) { viewModel.addSubject(subject.name) }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // Whether a restore here would destroy anything is `restoreNeedsConfirming`'s — an import is a
        // full replace, and what that costs is a fact about the backup rather than about this screen.
        if (uri != null) {
            if (viewModel.restoreNeedsConfirming()) confirmRestoreUri = uri
            else backupViewModel.import(DocumentRef(uri.toString()))
        }
    }

    // Every step but the first goes back one, so the welcome step's choices (restore, skip) stay
    // reachable after starting the setup.
    val previousStep = when (step) {
        Step.WELCOME -> null
        Step.LOCALE -> Step.WELCOME
        Step.SUBJECTS -> Step.LOCALE
        Step.STUDENT -> Step.SUBJECTS
    }

    KlasScreen(
        // The welcome screen names the app in its own body, under the mark — repeating it in the bar
        // would be the same word twice on a screen with three lines of text.
        title = when (step) {
            Step.WELCOME -> null
            Step.LOCALE -> stringResource(R.string.onboarding_locale_title)
            Step.SUBJECTS -> stringResource(R.string.onboarding_subjects_title)
            Step.STUDENT -> stringResource(R.string.onboarding_student_title)
        },
        onBack = previousStep?.let { back -> { step = back } },
        messages = messages,
        // Not only inside the embedded student form: the step's «Готово» button sits *below* the form,
        // so without this the keyboard covers the one control that finishes setup.
        imeAware = true,
    ) { modifier ->
        Column(modifier) {
            // Which step, and how many are left. Without it the setup was a sequence of screens with
            // no visible end, which is the difference between "this takes a minute" as a claim and as
            // something the tutor can see.
            if (step.number > 0) {
                Text(
                    stringResource(R.string.onboarding_step, step.number, Step.total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = Spacing.gutter, top = Spacing.inline),
                )
            }
            when (step) {
                Step.WELCOME -> {
                    // The first thing a new tutor sees, and it used to be a grey paragraph of
                    // instructions with the buttons pushed to the bottom by a spacer — the app
                    // explaining its setup before saying what it was. The mark and three lines of
                    // value proposition come first now; the setup is offered underneath them.
                    Column(
                        Modifier.fillMaxWidth().weight(1f).padding(horizontal = Spacing.sectionGap),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.itemGap, Alignment.CenterVertically),
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
                            modifier = Modifier.padding(bottom = Spacing.inline),
                        )
                        ValueLine(Icons.Outlined.CalendarMonth, stringResource(R.string.onboarding_value_schedule))
                        ValueLine(Icons.Outlined.Payments, stringResource(R.string.onboarding_value_money))
                        ValueLine(Icons.Outlined.PhonelinkLock, stringResource(R.string.onboarding_value_offline))
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(Spacing.gutter),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.inline),
                    ) {
                        Button(
                            onClick = { step = Step.LOCALE },
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
                            Text(stringResource(R.string.onboarding_restore), Modifier.padding(start = Spacing.inline))
                        }
                        Caption(stringResource(R.string.onboarding_restore_desc))
                        TextButton(
                            onClick = { viewModel.complete(); onFinished() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.onboarding_skip)) }
                        Caption(stringResource(R.string.onboarding_skip_desc))
                    }
                }

                Step.LOCALE -> {
                    Text(
                        stringResource(R.string.onboarding_locale_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.gutter),
                    )
                    // The two rows wait for the row that holds them, like every other screen that is *about*
                    // a record: the defaults are somebody else's answers, and a segmented control that shows
                    // BYN and then jumps to USD reads as the app changing its mind. See [Loadable].
                    val chosen = locale.valueOrNull()
                    Column(Modifier.weight(1f)) {
                        if (chosen == null) {
                            ListSkeleton(rows = 2, rowHeight = LocaleRowHeight)
                        } else {
                            ChoiceRow(
                                title = stringResource(R.string.settings_language),
                                options = AppLanguage.entries.map { it to languageLabel(it) },
                                selected = chosen.language,
                                onSelect = viewModel::setLanguage,
                            )
                            ChoiceRow(
                                title = stringResource(R.string.settings_currency),
                                // Said here and not in Settings: there the tutor is *changing* a currency and
                                // the dialog makes the same point at the moment it costs something. Here
                                // nothing has been recorded, so it is a fact about the app rather than a
                                // warning — and the one thing a tutor might otherwise assume it does.
                                description = stringResource(R.string.onboarding_locale_currency_desc),
                                options = Currency.entries.map { it to currencyLabel(it) },
                                selected = chosen.currency,
                                onSelect = viewModel::setCurrency,
                            )
                        }
                    }
                    Button(
                        onClick = { step = Step.SUBJECTS },
                        // Never disabled: both answers are already valid — that is what pre-setting them
                        // from the phone means — so there is nothing here for the tutor to supply.
                        modifier = Modifier.fillMaxWidth().padding(Spacing.gutter),
                    ) { Text(stringResource(R.string.onboarding_continue)) }
                }

                Step.SUBJECTS -> {
                    Text(
                        stringResource(R.string.onboarding_subjects_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.gutter),
                    )
                    OutlinedButton(
                        onClick = { addingSubject = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter),
                    ) {
                        Icon(Icons.Filled.Add, null)
                        Text(stringResource(R.string.subject_add_title), Modifier.padding(start = Spacing.inline))
                    }
                    // «Нет предметов» is usually the truth on this screen — it runs on a fresh install —
                    // but not always: the step is reachable again once subjects have been added, and it
                    // must not say that over three the tutor has just typed. See [Loadable].
                    val added = subjects.valueOrNull()
                    if (added == null) {
                        ListSkeleton(Modifier.weight(1f), rows = 3, rowHeight = SubjectRowHeight)
                    } else if (added.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.subjects_empty),
                            icon = Icons.AutoMirrored.Outlined.MenuBook,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(added, key = { it.id }) { subject ->
                                // Each one can be taken back. The list was read-only, so a typo in the
                                // step whose only gate is "at least one subject" could be *finished
                                // with* but not fixed — the tutor had to skip setup to reach the screen
                                // that deletes one. Removal is undone from the snackbar rather than
                                // guarded by a dialog: nothing references a subject added here (see
                                // [OnboardingViewModel.removeSubject]), so there is nothing to warn
                                // about, and «Вернуть» costs a mis-tap one tap instead of costing every
                                // deliberate delete a confirmation.
                                ListItem(
                                    headlineContent = { Text(subject.name) },
                                    trailingContent = {
                                        IconButton(onClick = { removeSubject(subject) }) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                stringResource(R.string.action_delete),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { step = Step.STUDENT },
                        // Disabled while the list has not arrived, as it is when the list is empty: the
                        // step's gate is "at least one subject", and nothing yet says there is one.
                        enabled = !added.isNullOrEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(Spacing.gutter),
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
                        enabled = studentState.canSave,
                        modifier = Modifier.fillMaxWidth().padding(Spacing.gutter),
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

/** A subject's row in step 2 — what the skeleton stands in for while the list is being read. */
private val SubjectRowHeight = MinTouchTarget

/** A label over a segmented control, which is taller than a plain row: step 1's skeleton. */
private val LocaleRowHeight = 88.dp

/** One thing the app does, on the welcome screen: an icon and a line, left-aligned as a list. */
@Composable
private fun ValueLine(icon: ImageVector, text: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.itemGap),
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
