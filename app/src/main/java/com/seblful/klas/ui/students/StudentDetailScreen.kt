package com.seblful.klas.ui.students

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.LessonFilter
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.people.Phone
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatDate
import com.seblful.klas.format.formatHours
import com.seblful.klas.format.toLocalDate
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.queries.students.ArchiveIntent
import com.seblful.klas.queries.students.ClassRow
import com.seblful.klas.queries.students.StudentDetails
import com.seblful.klas.ui.common.AddRow
import com.seblful.klas.ui.common.BadgeLine
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.ExternalTarget
import com.seblful.klas.ui.common.HeroCard
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.LessonKindIcon
import com.seblful.klas.ui.common.LoadingDetailScreen
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.Stat
import com.seblful.klas.ui.common.StatusBadge
import com.seblful.klas.ui.common.TitleSize
import com.seblful.klas.ui.common.TrailingAmount
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.classStatusColor
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.common.dateTimeText
import com.seblful.klas.ui.common.detailCard
import com.seblful.klas.ui.common.earnedStat
import com.seblful.klas.ui.common.insetRowPress
import com.seblful.klas.ui.common.lessonsStat
import com.seblful.klas.ui.common.plusInset
import com.seblful.klas.ui.common.rememberExternalApps
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.rememberRejectFeedback
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.homework.CardHomeworkDialogs
import com.seblful.klas.ui.homework.homeworkItems
import com.seblful.klas.ui.homework.homeworkUndo
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

private enum class StudentTab(val labelRes: Int, val icon: ImageVector) {
    CLASSES(R.string.student_classes_title, KlasIcons.Outlined.CalendarMonth),
    PAYMENTS(R.string.student_payments_title, KlasIcons.Outlined.Payments),

    /**
     * Their homework and their groups', current and done — the one place an archived student's is still
     * listed, and where homework is set for this student without picking them first.
     */
    HOMEWORK(R.string.student_homework_title, KlasIcons.Outlined.Assignment),
    SUBJECTS(R.string.student_subjects_title, KlasIcons.Outlined.MenuBook),
}

/** The task carried from a lesson or an unfinished setup form. */
enum class StudentTask { HOMEWORK, PAYMENT, SUBJECT }

private data class StudentNavigation(
    val back: () -> Unit,
    /** Whether the bar draws [back] as an arrow; not beside the list the card was opened from. */
    val backArrow: Boolean,
    val lesson: (Long) -> Unit,
    val book: () -> Unit,
)

/**
 * The card, once there is a student to draw one for.
 *
 * @param backArrow false beside the People list the card was opened from: the list is the way to another.
 */
