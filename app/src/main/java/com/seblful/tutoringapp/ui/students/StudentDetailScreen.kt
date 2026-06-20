package com.seblful.tutoringapp.ui.students

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.seblful.tutoringapp.R
import com.seblful.tutoringapp.domain.model.Payment
import com.seblful.tutoringapp.ui.common.StudentEditDialog
import com.seblful.tutoringapp.ui.common.TextFieldDialog
import com.seblful.tutoringapp.ui.common.balanceColor
import com.seblful.tutoringapp.ui.common.balanceDescription
import com.seblful.tutoringapp.ui.common.classModeLabel
import com.seblful.tutoringapp.ui.common.classStatusColor
import com.seblful.tutoringapp.ui.common.classStatusLabel
import com.seblful.tutoringapp.ui.common.text
import com.seblful.tutoringapp.format.formatDate
import com.seblful.tutoringapp.format.formatDateTime
import com.seblful.tutoringapp.format.parseMoneyOrNull
import com.seblful.tutoringapp.format.toAmountInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    onBack: () -> Unit,
    onClassClick: (Long) -> Unit,
    viewModel: StudentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val student = state.student

    var editing by remember { mutableStateOf(false) }
    var pickingSubjects by remember { mutableStateOf(false) }
    var pricingSubjectId by remember { mutableStateOf<Long?>(null) }
    var addingPayment by remember { mutableStateOf(false) }
    var deletingPayment by remember { mutableStateOf<Payment?>(null) }
    var showAllPayments by remember { mutableStateOf(false) }
    var showAllClasses by remember { mutableStateOf(false) }

    Scaffold(
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
                        TextButton(onClick = { viewModel.setArchived(!student.archived) }) {
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
            modifier = Modifier.fillMaxWidth().padding(padding),
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
                    action = { TextButton(onClick = { pickingSubjects = true }) { Text(stringResource(R.string.action_edit)) } },
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
                                sp.price?.let { stringResource(R.string.price_per_hour, it.text()) }
                                    ?: stringResource(R.string.price_not_set),
                                color = if (sp.price == null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { pricingSubjectId = sp.subject.id }) {
                                Icon(Icons.Filled.Edit, stringResource(R.string.price_edit_title))
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
                            IconButton(onClick = { deletingPayment = payment }) {
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
                    // A class in any status opens the editor; saving a completed class re-bills it so
                    // its snapshotted charge tracks the edit (see ClassRules.rebill).
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
                        modifier = Modifier.clickable { onClassClick(row.klass.id) },
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
    if (pickingSubjects) {
        SubjectPickerDialog(
            allSubjects = state.allSubjects,
            selected = state.subjects.map { it.subject.id }.toSet(),
            onConfirm = { viewModel.setSubjects(it) },
            onDismiss = { pickingSubjects = false },
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
    deletingPayment?.let { payment ->
        com.seblful.tutoringapp.ui.common.ConfirmDialog(
            text = stringResource(R.string.action_delete) + "?",
            onConfirm = { viewModel.deletePayment(payment) },
            onDismiss = { deletingPayment = null },
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

@Composable
private fun SubjectPickerDialog(
    allSubjects: List<com.seblful.tutoringapp.domain.model.Subject>,
    selected: Set<Long>,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val checked = remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_subjects_title)) },
        text = {
            Column {
                if (allSubjects.isEmpty()) {
                    Text(stringResource(R.string.subjects_empty))
                }
                allSubjects.forEach { subject ->
                    val isChecked = subject.id in checked.value
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = isChecked,
                                onValueChange = { on ->
                                    checked.value = if (on) checked.value + subject.id else checked.value - subject.id
                                },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = isChecked, onCheckedChange = null)
                        Text(subject.name, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(checked.value); onDismiss() }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
