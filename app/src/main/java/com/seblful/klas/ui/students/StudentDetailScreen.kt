package com.seblful.klas.ui.students

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Payments
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
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.ui.common.AddRow
import com.seblful.klas.ui.common.DatePickerModal
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.HeroCard
import com.seblful.klas.ui.common.KeepTypedInput
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.LessonFilterChips
import com.seblful.klas.ui.common.LessonKindIcon
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.LoadingDetailScreen
import com.seblful.klas.ui.common.MinTouchTarget
import com.seblful.klas.ui.common.PickerField
import com.seblful.klas.ui.common.RateHistoryList
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.Stat
import com.seblful.klas.ui.common.StatRow
import com.seblful.klas.ui.common.StickyTabRow
import com.seblful.klas.ui.common.StudentEditDialog
import com.seblful.klas.ui.common.TitleSize
import com.seblful.klas.ui.common.balanceDescription
import com.seblful.klas.ui.common.classModeLabel
import com.seblful.klas.ui.common.classStatusColor
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.common.launchExternally
import com.seblful.klas.ui.common.lessonsStat
import com.seblful.klas.ui.common.dateTimeText
import com.seblful.klas.ui.common.moneyErrorText
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.supportingTextOf
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

/**
 * The card, once there is a student to draw one for.
 *
 * The wait is a screen of its own rather than a card full of zeroes: `StudentDetailState()` — the initial
 * value this used to open on — is a student with no name, no debt and no lessons, so the card stated
 * «Долг 0» about somebody before it had read them. See [Loadable].
 */