@Composable
fun StudentDetailScreen(
    onBack: () -> Unit,

    onLessonClick: (Long) -> Unit,
    viewModel: StudentDetailViewModel = assistedMetroViewModel(),
    onBookLesson: () -> Unit = {},
    initialTask: StudentTask? = null,
    backArrow: Boolean = true,
) {
    when (val screenState = viewModel.state.collectAsStateWithLifecycle().value) {
        Loadable.Loading -> LoadingDetailScreen(onBack.takeIf { backArrow })
        is Loadable.Ready -> StudentCard(
            screenState.value,
            viewModel,
            StudentNavigation(onBack, backArrow, onLessonClick, onBookLesson),
            initialTask,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentCard(
    state: StudentDetails,
    viewModel: StudentDetailViewModel,
    navigation: StudentNavigation,
    initialTask: StudentTask?,
) {
    val student = state.student

    var tab by rememberSaveable { mutableStateOf(StudentTab.CLASSES) }
    var lessonFilter by rememberSaveable { mutableStateOf(LessonFilter.ALL) }
    // Filtered here rather than in the list's builder, which runs again on every scroll-driven remeasure.
    val lessonRows = remember(state, lessonFilter) { state.lessonsShown(lessonFilter) }
    var editing by remember { mutableStateOf(false) }
    var addingSubject by remember { mutableStateOf(false) }
    var pricingSubjectId by remember { mutableStateOf<Long?>(null) }
    // Asked first: taking a subject off a student forgets its price and the log of its past prices.
    var removingSubjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var addingPayment by remember { mutableStateOf(false) }
    var editingPayment by remember { mutableStateOf<Payment?>(null) }
    var confirmingArchive by remember { mutableStateOf(false) }
    // Taken when the dialog opens — see `StudentDetailViewModel.homeworkChoice`.
    var addingHomework by remember { mutableStateOf<AssigneeChoice?>(null) }
    var openedHomework by remember { mutableStateOf<HomeworkRow?>(null) }
    var taskHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(student?.id, initialTask) {
        if (taskHandled || student == null || initialTask == null) return@LaunchedEffect
        when (initialTask) {
            StudentTask.HOMEWORK -> {
                tab = StudentTab.HOMEWORK
                addingHomework = viewModel.homeworkChoice()
            }
            StudentTask.PAYMENT -> { tab = StudentTab.PAYMENTS; addingPayment = true }
            StudentTask.SUBJECT -> {
                tab = StudentTab.SUBJECTS
                addingSubject = true
            }
        }
        taskHandled = true
    }

    val messages = rememberMessages()
    val externalApps = rememberExternalApps(messages)
    val context = LocalContext.current
    val noAppMsg = stringResource(R.string.student_contact_failed)

    // Read inside the collector through `rememberUpdatedState`, so the words are the language in force
    // when the event arrives — and the plural's count is the one the event carries, the write's own.
    val resources by rememberUpdatedState(context.resources)
    // A hand collector rather than `ShowMessagesFor`: the refusal carries a way to the lessons it names.
    val rejectFeedback = rememberRejectFeedback()
    LaunchedEffect(Unit) {
        viewModel.events.stream.collect { event ->
            when (event) {
                // The refusal names lessons the tutor now has to go and look at — and they are one
                // tab away, so the message carries the way there. This is the thing a toast could
                // never do: state a problem *and* offer the next step.
                StudentDetailViewModel.Event.RemoveBlockedHasClasses -> {
                    rejectFeedback()
                    messages.show(
                        resources.getString(R.string.student_subject_remove_blocked),
                        resources.getString(R.string.action_show),
                    ) { tab = StudentTab.CLASSES }
                }

                is StudentDetailViewModel.Event.ArchiveCancelledLessons -> messages.show(
                    resources.getQuantityString(R.plurals.student_archive_lessons_cancelled, event.count, event.count),
                )
                StudentDetailViewModel.Event.WriteFailed -> messages.show(resources.getString(R.string.action_failed))
            }
        }
    }

    val homeworkUndo = homeworkUndo(
        messages = messages,
        setDone = viewModel::setHomeworkDone,
        putBack = viewModel::putHomeworkBack,
        delete = viewModel::deleteHomework,
        restore = viewModel::restoreHomework,
    )
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
        onBack = navigation.back.takeIf { navigation.backArrow },
        titleSize = TitleSize.Large,
        messages = messages,
        actions = {
            if (student != null && !student.archived) {
                NamedIconButton(
                    KlasIcons.Outlined.EditCalendar,
                    stringResource(R.string.class_book),
                    onClick = navigation.book,
                )
            }
            NamedIconButton(KlasIcons.Outlined.Edit, stringResource(R.string.action_edit), onClick = { editing = true })
            // Archiving is behind the overflow now. It used to be a bare text button in the
            // bar — «Имя [✎] [В архив]» — which put the most consequential thing on the
            // screen beside the pencil, at the width of a word, next to a name that has to
            // ellipsize to make room for it.
            if (student != null) {
                StudentOverflowMenu(
                    student = student,
                    // The same three launches the contact block makes, through the same guard: a tablet
                    // with no dialler must not crash the card.
                    onCall = { phone -> externalApps.open(ExternalTarget.Dial(phone), noAppMsg) },
                    onTelegram = { phone ->
                        externalApps.open(ExternalTarget.Telegram(phone), noAppMsg)
                    },
                    onEmail = { email -> externalApps.open(ExternalTarget.Email(email), noAppMsg) },
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
    ) { modifier, bottomInset ->
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = Spacing.sectionGap).plusInset(bottomInset),
        ) {
            // Identity, then the numbers, then the tabs — the same three in the same order as the
            // group's card (see [detailCard]). This one used to lead with the stat row and put the contacts
            // second, so the two cards disagreed about their own structure.
            detailCard(
                identity = {
                    item(key = StudentDetailKeys.CONTACTS) {
                        student?.let {
                            ContactBlock(
                                student = it,
                                onCall = { phone ->
                                    externalApps.open(ExternalTarget.Dial(phone), noAppMsg)
                                },
                                onEmail = { email ->
                                    externalApps.open(ExternalTarget.Email(email), noAppMsg)
                                },
                                onTelegram = { phone ->
                                    externalApps.open(ExternalTarget.Telegram(phone), noAppMsg)
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
                },
                stats = { studentStats(state) },
                views = StudentTab.entries,
                selected = tab,
                onSelect = { tab = it },
                label = { stringResource(it.labelRes) },
                icon = { it.icon },
                count = { entry ->
                    when (entry) {
                        StudentTab.CLASSES -> state.classes.size
                        StudentTab.PAYMENTS -> state.payments.size
                        StudentTab.HOMEWORK -> state.homework.current.size
                        StudentTab.SUBJECTS -> state.subjects.size
                    }
                },
            ) { shown ->
                when (shown) {
                    StudentTab.SUBJECTS -> subjectsTab(
                        state = state,
                        onAdd = { addingSubject = true },
                        onReprice = { id -> pricingSubjectId = id },
                        onRemove = { removingSubjectId = it },
                    )
                    StudentTab.CLASSES -> classesTab(
                        state = state,
                        rows = lessonRows,
                        filter = lessonFilter,
                        onLessonClick = navigation.lesson,
                        onFilter = { lessonFilter = it },
                    )
                    StudentTab.PAYMENTS -> paymentsTab(
                        state = state,
                        onAdd = { addingPayment = true },
                        onEdit = { editingPayment = it },
                        onDelete = ::deletePaymentWithUndo,
                    )
                    StudentTab.HOMEWORK -> homeworkItems(
                        owner = student?.let { Assignee.Student(it.id) },
                        lists = state.homework,
                        messages = messages,
                        onAdd = { addingHomework = viewModel.homeworkChoice() },
                        onOpen = { openedHomework = it },
                        onDone = homeworkUndo::swiped,
                    )
                }
            }
        }
    }

    if (editing && student != null) {
        StudentEditDialog(
            title = stringResource(R.string.student_edit_title),
            initialName = student.name,
            initialPhone = Phone.editable(student.phone),
            initialEmail = student.email,
            initialNote = student.note,
            initialMode = student.defaultMode,
            onConfirm = { name, phone, email, note, mode -> viewModel.editStudent(name, phone, email, note, mode) },
            onDismiss = { editing = false },
        )
    }
    if (addingSubject) {
        AddSubjectPriceDialog(
            addableSubjects = state.addableSubjects,
            noSubjectsYet = state.allSubjects.isEmpty(),
            onConfirm = { subjectId, amount -> viewModel.addSubject(subjectId, amount) },
            onDismiss = { addingSubject = false },
        )
    }
    removingSubjectId?.let { subjectId ->
        state.subjects.firstOrNull { it.subject.id == subjectId }?.let { sp ->
            ConfirmDialog(
                text = stringResource(R.string.student_subject_remove_confirm, sp.subject.name),
                confirmLabel = stringResource(R.string.action_remove),
                destructive = true,
                onConfirm = { viewModel.removeSubject(subjectId) },
                onDismiss = { removingSubjectId = null },
            )
        }
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
    CardHomeworkDialogs(
        adding = addingHomework,
        opened = openedHomework,
        onDismiss = { addingHomework = null; openedHomework = null },
        onGive = viewModel::giveHomework,
        onEdit = viewModel::editHomework,
        undo = homeworkUndo,
    )
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
        ConfirmDialog(
            text = parts.joinToString(" "),
            confirmLabel = stringResource(R.string.action_archive),
            onConfirm = { viewModel.setArchived(true) },
            onDismiss = { confirmingArchive = false },
        )
    }
}

@Composable
private fun studentStats(state: StudentDetails): List<Stat> {
    val totals = state.totals
    return listOfNotNull(
        // Hours as the hint, in place of the tile they used to have. `lessonsStat` prefers «впереди N»
        // when there are lessons still to come — that is something to act on and outranks a total.
        lessonsStat(totals).let { stat ->
            stat.copy(hint = stat.hint ?: stringResource(R.string.hours_value, formatHours(totals.minutes)))
        },
        earnedStat(totals.earned),
    )
}

@Composable
private fun BalanceHero(state: StudentDetails, modifier: Modifier = Modifier) {
    val balance = state.balance
    HeroCard(
        label = stringResource(
            when (balance) {
                is Balance.Credit -> R.string.stat_credit
                Balance.Settled, is Balance.Owed -> R.string.stat_debt
            },
        ),
        value = balance.amount.text(),
        modifier = modifier,
        tonal = true,
    ) {
        // Not the balance again in words — the label and the number already say it — but when the money last
        // came in, which they do not.
        Text(
            state.lastPaymentAt?.let { stringResource(R.string.student_last_payment, it.formatDate()) }
                ?: stringResource(R.string.payments_empty),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun LazyListScope.subjectsTab(
    state: StudentDetails,
    onAdd: () -> Unit,
    onReprice: (Long) -> Unit,
    onRemove: (Long) -> Unit,
) {
    if (state.subjects.isEmpty()) {
        item(key = StudentDetailKeys.SUBJECTS_EMPTY) {
            EmptyState(
                title = stringResource(R.string.student_no_subjects),
                icon = KlasIcons.Outlined.MenuBook,
                actionLabel = stringResource(R.string.student_add_subject),
                onAction = onAdd,
                compact = true,
            )
        }
        return
    }
    item(key = StudentDetailKeys.SUBJECTS_ADD) { AddRow(stringResource(R.string.student_add_subject), onAdd) }
    items(state.subjects, key = { StudentDetailKeys.subject(it.subject.id) }, contentType = { "subject" }) { sp ->
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
                        Icon(KlasIcons.Outlined.Edit, stringResource(R.string.price_edit_title))
                    }
                    IconButton(onClick = { onRemove(sp.subject.id) }) {
                        Icon(
                            KlasIcons.Outlined.Delete,
                            stringResource(R.string.student_subject_remove, sp.subject.name),
                        )
                    }
                }
            },
            modifier = animateInPlace(),
        )
    }
}

private fun LazyListScope.classesTab(
    state: StudentDetails,
    rows: List<ClassRow>,
    filter: LessonFilter,
    onFilter: (LessonFilter) -> Unit,
    onLessonClick: (Long) -> Unit,
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
    if (rows.isEmpty()) {
        item(key = StudentDetailKeys.CLASSES_EMPTY) {
            // No action: booking belongs to the schedule, which is the one place that knows what else
            // the hour holds. See the card's KDoc.
            EmptyState(
                title = stringResource(R.string.student_lessons_empty),
                icon = KlasIcons.Outlined.EventBusy,
                compact = true,
            )
        }
        return
    }
    items(rows, key = { StudentDetailKeys.klass(it.klass.id) }, contentType = { "lesson" }) { row ->
        ListItem(
            leadingContent = { LessonKindIcon(row.kind) },
            headlineContent = {
                Text(
                    // When first, as on the group card: it is what tells one row from the next, so it is
                    // the half that must survive when a large font leaves room for only one of the two.
                    bulletJoin(row.klass.start.dateTimeText(), row.subjectName),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                // The status in the pill the schedule says it in — it was coloured text here, the
                // one place a lesson's state was a font style.
                val status = row.klass.status
                BadgeLine(badge = { StatusBadge(classStatusLabel(status), classStatusColor(status)) }) {
                    // The group's name for a group lesson, since *which* group is the useful half of
                    // "not individual" — a student in two groups is billed a different rate by each. An
                    // individual lesson says nothing here: «Индивидуальное» repeated the icon on every row.
                    row.groupName?.let { group ->
                        Text(
                            group,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            trailingContent = {
                row.shownCharge?.let { TrailingAmount(it.text()) }
            },
            // Opens the lesson, as the schedule and the group's card do: a tutor reading a student's
            // history is the one most likely to spot the lesson to correct.
            modifier = Modifier.insetRowPress {
                Modifier.clickable(it, indication = null) { onLessonClick(row.klass.id) }
            },
        )
    }
}

private fun LazyListScope.paymentsTab(
    state: StudentDetails,
    onAdd: () -> Unit,
    onEdit: (Payment) -> Unit,
    onDelete: (Payment) -> Unit,
) {
    if (state.payments.isEmpty()) {
        item(key = StudentDetailKeys.PAYMENTS_EMPTY) {
            EmptyState(
                title = stringResource(R.string.payments_empty),
                icon = KlasIcons.Outlined.Payments,
                actionLabel = stringResource(R.string.add_payment),
                onAction = onAdd,
                compact = true,
            )
        }
        return
    }
    item(key = StudentDetailKeys.PAYMENTS_ADD) { AddRow(stringResource(R.string.add_payment), onAdd) }
    items(state.payments, key = { StudentDetailKeys.payment(it.id) }, contentType = { "payment" }) { payment ->
        ListItem(
            headlineContent = { Text(payment.amount.text()) },
            supportingContent = { Text(payment.date.formatDate()) },
            trailingContent = {
                IconButton(onClick = { onDelete(payment) }) {
                    Icon(KlasIcons.Outlined.Delete, stringResource(R.string.payment_delete))
                }
            },
            // Tapping the row corrects it; the bin still removes it. Editing is the commoner of the
            // two — a wrong amount is a typo, not a payment that did not happen.
            modifier = Modifier
                .insetRowPress { Modifier.clickable(it, indication = null) { onEdit(payment) } }
                .then(animateInPlace()),
        )
    }
}
