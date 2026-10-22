package com.seblful.klas.ui.students

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.LessonFilter
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.queries.students.ClassRow
import com.seblful.klas.queries.students.StudentDetails
import com.seblful.klas.ui.common.AddRow
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.LoadingDetailScreen
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.plusInset
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.homework.CardHomeworkDialogs
import com.seblful.klas.ui.homework.homeworkItems
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

/** One of a student's lists in full — what «See all» under a section of their card opens. */
@Composable
fun StudentListScreen(
    section: StudentSection,
    onBack: () -> Unit,
    onLessonClick: (Long) -> Unit,
    viewModel: StudentDetailViewModel = assistedMetroViewModel(),
) {
    when (val screenState = viewModel.state.collectAsStateWithLifecycle().value) {
        Loadable.Loading -> LoadingDetailScreen(onBack)
        is Loadable.Ready -> StudentList(screenState.value, section, viewModel, onBack, onLessonClick)
    }
}

@Composable
private fun StudentList(
    state: StudentDetails,
    section: StudentSection,
    viewModel: StudentDetailViewModel,
    onBack: () -> Unit,
    onLessonClick: (Long) -> Unit,
) {
    var lessonFilter by rememberSaveable { mutableStateOf(LessonFilter.ALL) }
    // Filtered here rather than in the list's builder, which runs again on every scroll-driven remeasure.
    val lessonRows = remember(state, lessonFilter) { state.lessonsShown(lessonFilter) }
    var addingPayment by remember { mutableStateOf(false) }
    var editingPayment by remember { mutableStateOf<Payment?>(null) }
    // Taken when the dialog opens — see `StudentDetailViewModel.homeworkChoice`.
    var addingHomework by remember { mutableStateOf<AssigneeChoice?>(null) }
    var openedHomework by remember { mutableStateOf<HomeworkRow?>(null) }

    val messages = rememberMessages()
    ShowStudentMessages(viewModel, messages)
    val homeworkUndo = studentHomeworkUndo(viewModel, messages)
    val deletePayment = paymentDeleteWithUndo(viewModel, messages)

    KlasScreen(
        title = stringResource(section.titleRes),
        onBack = onBack,
        messages = messages,
    ) { modifier, bottomInset ->
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = Spacing.sectionGap).plusInset(bottomInset),
        ) {
            when (section) {
                StudentSection.LESSONS -> lessons(
                    state = state,
                    rows = lessonRows,
                    filter = lessonFilter,
                    onFilter = { lessonFilter = it },
                    onLessonClick = onLessonClick,
                )
                StudentSection.PAYMENTS -> payments(
                    payments = state.payments,
                    onAdd = { addingPayment = true },
                    onEdit = { editingPayment = it },
                    onDelete = deletePayment,
                )
                StudentSection.HOMEWORK -> homeworkItems(
                    owner = state.student?.let { Assignee.Student(it.id) },
                    lists = state.homework,
                    messages = messages,
                    onAdd = { addingHomework = viewModel.homeworkChoice() },
                    onOpen = { openedHomework = it },
                    onDone = homeworkUndo::swiped,
                )
            }
        }
    }

    PaymentDialogs(
        viewModel = viewModel,
        adding = addingPayment,
        editing = editingPayment,
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
}

/** The card's events, in the card's words — a payment corrected on this page is offered back here. */
@Composable
private fun ShowStudentMessages(viewModel: StudentDetailViewModel, messages: Messages) {
    val resources by rememberUpdatedState(LocalContext.current.resources)
    LaunchedEffect(viewModel) {
        viewModel.events.stream.collect { messages.showStudentEvent(it, resources, viewModel) }
    }
}

private fun LazyListScope.lessons(
    state: StudentDetails,
    rows: List<ClassRow>,
    filter: LessonFilter,
    onFilter: (LessonFilter) -> Unit,
    onLessonClick: (Long) -> Unit,
) {
    if (state.hasBothKinds) {
        item(key = StudentDetailKeys.CLASSES_FILTER, contentType = StudentDetailKeys.CLASSES_FILTER) {
            LessonFilterChips(
                selected = filter,
                tally = state.lessonKinds,
                onSelect = onFilter,
                modifier = Modifier.padding(vertical = Spacing.inline),
            )
        }
    }
    if (rows.isEmpty()) {
        item(key = StudentDetailKeys.CLASSES_EMPTY, contentType = StudentDetailKeys.CLASSES_EMPTY) {
            // No action: booking belongs to the schedule's form, reached from the card's next lesson.
            EmptyState(
                title = stringResource(R.string.student_lessons_empty),
                icon = KlasIcons.Outlined.EventBusy,
                compact = true,
            )
        }
        return
    }
    items(rows, key = { StudentDetailKeys.klass(it.klass.id) }, contentType = { "lesson" }) { row ->
        LessonRow(row, onClick = { onLessonClick(row.klass.id) })
    }
}

private fun LazyListScope.payments(
    payments: List<Payment>,
    onAdd: () -> Unit,
    onEdit: (Payment) -> Unit,
    onDelete: (Payment) -> Unit,
) {
    if (payments.isEmpty()) {
        item(key = StudentDetailKeys.PAYMENTS_EMPTY, contentType = StudentDetailKeys.PAYMENTS_EMPTY) {
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
    item(key = StudentDetailKeys.PAYMENTS_ADD, contentType = StudentDetailKeys.PAYMENTS_ADD) {
        AddRow(stringResource(R.string.add_payment), onAdd)
    }
    items(payments, key = { StudentDetailKeys.payment(it.id) }, contentType = { "payment" }) { payment ->
        PaymentRow(
            payment = payment,
            onEdit = { onEdit(payment) },
            onDelete = { onDelete(payment) },
            modifier = animateInPlace(),
        )
    }
}