@Composable
fun StudentDetailScreen(
    onBack: () -> Unit,
    viewModel: StudentDetailViewModel = hiltViewModel(),
) {
    when (val screenState = viewModel.state.collectAsState().value) {
        Loadable.Loading -> LoadingDetailScreen(onBack)
        is Loadable.Ready -> StudentCard(screenState.value, viewModel, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentCard(
    state: StudentDetailState,
    viewModel: StudentDetailViewModel,
    onBack: () -> Unit,
) {
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

    // A student's name is what this screen is *about*, so it gets the large title Material designed for
    // exactly that — collapsing into an ordinary bar as the list scrolls, instead of being a 16sp string
    // ellipsised between a back arrow and two actions. [TitleSize.Large] is that, and the scroll
    // behaviour it needs, said once.
    KlasScreen(
        title = student?.name,
        onBack = onBack,
        titleSize = TitleSize.Large,
        messages = messages,
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
                    student = student,
                    // The same three launches the contact block makes, through the same guard: a tablet
                    // with no dialler must not crash the card.
                    onCall = { phone -> launchExternally(context, dialIntent(phone), noAppMsg, messages) },
                    onTelegram = { phone ->
                        launchExternally(context, telegramIntent(phone), noAppMsg, messages)
                    },
                    onEmail = { email -> launchExternally(context, mailIntent(email), noAppMsg, messages) },
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
    ) { modifier ->
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = Spacing.sectionGap),
        ) {
            // Identity, then the numbers, then the tabs — the same three in the same order as the
            // group's card. This one used to lead with the stat row and put the contacts second, so the
            // two cards disagreed about their own structure.
            item(key = StudentDetailKeys.CONTACTS) {
                student?.let {
                    ContactBlock(
                        student = it,
                        onCall = { phone -> launchExternally(context, dialIntent(phone), noAppMsg, messages) },
                        onEmail = { email -> launchExternally(context, mailIntent(email), noAppMsg, messages) },
                        onTelegram = { phone ->
                            launchExternally(context, telegramIntent(phone), noAppMsg, messages)
                        },
                        modifier = Modifier.padding(
                            horizontal = Spacing.gutter,
                            vertical = Spacing.tight,
                        ),
                    )
                }
            }

            // The one number the card exists to answer, ahead of the two that qualify it.
            item(key = StudentDetailKeys.BALANCE) {
                BalanceHero(
                    state = state,
                    modifier = Modifier.padding(
                        horizontal = Spacing.gutter,
                        vertical = Spacing.inline,
                    ),
                )
            }

            item(key = StudentDetailKeys.STATS) {
                StatRow(
                    stats = studentStats(state),
                    modifier = Modifier.padding(
                        horizontal = Spacing.gutter,
                        vertical = Spacing.tight,
                    ),
                )
            }

            // Pinned, so the tab bar stays reachable however long the list under it runs.
            stickyHeader(key = StudentDetailKeys.TABS) {
                StickyTabRow(selectedTabIndex = tab.ordinal) {
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
 * The two numbers that **qualify** the balance above them: what has been taught, and what it earned.
 *
 * **Two, because the money left.** It was the first of three tiles, and it is the thing the card exists
 * to answer — so it is a [HeroCard] now (see [BalanceHero]) and the row keeps what supports it. Two
 * tiles also gives each of them half a phone instead of a third: on a 360dp screen four tiles left about
 * 80dp each, at which point the value text shrank to 11sp and «1 250,50 BYN» was present but not
 * readable, which is the worst of both.
 *
 * Hours ride along as the lesson tile's **hint** rather than taking a tile: they are the one number
 * derivable from the other two at a glance. `lessonsStat` prefers «впереди N» where there are lessons
 * still to come — that is something to act on, and it outranks a total.
 */
@Composable
private fun studentStats(state: StudentDetailState): List<Stat> {
    val totals = state.totals
    return listOf(
        // Hours as the hint, in place of the tile they used to have. `lessonsStat` prefers «впереди N»
        // when there are lessons still to come — that is something to act on and outranks a total.
        lessonsStat(totals).let { stat ->
            stat.copy(hint = stat.hint ?: formatHours(totals.minutes))
        },
        Stat(label = stringResource(R.string.stat_earned), value = totals.earned.text()),
    )
}

/**
 * What the student owes, as the card's **hero**.
 *
 * It was the first of three stat tiles: red text in a grey row, at whatever size «1 250,50 BYN» had to
 * shrink to in a third of a phone. That is the number the card exists to answer — the tutor opens a
 * student to find out where they stand — and the report's total, its exact counterpart, has had a filled
 * accent card to itself all along. Two uses is a system; one was an accident. See [HeroCard].
 *
 * **Tonal, not the primary fill.** «Долг 240» in the app's happiest colour would be congratulating the
 * tutor on money they have not been paid; the report's income earns that colour and this does not.
 *
 * Debt and credit swap label rather than printing a negative: «Долг −50» is a puzzle, «Переплата 50» is
 * the fact. A settled balance still reads «Долг 0» — the card always answers the same question in the
 * same place, and «0» is an answer.
 *
 * The `when` is over [Balance] and exhaustive, so that last rule is now *stated*: [Balance.Settled] is
 * listed beside [Balance.Owed] under the debt label because that is the decision, where before it was a
 * two-armed `isNegative` check that produced it by omission. The sign flip is [Balance]'s and is gone
 * from here.
 */
@Composable
private fun BalanceHero(state: StudentDetailState, modifier: Modifier = Modifier) {
    val balance = state.balance
    HeroCard(
        label = stringResource(
            when (balance) {
                is Balance.Credit -> R.string.stat_credit
                Balance.Settled, is Balance.Owed -> R.string.stat_debt
            }
        ),
        value = balance.amount.text(),
        modifier = modifier,
        tonal = true,
    ) {
        // The same sentence the students list uses for the same number, so a balance reads identically
        // wherever it is met.
        Text(balanceDescription(balance), style = MaterialTheme.typography.bodySmall)
    }
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
    student: Student,
    onCall: (String) -> Unit,
    onEmail: (String) -> Unit,
    onTelegram: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        if (student.note.isNotBlank()) {
            Text(
                student.note,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Spacing.tight),
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
            modifier = Modifier.padding(top = Spacing.tight),
        )
    }
}

/** One tappable way of reaching the student, at a full-height target. */
@Composable
private fun ContactRow(
    icon: ImageVector,
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
            .padding(horizontal = Spacing.tight),
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
            modifier = Modifier.weight(1f).padding(start = Spacing.leading),
        )
        trailing?.invoke()
    }
}

/** Archiving, one step back from the pencil it used to sit beside. */
@Composable
private fun OverflowMenu(
    student: Student,
    onCall: (String) -> Unit,
    onTelegram: (String) -> Unit,
    onEmail: (String) -> Unit,
    onArchive: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // **Reaching the student, from any tab.** The contact block is the top of the card, which is
            // the right place for it — it is identity, not content — but it is also the part that scrolls
            // away first, and a tutor two hundred rows into «Занятия» who wants to ring about the next
            // one had to scroll all the way back for a number. The bar does not: it is the one thing on
            // this screen that stays.
            //
            // Not a duplicate control so much as the same act reached the way an *action* is reached. The
            // block still shows the phone, the email and the note — those are facts about the student, and
            // reading one is why it stays visible; these are the three things you can *do* with them.
            if (student.phone.isNotBlank()) {
                ContactMenuItem(
                    label = stringResource(R.string.student_call),
                    icon = Icons.Filled.Phone,
                    onClick = { expanded = false; onCall(student.phone) },
                )
                ContactMenuItem(
                    label = stringResource(R.string.student_write_telegram),
                    icon = Icons.AutoMirrored.Filled.Send,
                    onClick = { expanded = false; onTelegram(student.phone) },
                )
            }
            if (student.email.isNotBlank()) {
                ContactMenuItem(
                    label = stringResource(R.string.student_write_email),
                    icon = Icons.Filled.MailOutline,
                    onClick = { expanded = false; onEmail(student.email) },
                )
            }
            if (student.phone.isNotBlank() || student.email.isNotBlank()) {
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (student.archived) R.string.action_unarchive else R.string.action_archive
                        )
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Inventory2, contentDescription = null) },
                onClick = { expanded = false; onArchive() },
            )
        }
    }
}

