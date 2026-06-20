package com.seblful.tutoringapp.ui.students

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.seblful.tutoringapp.R
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.Payment
import com.seblful.tutoringapp.domain.model.Subject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.seblful.tutoringapp.ui.common.StudentEditDialog
import com.seblful.tutoringapp.ui.common.TextFieldDialog
import com.seblful.tutoringapp.ui.common.balanceColor
import com.seblful.tutoringapp.ui.common.balanceDescription
import com.seblful.tutoringapp.ui.common.classModeLabel
import com.seblful.tutoringapp.ui.common.classStatusColor
import com.seblful.tutoringapp.ui.common.classStatusLabel
import com.seblful.tutoringapp.ui.common.constrainedContent
import com.seblful.tutoringapp.ui.common.text
import com.seblful.tutoringapp.format.formatDate
import com.seblful.tutoringapp.format.formatDateTime
import com.seblful.tutoringapp.format.parseMoneyOrNull
import com.seblful.tutoringapp.format.toAmountInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    onBack: () -> Unit,
    viewModel: StudentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val student = state.student

    var editing by remember { mutableStateOf(false) }
    var addingSubject by remember { mutableStateOf(false) }
    var pricingSubjectId by remember { mutableStateOf<Long?>(null) }
    var addingPayment by remember { mutableStateOf(false) }
    var confirmingArchive by remember { mutableStateOf(false) }
    var showAllPayments by remember { mutableStateOf(false) }
    var showAllClasses by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val removeBlockedMsg = stringResource(R.string.student_subject_remove_blocked)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { BalanceCard(balance = state.balance) }

            if (!student?.note.isNullOrBlank()) {
                item { Text(student!!.note, style = MaterialTheme.typography.bodyMedium) }
            }

            student?.let { s ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (s.phone.isNotBlank()) {
                            Text(
                                "${stringResource(R.string.student_phone_label)}: ${s.phone}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (s.email.isNotBlank()) {
                            Text(
                                "${stringResource(R.string.student_email_label)}: ${s.email}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            "${stringResource(R.string.student_mode_label)}: ${classModeLabel(s.defaultMode)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.student_subjects_title),
                    action = { TextButton(onClick = { addingSubject = true }) { Text(stringResource(R.string.action_add)) } },
                )
            }
            if (state.subjects.isEmpty()) {
                item { Text(stringResource(R.string.student_no_subjects), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(state.subjects, key = { StudentDetailKeys.subject(it.subject.id) }) { sp ->
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
                                IconButton(onClick = { pricingSubjectId = sp.subject.id }) {
                                    Icon(Icons.Filled.Edit, stringResource(R.string.price_edit_title))
                                }
                                IconButton(onClick = { viewModel.removeSubject(sp.subject.id) }) {
                                    Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                                }
                            }
                        },
                    )
                }
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.student_payments_title),
                    action = { TextButton(onClick = { addingPayment = true }) { Text(stringResource(R.string.add_payment)) } },
                )
            }
            if (state.payments.isEmpty()) {
                item { Text(stringResource(R.string.payments_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val visiblePayments = if (showAllPayments) state.payments else state.payments.take(LIST_CAP)
                items(visiblePayments, key = { StudentDetailKeys.payment(it.id) }) { payment ->
                    ListItem(
                        headlineContent = { Text(payment.amount.text()) },
                        supportingContent = { Text(payment.date.formatDate()) },
                        trailingContent = {
                            IconButton(onClick = { deletePaymentWithUndo(payment) }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                            }
                        },
                    )
                }
                if (state.payments.size > LIST_CAP) {
                    item {
                        ShowMoreRow(expanded = showAllPayments, total = state.payments.size) {
                            showAllPayments = !showAllPayments
                        }
                    }
                }
            }

            item { SectionHeader(title = stringResource(R.string.student_classes_title)) }
            if (state.classes.isEmpty()) {
                item { Text(stringResource(R.string.schedule_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val visibleClasses = if (showAllClasses) state.classes else state.classes.take(LIST_CAP)
                items(visibleClasses, key = { StudentDetailKeys.klass(it.klass.id) }) { row ->
                    // Read-only here: classes are edited from the schedule, not the student page.
                    ListItem(
                        headlineContent = { Text("${row.subjectName} · ${row.klass.start.formatDateTime()}") },
                        supportingContent = {
                            Text(classStatusLabel(row.klass.status), color = classStatusColor(row.klass.status))
                        },
                        trailingContent = {
                            if (row.klass.status == com.seblful.tutoringapp.domain.model.ClassStatus.COMPLETED) {
                                Text(row.klass.charge.text())
                            }
                        },
                    )
                }
                if (state.classes.size > LIST_CAP) {
                    item {
                        ShowMoreRow(expanded = showAllClasses, total = state.classes.size) {
                            showAllClasses = !showAllClasses
                        }
                    }
                }
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
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            validate = { parseMoneyOrNull(it) != null }, // a price of 0 (free) is allowed
            onConfirm = { text -> parseMoneyOrNull(text)?.let { viewModel.setPrice(subjectId, it) } },
            onDismiss = { pricingSubjectId = null },
        )
    }
    if (addingPayment) {
        TextFieldDialog(
            title = stringResource(R.string.payment_add_title),
            label = stringResource(R.string.payment_amount_label),
            initial = "",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            // A payment must be a valid, non-zero amount, matching addPayment's own guard.
            validate = { parseMoneyOrNull(it)?.let { m -> !m.isZero } == true },
            onConfirm = { text -> parseMoneyOrNull(text)?.let { viewModel.addPayment(it) } },
            onDismiss = { addingPayment = false },
        )
    }
    if (confirmingArchive && student != null) {
        com.seblful.tutoringapp.ui.common.ConfirmDialog(
            text = stringResource(R.string.student_archive_balance_warning, balanceDescription(state.balance)),
            confirmLabel = stringResource(R.string.action_archive),
            onConfirm = { viewModel.setArchived(true) },
            onDismiss = { confirmingArchive = false },
        )
    }
}

/** Max rows shown before a list collapses behind a "show all" toggle. */
private const val LIST_CAP = 10

/** A tappable row that expands or collapses a capped list. */
@Composable
private fun ShowMoreRow(expanded: Boolean, total: Int, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (expanded) stringResource(R.string.list_show_less)
            else stringResource(R.string.list_show_all, total)
        )
    }
}

@Composable
private fun BalanceCard(balance: com.seblful.tutoringapp.domain.model.Money) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.student_balance_label), style = MaterialTheme.typography.labelMedium)
            Text(
                balanceDescription(balance),
                style = MaterialTheme.typography.headlineSmall,
                color = balanceColor(balance),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        action?.invoke()
    }
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
    var priceText by remember { mutableStateOf("") }
    val amount = parseMoneyOrNull(priceText)
    AlertDialog(
        onDismissRequest = onDismiss,
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
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text(stringResource(R.string.price_label)) },
                        isError = priceText.isNotBlank() && amount == null,
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
                    if (id != null && amount != null) { onConfirm(id, amount); onDismiss() }
                },
                enabled = selectedId != null && amount != null,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
