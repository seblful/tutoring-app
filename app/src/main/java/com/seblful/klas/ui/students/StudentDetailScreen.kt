package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.people.Phone
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.queries.students.ArchiveIntent
import com.seblful.klas.queries.students.StudentDetails
import com.seblful.klas.ui.common.AddRow
import com.seblful.klas.ui.common.AddRowInSection
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.ExternalTarget
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.LoadingDetailScreen
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.common.NamedIconButton
import com.seblful.klas.ui.common.OverviewSection
import com.seblful.klas.ui.common.SectionNote
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.TitleSize
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.plusInset
import com.seblful.klas.ui.common.rememberExternalApps
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.rememberRejectFeedback
import com.seblful.klas.ui.common.sectionRowColors
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.homework.CardHomeworkDialogs
import com.seblful.klas.ui.homework.HomeworkSection
import com.seblful.klas.ui.people.NextLessonCard
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

private data class StudentNavigation(
    val back: () -> Unit,
    /** Whether the bar draws [back] as an arrow; not beside the list the card was opened from. */
    val backArrow: Boolean,
    val lesson: (Long) -> Unit,
    val book: () -> Unit,
    val seeAll: (StudentSection) -> Unit,
)

/**
 * The card, once there is a student to draw one for: who they are, then **an overview** — their next lesson,
 * what they owe, and the latest of each of their lists, the long ones opening in full ([StudentSection]) — so
 * nothing on the card is hidden behind another view.
 *
 * @param backArrow false beside the People list the card was opened from: the list is the way to another.
 */
