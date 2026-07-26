package com.seblful.repetit.ui.students

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.repetit.R
import com.seblful.repetit.domain.LessonFilter
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.Payment
import com.seblful.repetit.domain.model.Subject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.seblful.repetit.ui.common.LessonFilterChips
import com.seblful.repetit.ui.common.LessonKindIcon
import com.seblful.repetit.ui.common.RateHistoryList
import com.seblful.repetit.ui.common.Stat
import com.seblful.repetit.ui.common.StatRow
import com.seblful.repetit.ui.common.StudentEditDialog
import com.seblful.repetit.ui.common.TextFieldDialog
import com.seblful.repetit.ui.common.balanceDescription
import com.seblful.repetit.ui.common.classModeLabel
import com.seblful.repetit.ui.common.classStatusColor
import com.seblful.repetit.ui.common.classStatusLabel
import com.seblful.repetit.ui.common.constrainedContent
import com.seblful.repetit.ui.common.lessonsStat
import com.seblful.repetit.ui.common.text
import com.seblful.repetit.format.formatDate
import com.seblful.repetit.format.formatDateTime
import com.seblful.repetit.format.formatHours
import com.seblful.repetit.format.MoneyField
import com.seblful.repetit.format.toAmountInput

/**
 * The three things a student's card is *about*, each its own tab.
 *
 * They used to be three stacked sections in one scroll, which meant reaching a payment scrolled past
 * every subject and every lesson — and the lesson list, the longest of the three by far, had to be
 * capped behind a "show all" toggle to stay navigable. A tab shows one list at full length instead,
 * so nothing is hidden and each list starts at the top.
 */
private enum class StudentTab(val labelRes: Int) {
    SUBJECTS(R.string.student_subjects_title),
    CLASSES(R.string.student_classes_title),
    PAYMENTS(R.string.student_payments_title),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    onBack: () -> Unit,
    viewModel: StudentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val student = state.student

