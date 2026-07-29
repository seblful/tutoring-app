package com.seblful.klas.ui.students

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.domain.LessonFilter
import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.ui.common.DatePickerModal
import com.seblful.klas.ui.common.LessonFilterChips
import com.seblful.klas.ui.common.LessonKindIcon
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.PickerField
import com.seblful.klas.ui.common.RateHistoryList
import com.seblful.klas.ui.common.Stat
import com.seblful.klas.ui.common.StatRow
import com.seblful.klas.ui.common.StudentEditDialog
import com.seblful.klas.ui.common.balanceDescription
import com.seblful.klas.ui.common.classModeLabel
import com.seblful.klas.ui.common.classStatusColor
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.common.constrainedContent
import com.seblful.klas.ui.common.lessonsStat
import com.seblful.klas.ui.common.dateTimeText
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.text
import com.seblful.klas.format.formatDate
import com.seblful.klas.format.formatHours
import com.seblful.klas.format.MoneyField
import com.seblful.klas.format.toAmountInput
import com.seblful.klas.format.toLocalDate
import java.time.LocalDate

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
    var addingPayment by remember { mutableStateOf(false) }
    var editingPayment by remember { mutableStateOf<Payment?>(null) }
    var confirmingArchive by remember { mutableStateOf(false) }

    val messages = rememberMessages()
    val context = LocalContext.current
    val noAppMsg = stringResource(R.string.student_contact_failed)

    val removeBlockedMsg = stringResource(R.string.student_subject_remove_blocked)
    val showLabel = stringResource(R.string.action_show)
    val writeFailedMsg = stringResource(R.string.action_failed)
    // Held here rather than read inside the collector: a plural needs the count, and the count is
    // whatever the event carries — so the message is built from the state the dialog just confirmed.
    val cancelledCount = state.totals.upcoming
    val cancelledMsg = pluralStringResource(
        R.plurals.student_archive_lessons_cancelled,
        cancelledCount.coerceAtLeast(1),
        cancelledCount,
    )
    LaunchedEffect(Unit) {
        viewModel.events.stream.collect { event ->
            when (event) {
                // The refusal names lessons the tutor now has to go and look at — and they are one
                // tab away, so the message carries the way there. This is the thing a toast could
                // never do: state a problem *and* offer the next step.
                StudentDetailViewModel.Event.REMOVE_BLOCKED_HAS_CLASSES ->
                    messages.show(removeBlockedMsg, showLabel) { tab = StudentTab.CLASSES }

                StudentDetailViewModel.Event.ARCHIVE_CANCELLED_LESSONS -> messages.show(cancelledMsg)
                StudentDetailViewModel.Event.WRITE_FAILED -> messages.show(writeFailedMsg)
            }
        }
    }

    val paymentDeletedMsg = stringResource(R.string.payment_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    fun deletePaymentWithUndo(payment: Payment) {
        viewModel.deletePayment(payment)
        messages.show(paymentDeletedMsg, undoLabel) { viewModel.restorePayment(payment) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(messages.hostState) },
        topBar = {
            TopAppBar(
                title = { Text(student?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { editing = true }) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.action_edit))
                    }
                    // Archiving is behind the overflow now. It used to be a bare text button in the
                    // bar — «Имя [✎] [В архив]» — which put the most consequential thing on the
                    // screen beside the pencil, at the width of a word, next to a name that has to
                    // ellipsize to make room for it.
                    if (student != null) {
                        OverflowMenu(
                            archived = student.archived,
                            // Whether archiving is worth asking about, and what the question would have
                            // to say, are both `state.archiveIntent`'s — see [ArchiveIntent].
                            onArchive = {
                                when (state.archiveIntent) {
                                    is ArchiveIntent.Confirm -> confirmingArchive = true
                                    ArchiveIntent.Archive -> viewModel.setArchived(true)
                                    ArchiveIntent.Restore -> viewModel.setArchived(false)
                                    ArchiveIntent.Nothing -> Unit
                                }
                            },
                        )
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
                student?.let {
                    ContactBlock(
                        student = it,
                        onCall = { phone -> launchContact(context, dialIntent(phone), noAppMsg, messages) },
                        onEmail = { email -> launchContact(context, mailIntent(email), noAppMsg, messages) },
                        onTelegram = { phone ->
                            launchContact(context, telegramIntent(phone), noAppMsg, messages)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // Pinned, so the tab bar stays reachable however long the list under it runs.
            stickyHeader(key = StudentDetailKeys.TABS) {
                SecondaryTabRow(
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
                    onEdit = { editingPayment = it },
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
        state.subjects.firstOrNull { it.subject.id == subjectId }?.let { sp ->
            RepriceDialog(
                subjectPrice = sp,
                onConfirm = { viewModel.setPrice(subjectId, it) },
                onDismiss = { pricingSubjectId = null },
            )
        }
    }
    if (addingPayment) {
        PaymentDialog(
            title = stringResource(R.string.payment_add_title),
            initialAmount = null,
            initialDate = viewModel.paymentDay(),
            onConfirm = { amount, date -> viewModel.addPayment(amount, date) },
            onDismiss = { addingPayment = false },
        )
    }
    editingPayment?.let { payment ->
        PaymentDialog(
            title = stringResource(R.string.payment_edit_title),
            initialAmount = payment.amount,
            initialDate = payment.date.toLocalDate(),
            onConfirm = { amount, date -> viewModel.editPayment(payment, amount, date) },
            onDismiss = { editingPayment = null },
        )
    }
    val confirm = state.archiveIntent as? ArchiveIntent.Confirm
    if (confirmingArchive && confirm != null) {
        // One string per part the intent named, so the question says exactly what archiving costs here.
        // Which parts apply is decided in [ArchiveIntent]; only the wording is this screen's.
        val parts = buildList {
            confirm.openBalance?.let {
                add(stringResource(R.string.student_archive_balance_part, balanceDescription(it)))
            }
            if (confirm.upcomingLessons > 0) {
                add(
                    pluralStringResource(
                        R.plurals.student_archive_lessons_part,
                        confirm.upcomingLessons,
                        confirm.upcomingLessons,
                    ),
                )
            }
            add(stringResource(R.string.student_archive_question))
        }
        com.seblful.klas.ui.common.ConfirmDialog(
            text = parts.joinToString(" "),
            confirmLabel = stringResource(R.string.action_archive),
            onConfirm = { viewModel.setArchived(true) },
            onDismiss = { confirmingArchive = false },
        )
    }
}

/**
 * The three numbers the card leads with: what is owed, what has been taught, what it earned.
 *
 * **Three, not four.** On a 360dp phone four tiles leave about 80dp each, and the value text is
 * allowed to shrink to 11sp to fit — at which point «1 250,50 BYN» under «Заработано» is present but
 * not readable, which is the worst of both. Hours were the tile to lose: they are the one number
 * derivable from the other two at a glance, and they now ride along as the lesson tile's hint rather
 * than costing a quarter of the row.
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
        // Hours as the hint, in place of the tile they used to have. `lessonsStat` prefers «впереди N»
        // when there are lessons still to come — that is something to act on and outranks a total.
        lessonsStat(totals).let { stat ->
            stat.copy(hint = stat.hint ?: formatHours(totals.minutes))
        },
        Stat(label = stringResource(R.string.stat_earned), value = totals.earned.text()),
    )
}

/**
 * Who the student is and how to reach them.
 *
 * The phone and the email are **actions**, not text. The tutor's next move after "who owes me money"
 * is "message them", and these used to be unselectable labels — so the number had to be read off the
 * screen and typed into the dialler by hand. Telegram is offered beside the phone because for a
 * Belarusian tutor that, not a voice call, is usually the real job.
 */
@Composable
private fun ContactBlock(
    student: com.seblful.klas.domain.model.Student,
    onCall: (String) -> Unit,
    onEmail: (String) -> Unit,
    onTelegram: (String) -> Unit,
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
            ContactRow(
                icon = Icons.Filled.Phone,
                text = student.phone,
                onClick = { onCall(student.phone) },
                trailing = {
                    IconButton(onClick = { onTelegram(student.phone) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            stringResource(R.string.student_write_telegram),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }
        if (student.email.isNotBlank()) {
            ContactRow(
                icon = Icons.Filled.MailOutline,
                text = student.email,
                onClick = { onEmail(student.email) },
            )
        }
        Text(
            "${stringResource(R.string.student_mode_label)}: ${classModeLabel(student.defaultMode)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** One tappable way of reaching the student, at a full-height target. */
@Composable
private fun ContactRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .heightIn(min = MinTouchTarget)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        trailing?.invoke()
    }
}

/** Archiving, one step back from the pencil it used to sit beside. */
@Composable
private fun OverflowMenu(archived: Boolean, onArchive: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (archived) R.string.action_unarchive else R.string.action_archive
                        )
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Inventory2, contentDescription = null) },
                onClick = { expanded = false; onArchive() },
            )
        }
    }
}

/**
 * The subjects the student takes, each with its rate.
 *
 * **The rate's history is not here any more.** Every subject used to carry a «История цены ⌄» strip
 * below its row — a 28dp toggle, under the 48dp minimum, on a list where the common case is a rate
 * that has never moved and the strip says so at the cost of a line per subject. Two subjects turned a
 * two-row list into six rows of furniture. It now lives inside the reprice dialog, which is both the
 * one place the question "what did this cost before?" actually comes up and the place where the
 * answer changes something.
 */
private fun LazyListScope.subjectsTab(
    state: StudentDetailState,
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
            modifier = Modifier.animateItem(),
        )
    }
}

/**
 * Changing one subject's rate, with what it has been before.
 *
 * The history belongs *here* rather than on the list: it is the context for the decision being made
 * — «было 25, стало 30» is the thing worth knowing while typing a new number — and on the list it was
 * a permanent strip answering a question nobody had yet asked.
 */
@Composable
private fun RepriceDialog(
    subjectPrice: SubjectPrice,
    onConfirm: (Money) -> Unit,
    onDismiss: () -> Unit,
) {
    var field by remember {
        mutableStateOf(MoneyField(subjectPrice.price.toAmountInput(), MoneyField.Kind.RATE))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        // As every dialog the tutor types into: a stray tap outside must not discard the amount.
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(subjectPrice.subject.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = field.text,
                    onValueChange = { field = field.typed(it) },
                    label = { Text(stringResource(R.string.price_label)) },
                    isError = field.isError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider()
                Text(
                    stringResource(R.string.rate_history_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RateHistoryList(subjectPrice.history)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { field.amount?.let(onConfirm); onDismiss() },
                enabled = field.isValid,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Recording or correcting one payment: how much, and **when**.
 *
 * The date is the addition. A payment used to be stamped with the moment it was typed, so a tutor
 * writing up the week on Sunday recorded five Sunday payments — the amounts were right and the
 * history was fiction. Defaulted to today, which is right most of the time.
 */
@Composable
private fun PaymentDialog(
    title: String,
    initialAmount: Money?,
    initialDate: LocalDate,
    onConfirm: (Money, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var field by remember {
        mutableStateOf(
            MoneyField(initialAmount?.toAmountInput().orEmpty(), MoneyField.Kind.PAYMENT),
        )
    }
    var date by remember { mutableStateOf(initialDate) }
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = field.text,
                    onValueChange = { field = field.typed(it) },
                    label = { Text(stringResource(R.string.payment_amount_label)) },
                    isError = field.isError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                PickerField(
                    value = date.formatDate(),
                    label = stringResource(R.string.payment_date_label),
                    onClick = { picking = true },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // The day, not an instant: which hour a payment lands at is `PaymentDate`'s,
                    // and it needs the injected clock this composable has no way to hold.
                    field.amount?.let { onConfirm(it, date) }
                    onDismiss()
                },
                enabled = field.isValid,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (picking) {
        DatePickerModal(
            initial = date,
            onConfirm = { date = it; picking = false },
            onDismiss = { picking = false },
        )
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
    // Through the domain filter, which had no production caller while this spelled the rule itself.
    val rows = if (state.hasBothKinds) filter.accepted(state.classes) { it.kind } else state.classes
    if (rows.isEmpty()) {
        item(key = StudentDetailKeys.CLASSES_EMPTY) {
            TabEmptyState(stringResource(R.string.schedule_empty))
        }
        return
    }
    items(rows, key = { StudentDetailKeys.klass(it.klass.id) }) { row ->
        ListItem(
            leadingContent = { LessonKindIcon(row.kind) },
            headlineContent = { Text("${row.subjectName} · ${row.klass.start.dateTimeText()}") },
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

/**
 * The money the student has handed over. Each row can be **corrected**, not only deleted: a mistyped
 * amount used to mean removing the payment and entering it again.
 */
private fun LazyListScope.paymentsTab(
    state: StudentDetailState,
    onAdd: () -> Unit,
    onEdit: (Payment) -> Unit,
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
            // Tapping the row corrects it; the bin still removes it. Editing is the commoner of the
            // two — a wrong amount is a typo, not a payment that did not happen.
            modifier = Modifier.clickable { onEdit(payment) }.animateItem(),
        )
    }
}

/**
 * The one way to add to a tab's list, sitting at its top where a section header's action used to.
 *
 * Held to the 48dp minimum: a bare `TextButton` measures to its text, which on these rows came out
 * around 36dp — under the floor, on the only control that adds anything to the tab.
 */
@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(start = 8.dp).heightIn(min = MinTouchTarget),
    ) {
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
 * Reaching the student through whatever app handles it.
 *
 * Every one of these can fail on a real device — a tablet with no dialler, a phone with no mail
 * client, Telegram not installed — and an unhandled `ActivityNotFoundException` from a tap on a
 * phone number would be a crash in the middle of the most ordinary thing on the screen. It says so
 * instead, in the same snackbar everything else on this card speaks through.
 */
private fun launchContact(
    context: android.content.Context,
    intent: Intent,
    failureMessage: String,
    messages: com.seblful.klas.ui.common.Messages,
) {
    runCatching { context.startActivity(intent) }.onFailure { messages.show(failureMessage) }
}

private fun dialIntent(phone: String): Intent =
    Intent(Intent.ACTION_DIAL, "tel:${phone.filterNot { it.isWhitespace() }}".toUri())

private fun mailIntent(email: String): Intent =
    Intent(Intent.ACTION_SENDTO, "mailto:$email".toUri())

/**
 * Telegram by phone number, through the public `t.me` deep link.
 *
 * `https://t.me/+<number>` opens the app when it is installed and the browser when it is not, which
 * is the right behaviour either way — the alternative, an app-specific `tg://` scheme, fails hard on
 * a phone without Telegram. The number is stripped to digits and a leading `+`: `t.me` takes it in
 * international form, and the tutor types it however they like (the form pre-fills «+375 »).
 */
private fun telegramIntent(phone: String): Intent {
    val digits = phone.filter { it.isDigit() }
    return Intent(Intent.ACTION_VIEW, "https://t.me/+$digits".toUri())
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