/** One way of reaching the student, in the overflow. Filled glyph: it is an action, not art. */
@Composable
private fun ContactMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
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
    if (state.subjects.isEmpty()) {
        item(key = StudentDetailKeys.SUBJECTS_EMPTY) {
            EmptyState(
                title = stringResource(R.string.student_no_subjects),
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                actionLabel = stringResource(R.string.student_add_subject),
                onAction = onAdd,
                compact = true,
            )
        }
        return
    }
    item(key = StudentDetailKeys.SUBJECTS_ADD) { AddRow(stringResource(R.string.student_add_subject), onAdd) }
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
        properties = KeepTypedInput,
        title = { Text(subjectPrice.subject.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
                OutlinedTextField(
                    value = field.text,
                    onValueChange = { field = field.typed(it) },
                    label = { Text(stringResource(R.string.price_label)) },
                    isError = field.isError,
                    // The red outline used to be the whole message. `MoneyField` knows which rule
                    // failed; this is it saying so.
                    supportingText = supportingTextOf(moneyErrorText(field)),
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
        properties = KeepTypedInput,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.itemGap)) {
                OutlinedTextField(
                    value = field.text,
                    onValueChange = { field = field.typed(it) },
                    label = { Text(stringResource(R.string.payment_amount_label)) },
                    isError = field.isError,
                    supportingText = supportingTextOf(moneyErrorText(field)),
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
                modifier = Modifier.padding(vertical = Spacing.inline),
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
            // No action: booking belongs to the schedule, which is the one place that knows what else
            // the hour holds. See the card's KDoc.
            EmptyState(
                title = stringResource(R.string.schedule_empty),
                icon = Icons.Outlined.EventBusy,
                compact = true,
            )
        }
        return
    }
    items(rows, key = { StudentDetailKeys.klass(it.klass.id) }) { row ->
        ListItem(
            leadingContent = { LessonKindIcon(row.kind) },
            headlineContent = { Text("${row.subjectName} · ${row.klass.start.dateTimeText()}") },
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.inline)) {
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
                if (row.klass.status == LessonStatus.COMPLETED) Text(row.klass.charge.text())
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
    if (state.payments.isEmpty()) {
        item(key = StudentDetailKeys.PAYMENTS_EMPTY) {
            EmptyState(
                title = stringResource(R.string.payments_empty),
                icon = Icons.Outlined.Payments,
                actionLabel = stringResource(R.string.add_payment),
                onAction = onAdd,
                compact = true,
            )
        }
        return
    }
    item(key = StudentDetailKeys.PAYMENTS_ADD) { AddRow(stringResource(R.string.add_payment), onAdd) }
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
        // A stray tap beside the dialog must not throw away the price just typed.
        properties = KeepTypedInput,
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
                                .padding(vertical = Spacing.tight),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedId == subject.id, onClick = { selectedId = subject.id })
                            Text(subject.name, Modifier.padding(start = Spacing.inline))
                        }
                    }
                    OutlinedTextField(
                        value = price.text,
                        onValueChange = { price = price.typed(it) },
                        label = { Text(stringResource(R.string.price_label)) },
                        isError = price.isError,
                        supportingText = supportingTextOf(moneyErrorText(price)),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.inline),
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