    var tab by rememberSaveable { mutableStateOf(StudentTab.SUBJECTS) }
    var lessonFilter by rememberSaveable { mutableStateOf(LessonFilter.ALL) }
    var editing by remember { mutableStateOf(false) }
    var addingSubject by remember { mutableStateOf(false) }
    var pricingSubjectId by remember { mutableStateOf<Long?>(null) }
    var expandedSubjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var addingPayment by remember { mutableStateOf(false) }
    var confirmingArchive by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val removeBlockedMsg = stringResource(R.string.student_subject_remove_blocked)
    LaunchedEffect(Unit) {
        viewModel.events.stream.collect { event ->
            when (event) {
                StudentDetailViewModel.Event.REMOVE_BLOCKED_HAS_CLASSES ->
                    android.widget.Toast.makeText(context, removeBlockedMsg, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val paymentDeletedMsg = stringResource(R.string.payment_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    fun deletePaymentWithUndo(payment: Payment) {
        viewModel.deletePayment(payment)
        scope.launch {
            // Show for ~5s with an Undo action; dismiss it ourselves once the window passes.
            val dismiss = launch {
                delay(5000)
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            val result = snackbarHostState.showSnackbar(
                message = paymentDeletedMsg,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Indefinite,
            )
            dismiss.cancel()
            if (result == SnackbarResult.ActionPerformed) viewModel.restorePayment(payment)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(student?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { editing = true }) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.action_edit))
                    }
                    if (student != null) {
                        TextButton(onClick = {
                            // Archiving hides the student from Reports — including any debt they still
                            // carry. Warn before silently dropping a non-zero balance from the totals.
                            if (!student.archived && !state.balance.isZero) confirmingArchive = true
                            else viewModel.setArchived(!student.archived)
                        }) {
                            Text(
                                stringResource(
                                    if (student.archived) R.string.action_unarchive else R.string.action_archive
                                )
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding).constrainedContent(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = StudentDetailKeys.STATS) {
                StatRow(
                    stats = studentStats(state),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            item(key = StudentDetailKeys.CONTACTS) {
                student?.let { ContactBlock(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            }

            // Pinned, so the tab bar stays reachable however long the list under it runs.
            stickyHeader(key = StudentDetailKeys.TABS) {
                TabRow(
                    selectedTabIndex = tab.ordinal,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                ) {
                    StudentTab.entries.forEach { entry ->
                        Tab(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            text = { Text(stringResource(entry.labelRes)) },
                        )
                    }
                }
            }

            when (tab) {
                StudentTab.SUBJECTS -> subjectsTab(
                    state = state,
                    expandedSubjectId = expandedSubjectId,
                    onExpand = { id -> expandedSubjectId = if (expandedSubjectId == id) null else id },
                    onAdd = { addingSubject = true },
                    onReprice = { id -> pricingSubjectId = id },
                    onRemove = viewModel::removeSubject,
                )
                StudentTab.CLASSES -> classesTab(
                    state = state,
                    filter = lessonFilter,
                    onFilter = { lessonFilter = it },
                )
                StudentTab.PAYMENTS -> paymentsTab(
                    state = state,
                    onAdd = { addingPayment = true },
                    onDelete = ::deletePaymentWithUndo,
                )
            }
        }
    }

    if (editing && student != null) {
        StudentEditDialog(
            title = stringResource(R.string.student_edit_title),
            initialName = student.name,
            initialPhone = student.phone.ifBlank { "+375 " },
            initialEmail = student.email,
            initialNote = student.note,
            initialMode = student.defaultMode,
            onConfirm = { name, phone, email, note, mode -> viewModel.editStudent(name, phone, email, note, mode) },
            onDismiss = { editing = false },
        )
    }
    if (addingSubject) {
        // Only subjects not already priced for this student can be added.
        val pricedIds = state.subjects.map { it.subject.id }.toSet()
        AddSubjectPriceDialog(
            addableSubjects = state.allSubjects.filter { it.id !in pricedIds },
            onConfirm = { subjectId, amount -> viewModel.addSubject(subjectId, amount) },
            onDismiss = { addingSubject = false },
        )
    }
    pricingSubjectId?.let { subjectId ->
        val current = state.subjects.firstOrNull { it.subject.id == subjectId }?.price
        TextFieldDialog(
            title = stringResource(R.string.price_edit_title),
            label = stringResource(R.string.price_label),
            initial = current?.toAmountInput() ?: "",
            keyboardType = KeyboardType.Decimal,
            validate = { MoneyField(it, MoneyField.Kind.RATE).isValid },
            onConfirm = { text ->
                MoneyField(text, MoneyField.Kind.RATE).amount?.let { viewModel.setPrice(subjectId, it) }
            },
            onDismiss = { pricingSubjectId = null },
        )
    }
    if (addingPayment) {
        TextFieldDialog(
            title = stringResource(R.string.payment_add_title),
            label = stringResource(R.string.payment_amount_label),
            initial = "",
            keyboardType = KeyboardType.Decimal,
            validate = { MoneyField(it, MoneyField.Kind.PAYMENT).isValid },
            onConfirm = { text ->
                MoneyField(text, MoneyField.Kind.PAYMENT).amount?.let { viewModel.addPayment(it) }
            },
            onDismiss = { addingPayment = false },
        )
    }
    if (confirmingArchive && student != null) {
        com.seblful.repetit.ui.common.ConfirmDialog(
            text = stringResource(R.string.student_archive_balance_warning, balanceDescription(state.balance)),
            confirmLabel = stringResource(R.string.action_archive),
            onConfirm = { viewModel.setArchived(true) },
            onDismiss = { confirmingArchive = false },
        )
    }
}

/**
 * The four numbers the card leads with: what is owed, and what has been taught for it.
 *
 * The money tile flips between debt and credit rather than printing a negative: «Долг −50» is a
 * puzzle, «Переплата 50» is the fact. A settled balance still shows as «Долг 0», because the tile's
 * job in a fixed row is to always occupy the same place with the same meaning.
 */
@Composable
private fun studentStats(state: StudentDetailState): List<Stat> {
    val balance = state.balance
    val totals = state.totals
    return listOf(
        if (balance.isNegative) {
            Stat(
                label = stringResource(R.string.stat_credit),
                value = (Money.ZERO - balance).text(),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Stat(
                label = stringResource(R.string.stat_debt),
                value = balance.text(),
                color = if (balance.isZero) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
            )
        },
        lessonsStat(totals),
        Stat(label = stringResource(R.string.stat_hours), value = formatHours(totals.minutes)),
        Stat(label = stringResource(R.string.stat_earned), value = totals.earned.text()),
    )
}

@Composable
private fun ContactBlock(
    student: com.seblful.repetit.domain.model.Student,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (student.note.isNotBlank()) {
            Text(
                student.note,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (student.phone.isNotBlank()) {
            Text(
                "${stringResource(R.string.student_phone_label)}: ${student.phone}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (student.email.isNotBlank()) {
            Text(
                "${stringResource(R.string.student_email_label)}: ${student.email}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            "${stringResource(R.string.student_mode_label)}: ${classModeLabel(student.defaultMode)}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * The subjects the student takes, each with its rate and — folded away until asked for — how that
 * rate got there. The history is per subject rather than one combined list, because a price only
 * means anything next to the subject it prices.
 */
private fun LazyListScope.subjectsTab(
    state: StudentDetailState,
    expandedSubjectId: Long?,
    onExpand: (Long) -> Unit,
    onAdd: () -> Unit,
    onReprice: (Long) -> Unit,
    onRemove: (Long) -> Unit,
) {
    item(key = StudentDetailKeys.SUBJECTS_ADD) { AddRow(stringResource(R.string.student_add_subject), onAdd) }
    if (state.subjects.isEmpty()) {
        item(key = StudentDetailKeys.SUBJECTS_EMPTY) {
            TabEmptyState(stringResource(R.string.student_no_subjects))
        }
        return
    }
    items(state.subjects, key = { StudentDetailKeys.subject(it.subject.id) }) { sp ->
        val expanded = expandedSubjectId == sp.subject.id
        Column {
            ListItem(
                headlineContent = { Text(sp.subject.name) },
                supportingContent = {
                    Text(
                        stringResource(R.string.price_per_hour, sp.price.text()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { onReprice(sp.subject.id) }) {
                            Icon(Icons.Filled.Edit, stringResource(R.string.price_edit_title))
                        }
                        IconButton(onClick = { onRemove(sp.subject.id) }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                        }
                    }
                },
            )
            // A rate's past is secondary to what it is now, so it stays behind one tap. The toggle is
            // its own row rather than the whole `ListItem`, whose trailing icons already own taps.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onExpand(sp.subject.id) }
                    .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.rate_history_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(R.string.rate_history_expand),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            AnimatedVisibility(expanded) {
                RateHistoryList(
                    sp.history,
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }
    }
}

/**
 * Read-only: lessons are booked and edited from the schedule, not from the student's card.
 *
 * This is the one list in the app that genuinely **mixes** individual lessons with a group's, so it is
 * where the distinction has to be drawn: each row is marked with its kind and names the group it
 * belonged to, and a student who is taught both ways gets chips to see one kind at a time. Without
 * that, two lessons of the same subject an hour apart, billed at different amounts, look like a bug.
 */
private fun LazyListScope.classesTab(
    state: StudentDetailState,
    filter: LessonFilter,
    onFilter: (LessonFilter) -> Unit,
) {
    if (state.hasBothKinds) {
        item(key = StudentDetailKeys.CLASSES_FILTER) {
            LessonFilterChips(
                selected = filter,
                tally = state.lessonKinds,
                onSelect = onFilter,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
    // A filter that isn't on screen doesn't apply. The chips come and go with the data — deleting a
    // student's last group lesson from the schedule takes them away — and a selection left behind
    // would go on hiding their remaining lessons with nothing left to say why, or to undo it.
    val rows = state.classes.filter { !state.hasBothKinds || filter.accepts(it.kind) }
    if (rows.isEmpty()) {
        item(key = StudentDetailKeys.CLASSES_EMPTY) {
            TabEmptyState(stringResource(R.string.schedule_empty))
        }
        return
    }
    items(rows, key = { StudentDetailKeys.klass(it.klass.id) }) { row ->
        ListItem(
            leadingContent = { LessonKindIcon(row.kind) },
            headlineContent = { Text("${row.subjectName} · ${row.klass.start.formatDateTime()}") },
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(classStatusLabel(row.klass.status), color = classStatusColor(row.klass.status))
                    // The group's name for a group lesson, since *which* group is the useful half of
                    // "not individual" — a student in two groups is billed a different rate by each.
                    Text(
                        row.groupName ?: stringResource(R.string.lesson_kind_individual_one),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingContent = {
                if (row.klass.status == ClassStatus.COMPLETED) Text(row.klass.charge.text())
            },
        )
    }
}

private fun LazyListScope.paymentsTab(
    state: StudentDetailState,
    onAdd: () -> Unit,
    onDelete: (Payment) -> Unit,
) {
    item(key = StudentDetailKeys.PAYMENTS_ADD) { AddRow(stringResource(R.string.add_payment), onAdd) }
    if (state.payments.isEmpty()) {
        item(key = StudentDetailKeys.PAYMENTS_EMPTY) {
            TabEmptyState(stringResource(R.string.payments_empty))
        }
        return
    }
    items(state.payments, key = { StudentDetailKeys.payment(it.id) }) { payment ->
        ListItem(
            headlineContent = { Text(payment.amount.text()) },
            supportingContent = { Text(payment.date.formatDate()) },
            trailingContent = {
                IconButton(onClick = { onDelete(payment) }) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                }
            },
        )
    }
}

/** The one way to add to a tab's list, sitting at its top where a section header's action used to. */
@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(start = 8.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun TabEmptyState(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
    )
}

/**
 * Adds one subject to the student together with its price — the two are set in lock-step, so a
 * student's subjects are always exactly their priced (bookable) subjects. Offers only subjects the
 * student isn't already priced for; the save button stays disabled until a subject and a valid price
 * are both chosen.
 */
@Composable
private fun AddSubjectPriceDialog(
    addableSubjects: List<Subject>,
    onConfirm: (subjectId: Long, amount: Money) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var price by remember { mutableStateOf(MoneyField(kind = MoneyField.Kind.RATE)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        // A stray tap beside the dialog must not throw away the price just typed — see `KeepTypedInput`.
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.student_add_subject)) },
        text = {
            Column {
                if (addableSubjects.isEmpty()) {
                    Text(stringResource(R.string.student_all_subjects_added))
                } else {
                    addableSubjects.forEach { subject ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedId = subject.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedId == subject.id, onClick = { selectedId = subject.id })
                            Text(subject.name, Modifier.padding(start = 8.dp))
                        }
                    }
                    OutlinedTextField(
                        value = price.text,
                        onValueChange = { price = price.typed(it) },
                        label = { Text(stringResource(R.string.price_label)) },
                        isError = price.isError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = selectedId
                    val amount = price.amount
                    if (id != null && amount != null) { onConfirm(id, amount); onDismiss() }
                },
                enabled = selectedId != null && price.isValid,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
