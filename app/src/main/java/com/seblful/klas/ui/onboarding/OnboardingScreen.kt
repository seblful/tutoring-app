package com.seblful.klas.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.seblful.klas.R
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.ui.backup.BACKUP_MIME_TYPES
import com.seblful.klas.ui.backup.BackupResult
import com.seblful.klas.ui.backup.BackupViewModel
import com.seblful.klas.ui.backup.ConfirmRestoreDialog
import com.seblful.klas.ui.backup.messageIn
import com.seblful.klas.ui.common.ChoiceRow
import com.seblful.klas.ui.common.CurrencyPicker
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.KlasLogo
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.KlasWordmark
import com.seblful.klas.ui.common.LanguageChoices
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.languageLabel
import com.seblful.klas.ui.common.predictiveBack
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.rememberOpenDocument
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.students.StudentFormContent
import com.seblful.klas.ui.students.StudentFormViewModel
import com.seblful.klas.ui.subjects.SubjectNameDialog
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.metroViewModel

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
    STUDENT(3),
    ;

    companion object {
        /** How many steps the setup actually has — the welcome screen is not one of them. */
        val total: Int = entries.count { it.number > 0 }
    }
}

/**
 * First run. It opens on a welcome step that offers three ways out — walk the setup (step 1: language and
 * currency, step 2: subjects, step 3: the first student with subjects + prices), restore a backup, or skip
 * it — so setup is offered rather than imposed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = metroViewModel(),
    backupViewModel: BackupViewModel = metroViewModel(),
) {
    // Saveable, so turning the phone on step 3 does not throw the tutor back to the welcome screen.
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val locale by viewModel.locale.collectAsStateWithLifecycle()
    var addingSubject by rememberSaveable { mutableStateOf(false) }
    var confirmRestore by rememberSaveable { mutableStateOf<String?>(null) }
    val messages = rememberMessages()
    val noPickerMsg = stringResource(R.string.student_contact_failed)

    // The newest: the language step swaps the context's resources, and a failure after it is worded in the
    // language just picked, not the one the screen opened in.
    val context by rememberUpdatedState(LocalContext.current)
    // Only a failure needs saying — a successful restore leaves this screen behind. It says which
    // failure: someone restoring on a fresh install has the least context for a bare «Не удалось».
    LaunchedEffect(backupViewModel, messages) {
        backupViewModel.events.stream.collect { result ->
            if (result is BackupResult.Failed) messages.show(result.failure.messageIn(context))
        }
    }

    val removeFailedMsg = stringResource(R.string.onboarding_subject_remove_failed)
    val nameTakenMsg = stringResource(R.string.subject_name_taken)
    val saveFailedMsg = stringResource(R.string.save_failed)
    val studentVm: StudentFormViewModel = metroViewModel()
    ShowMessagesFor(studentVm.events, messages) { saveFailedMsg }
    ShowMessagesFor(viewModel.events, messages) { event ->
        when (event) {
            OnboardingViewModel.Event.REMOVE_REFUSED -> removeFailedMsg
            OnboardingViewModel.Event.NAME_TAKEN -> nameTakenMsg
            OnboardingViewModel.Event.WRITE_FAILED -> saveFailedMsg
        }
    }

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

    val restore = rememberOpenDocument(noPickerMsg, messages) { document ->
        // Whether a restore here would destroy anything is `restoreNeedsConfirming`'s — an import is a
        // full replace, and what that costs is a fact about the backup rather than about this screen.
        // The handle is kept as its string: it survives rotation in a saveable, which a value class does not.
        if (viewModel.restoreNeedsConfirming()) confirmRestore = document.value
        else backupViewModel.import(document)
    }

    // Every step but the first goes back one, so the welcome step's choices (restore, skip) stay
    // reachable after starting the setup.
    val previousStep = when (step) {
        Step.WELCOME -> null
        Step.LOCALE -> Step.WELCOME
        Step.SUBJECTS -> Step.LOCALE
        Step.STUDENT -> Step.SUBJECTS
    }
    // The system back, too: the screen sits outside the `NavDisplay`, so without this the gesture on step 2
    // left the app mid-setup while the arrow above went back a step. Outside it, the gesture's preview is
    // this screen's to draw: the step shrinks under the finger (`predictiveBack`).
    val backState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = backState,
        isBackEnabled = previousStep != null,
        onBackCompleted = { previousStep?.let { step = it } },
    )

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
    ) { modifier, bottomInset ->
        Column(modifier.padding(bottomInset).predictiveBack(backState)) {
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
                    //
                    // Centred while it fits, scrolled when it does not: at a large font scale, or on a phone
                    // turned sideways, the mark and the three lines drew over the buttons below them.
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .heightIn(min = maxHeight)
                                .padding(horizontal = Spacing.sectionGap),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap, Alignment.CenterVertically),
                        ) {
                            KlasLogo(Modifier.size(96.dp))
                            KlasWordmark(Modifier.width(160.dp).height(64.dp))
                            Text(
                                stringResource(R.string.onboarding_tagline),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = Spacing.inline),
                            )
                            ValueLine(
                                KlasIcons.Outlined.CalendarMonth,
                                stringResource(R.string.onboarding_value_schedule),
                            )
                            ValueLine(KlasIcons.Outlined.Payments, stringResource(R.string.onboarding_value_money))
                            ValueLine(
                                KlasIcons.Outlined.PhonelinkLock,
                                stringResource(R.string.onboarding_value_offline),
                            )
                        }
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
                            onClick = { restore(BACKUP_MIME_TYPES) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(KlasIcons.Filled.Download, null)
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
                    // a record: the defaults are somebody else's answers, and a currency field that shows
                    // BYN and then jumps to USD reads as the app changing its mind. See [Loadable].
                    val chosen = locale.valueOrNull()
                    Column(Modifier.weight(1f)) {
                        if (chosen == null) {
                            ListSkeleton(rows = 2, rowHeight = LocaleRowHeight)
                        } else {
                            ChoiceRow(
                                title = stringResource(R.string.settings_language),
                                options = LanguageChoices.map { it to languageLabel(it) },
                                selected = chosen.language,
                                onSelect = viewModel::setLanguage,
                            )
                            CurrencyPicker(
                                // Setup applies immediately; Settings confirms a change because it also
                                // relabels existing records. Both explain that no amounts are converted.
                                description = stringResource(R.string.onboarding_locale_currency_desc),
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
                        Icon(KlasIcons.Filled.Add, null)
                        Text(stringResource(R.string.subjects_add), Modifier.padding(start = Spacing.inline))
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
                            icon = KlasIcons.Outlined.MenuBook,
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
                                                KlasIcons.Outlined.Delete,
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
                    val studentState by studentVm.state.collectAsStateWithLifecycle()
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
        SubjectNameDialog(onConfirm = { viewModel.addSubject(it) }, onDismiss = { addingSubject = false })
    }

    confirmRestore?.let { document ->
        ConfirmRestoreDialog(
            onConfirm = { backupViewModel.import(DocumentRef(document)) },
            onDismiss = { confirmRestore = null },
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private val SubjectRowHeight = MinTouchTarget

private val LocaleRowHeight = 88.dp

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
