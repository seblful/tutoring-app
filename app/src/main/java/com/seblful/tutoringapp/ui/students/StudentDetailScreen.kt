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
import com.seblful.tutoringapp.ui.common.classStatusColor
import com.seblful.tutoringapp.ui.common.classStatusLabel
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
                                sp.price?.format() ?: stringResource(R.string.price_not_set),
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
                items(state.payments, key = { StudentDetailKeys.payment(it.id) }) { payment ->
                    ListItem(
                        headlineContent = { Text(payment.amount.format()) },
                        supportingContent = { Text(payment.date.formatDate()) },
                        trailingContent = {
                            IconButton(onClick = { deletingPayment = payment }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.action_delete))
                            }
                        },
                    )
                }
            }

            item { SectionHeader(title = stringResource(R.string.student_classes_title)) }
            if (state.classes.isEmpty()) {
                item { Text(stringResource(R.string.schedule_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(state.classes, key = { StudentDetailKeys.klass(it.klass.id) }) { row ->
                    ListItem(
                        headlineContent = { Text("${row.subjectName} · ${row.klass.start.formatDateTime()}") },
                        supportingContent = {
                            Text(classStatusLabel(row.klass.status), color = classStatusColor(row.klass.status))
                        },
                        trailingContent = {
                            if (row.klass.status == com.seblful.tutoringapp.domain.model.ClassStatus.COMPLETED) {
                                Text(row.klass.charge.format())
                            }
                        },
                        modifier = Modifier.clickable { onClassClick(row.klass.id) },
                    )
                }
            }
        }
    }

    if (editing && student != null) {
        StudentEditDialog(
            title = stringResource(R.string.student_edit_title),
            initialName = student.name,
            initialNote = student.note,
            onConfirm = { name, note -> viewModel.editStudent(name, note) },
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