@Composable
fun StudentDetailScreen(
    onBack: () -> Unit,
    onLessonClick: (Long) -> Unit,
    viewModel: StudentDetailViewModel = assistedMetroViewModel(),
    onBookLesson: () -> Unit = {},
    onSeeAll: (StudentSection) -> Unit = {},
    initialTask: StudentTask? = null,
    backArrow: Boolean = true,
) {
    when (val overview = viewModel.overview.collectAsStateWithLifecycle().value) {
        null -> LoadingDetailScreen(onBack.takeIf { backArrow })
        else -> StudentCard(
            overview,
            viewModel,
            StudentNavigation(onBack, backArrow, onLessonClick, onBookLesson, onSeeAll),
            initialTask,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentCard(
    overview: StudentOverview,
    viewModel: StudentDetailViewModel,
    navigation: StudentNavigation,
    initialTask: StudentTask?,
) {
    val state = overview.details
    val student = state.student

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
        // Each task's dialog opens over the overview, which shows the section it adds to.
        when (initialTask) {
            StudentTask.HOMEWORK -> addingHomework = viewModel.homeworkChoice()
            StudentTask.PAYMENT -> addingPayment = true
            StudentTask.SUBJECT -> addingSubject = true
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
    val seeAll by rememberUpdatedState(navigation.seeAll)
    // A hand collector rather than `ShowMessagesFor`: the refusal carries a way to the lessons it names.
    val rejectFeedback = rememberRejectFeedback()
    LaunchedEffect(Unit) {
        viewModel.events.stream.collect { event ->
            when (event) {
                // The refusal names lessons the tutor now has to go and look at — so the message carries
                // the way there, which is the thing a toast could never do: state a problem *and* offer the
                // next step.
                StudentDetailViewModel.Event.RemoveBlockedHasClasses -> {
                    rejectFeedback()
                    messages.show(
                        resources.getString(R.string.student_subject_remove_blocked),
                        resources.getString(R.string.action_show),
                    ) { seeAll(StudentSection.LESSONS) }
                }

                else -> messages.showStudentEvent(event, resources, viewModel)
            }
        }
    }

    val homeworkUndo = studentHomeworkUndo(viewModel, messages)
    val deletePayment = paymentDeleteWithUndo(viewModel, messages)

    // A student's name is what this screen is *about*, so it gets the large title Material designed for
    // exactly that — collapsing into an ordinary bar as the list scrolls. [TitleSize.Large] is that, and the
    // scroll behaviour it needs, said once.
    KlasScreen(
        title = student?.name,
        onBack = navigation.back.takeIf { navigation.backArrow },
        titleSize = TitleSize.Large,
        messages = messages,
        actions = {
            // Booking is the next lesson's «+», in the card it books into.
            NamedIconButton(KlasIcons.Outlined.Edit, stringResource(R.string.action_edit), onClick = { editing = true })
            // Archiving is behind the overflow: as a bare text button in the bar it put the most consequential
            // thing on the screen beside the pencil, next to a name that had to ellipsize to make room for it.
            if (student != null) {
                StudentOverflowMenu(
                    student = student,
                    shares = rememberAccountShares(state, externalApps, noAppMsg),
                    // The same launches the contact block makes, through the same guard: a tablet with no
                    // dialler must not crash the card.
                    onCall = { phone -> externalApps.open(ExternalTarget.Dial(phone), noAppMsg) },
                    onTelegram = { phone -> externalApps.open(ExternalTarget.Telegram(phone), noAppMsg) },
                    onEmail = { email -> externalApps.open(ExternalTarget.Email(email), noAppMsg) },
                    // Whether archiving is worth asking about, and what the question would have to say, are
                    // both `state.archiveIntent`'s — see [ArchiveIntent].
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
        val cardModifier = Modifier.padding(horizontal = Spacing.gutter)
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = Spacing.sectionGap).plusInset(bottomInset),
            // Separate cards, each a section of its own, rather than parts of one surface.
            verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
        ) {
            item(key = StudentDetailKeys.CONTACTS, contentType = StudentDetailKeys.CONTACTS) {
                student?.let {
                    ContactBlock(
                        student = it,
                        onCall = { phone -> externalApps.open(ExternalTarget.Dial(phone), noAppMsg) },
                        onEmail = { email -> externalApps.open(ExternalTarget.Email(email), noAppMsg) },
                        onTelegram = { phone -> externalApps.open(ExternalTarget.Telegram(phone), noAppMsg) },
                        // The icons' own touch padding stands in for most of the gutter, so their glyphs line
                        // up with the cards' edge below.
                        modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.tight),
                    )
                }
            }
            // An archived student's planned lessons were called off, and the form books nobody archived.
            if (student != null && !student.archived) {
                item(key = StudentDetailKeys.NEXT, contentType = StudentDetailKeys.NEXT) {
                    NextLessonCard(
                        next = overview.next,
                        onOpen = navigation.lesson,
                        onBook = navigation.book,
                        modifier = animateInPlace(cardModifier),
                    )
                }
            }
            item(key = StudentDetailKeys.BALANCE, contentType = StudentDetailKeys.BALANCE) {
                BalanceCard(state, onAddPayment = { addingPayment = true }, modifier = animateInPlace(cardModifier))
            }
            item(key = StudentDetailKeys.LESSONS, contentType = StudentDetailKeys.LESSONS) {
                LessonsSection(
                    overview = overview,
                    onLessonClick = navigation.lesson,
                    onSeeAll = { navigation.seeAll(StudentSection.LESSONS) },
                    modifier = animateInPlace(cardModifier),
                )
            }
            item(key = StudentDetailKeys.HOMEWORK, contentType = StudentDetailKeys.HOMEWORK) {
                HomeworkSection(
                    owner = student?.let { Assignee.Student(it.id) },
                    lists = state.homework,
                    messages = messages,
                    onAdd = { addingHomework = viewModel.homeworkChoice() },
                    onOpen = { openedHomework = it },
                    onDone = homeworkUndo::swiped,
                    onSeeAll = { navigation.seeAll(StudentSection.HOMEWORK) },
                    modifier = animateInPlace(cardModifier),
                )
            }
            item(key = StudentDetailKeys.PAYMENTS, contentType = StudentDetailKeys.PAYMENTS) {
                PaymentsSection(
                    payments = state.payments,
                    onEdit = { editingPayment = it },
                    onSeeAll = { navigation.seeAll(StudentSection.PAYMENTS) },
                    modifier = animateInPlace(cardModifier),
                )
            }
            item(key = StudentDetailKeys.SUBJECTS, contentType = StudentDetailKeys.SUBJECTS) {
                SubjectsSection(
                    state = state,
                    onAdd = { addingSubject = true },
                    onOpen = { pricingSubjectId = it },
                    modifier = animateInPlace(cardModifier),
                )
            }
        }
    }

    if (editing && student != null) {
        StudentEditDialog(
            title = stringResource(R.string.student_edit_title),
            initialName = student.name,
            initialPhone = Phone.editable(student.phone, LocalCurrency.current),
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
                onRemove = { removingSubjectId = subjectId },
            )
        }
    }
    PaymentDialogs(
        viewModel = viewModel,
        adding = addingPayment,
        editing = editingPayment,
        onDelete = deletePayment,
        onDismiss = { addingPayment = false; editingPayment = null },
    )
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
private fun LessonsSection(
    overview: StudentOverview,
    onLessonClick: (Long) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = overview.details.classes
    OverviewSection(
        title = stringResource(R.string.student_classes_title),
        count = all.size,
        onSeeAll = onSeeAll.takeIf { all.size > overview.lessons.size },
        modifier = modifier,
    ) {
        if (all.isEmpty()) SectionNote(stringResource(R.string.student_lessons_empty))
        overview.lessons.forEach { row ->
            LessonRow(row, onClick = { onLessonClick(row.klass.id) }, colors = sectionRowColors())
        }
    }
}

@Composable
private fun PaymentsSection(
    payments: List<Payment>,
    onEdit: (Payment) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = remember(payments) { payments.sortedByDescending { it.date }.take(StudentOverview.PAYMENTS_SHOWN) }
    OverviewSection(
        title = stringResource(R.string.student_payments_title),
        count = payments.size,
        onSeeAll = onSeeAll.takeIf { payments.size > shown.size },
        modifier = modifier,
    ) {
        // Recorded from the balance's button, so no add row here.
        if (payments.isEmpty()) SectionNote(stringResource(R.string.payments_empty))
        shown.forEach { payment ->
            PaymentRow(
                payment,
                onEdit = { onEdit(payment) },
                colors = sectionRowColors(),
            )
        }
    }
}

/** Every subject, in place: a student takes a few, so there is no longer list to open. */
@Composable
private fun SubjectsSection(
    state: StudentDetails,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    OverviewSection(
        title = stringResource(R.string.student_subjects_title),
        count = 0,
        onSeeAll = null,
        modifier = modifier,
    ) {
        if (state.subjects.isEmpty()) SectionNote(stringResource(R.string.student_no_subjects))
        state.subjects.forEach { sp ->
            SubjectRow(
                sp,
                onOpen = { onOpen(sp.subject.id) },
                colors = sectionRowColors(),
            )
        }
        AddRow(stringResource(R.string.student_add_subject), onAdd, AddRowInSection)
    }
}
