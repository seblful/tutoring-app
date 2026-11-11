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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.domain.lesson.LessonFilter
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.formatHours
import com.seblful.klas.format.formatMonth
import com.seblful.klas.queries.students.LessonMonth
import com.seblful.klas.queries.students.StudentDetails
import com.seblful.klas.queries.students.statusTally
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.Strings
import com.seblful.klas.text.currentTexts
import com.seblful.klas.text.pluralStringResource
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.common.AddRow
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.LoadingDetailScreen
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.plusInset
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.sectionRowColors
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.text
import com.seblful.klas.ui.designsystem.component.HeaderedCard
import com.seblful.klas.ui.homework.homeworkItems
import com.seblful.klas.ui.homework.rememberCardHomework
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

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
    val history = rememberLessonHistory(state, viewModel.zone.collectAsStateWithLifecycle().value)
    // A value here, not a delegate the list's builder reads — see docs/ui-conventions.md.
    val now = viewModel.minute.collectAsStateWithLifecycle().value

    val messages = rememberMessages()
    val homework = rememberCardHomework(viewModel.cardHomework, messages)
    val cardPayments = rememberCardPayments(viewModel, messages)

    ShowStudentMessages(viewModel, messages)

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
                    months = history.months,
                    filters = history.filters,
                    now = now,
                    onLessonClick = onLessonClick,
                )
                StudentSection.PAYMENTS -> payments(
                    payments = state.payments,
                    onAdd = cardPayments::add,
                    onEdit = cardPayments::open,
                )
                StudentSection.HOMEWORK -> homeworkItems(
                    owner = state.student?.let { Assignee.Student(it.id) },
                    lists = state.homework,
                    messages = messages,
                    onAdd = homework::add,
                    onOpen = homework::open,
                    onDone = homework::swiped,
                )
            }
        }
    }

    cardPayments.Dialogs()
    homework.Dialogs()
}

/** The card's events, in the card's words — a payment corrected on this page is offered back here. */
@Composable
private fun ShowStudentMessages(viewModel: StudentDetailViewModel, messages: Messages) {
    val resources by rememberUpdatedState(currentTexts())
    LaunchedEffect(viewModel) {
        viewModel.events.stream.collect { messages.showStudentEvent(it, resources, viewModel) }
    }
}

/** The lesson history as narrowed: its filters, and its months under them. */
private class LessonHistory(val filters: LessonHistoryFilters, val months: List<LessonMonth>)

@Composable
private fun rememberLessonHistory(state: StudentDetails, zone: TimeZone): LessonHistory {
    var kind by rememberSaveable { mutableStateOf(LessonFilter.ALL) }
    var status by rememberSaveable { mutableStateOf<LessonStatus?>(null) }
    // Filtered and cut into months here rather than in the list's builder, which runs again on every
    // scroll-driven remeasure.
    val months = remember(state, kind, status, zone) {
        LessonMonth.of(state.lessonsShown(kind, status), zone)
    }
    return LessonHistory(LessonHistoryFilters(kind, { kind = it }, status) { status = it }, months)
}

/** What the lesson history is narrowed to, and how to change it. */
private class LessonHistoryFilters(
    val kind: LessonFilter,
    val onKind: (LessonFilter) -> Unit,
    val status: LessonStatus?,
    val onStatus: (LessonStatus?) -> Unit,
)

private fun LazyListScope.lessons(
    state: StudentDetails,
    months: List<LessonMonth>,
    filters: LessonHistoryFilters,
    now: Instant,
    onLessonClick: (Long) -> Unit,
) {
    if (state.classes.isNotEmpty()) {
        item(key = StudentDetailKeys.CLASSES_FILTER, contentType = StudentDetailKeys.CLASSES_FILTER) {
            LessonHistoryChips(
                status = filters.status,
                statuses = remember(state) { statusTally(state.classes) },
                onStatus = filters.onStatus,
                kind = filters.kind,
                kinds = state.lessonKinds.takeIf { state.hasBothKinds },
                onKind = filters.onKind,
                modifier = Modifier.padding(vertical = Spacing.inline),
            )
        }
    }
    if (months.isEmpty()) {
        item(key = StudentDetailKeys.CLASSES_EMPTY, contentType = StudentDetailKeys.CLASSES_EMPTY) {
            // No action: booking belongs to the schedule's form, reached from the card's next lesson.
            EmptyState(
                title = stringResource(Strings.student_lessons_empty),
                icon = KlasIcons.Outlined.EventBusy,
                compact = true,
            )
        }
        return
    }
    // A card a month, its totals in the header: the history reads as months, as the tutor bills them.
    items(months, key = { "month-${it.month}" }, contentType = { "month" }) { month ->
        HeaderedCard(
            title = formatMonth(month.month),
            summary = monthSummary(month),
            modifier = animateInPlace(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.tight)),
        ) {
            month.rows.forEach { row ->
                LessonRow(row, now, onClick = { onLessonClick(row.klass.id) }, colors = sectionRowColors())
            }
        }
    }
}

private fun LazyListScope.payments(
    payments: List<Payment>,
    onAdd: () -> Unit,
    onEdit: (Payment) -> Unit,
) {
    if (payments.isEmpty()) {
        item(key = StudentDetailKeys.PAYMENTS_EMPTY, contentType = StudentDetailKeys.PAYMENTS_EMPTY) {
            EmptyState(
                title = stringResource(Strings.payments_empty),
                icon = KlasIcons.Outlined.Payments,
                actionLabel = stringResource(Strings.add_payment),
                onAction = onAdd,
                compact = true,
            )
        }
        return
    }
    item(key = StudentDetailKeys.PAYMENTS_ADD, contentType = StudentDetailKeys.PAYMENTS_ADD) {
        AddRow(stringResource(Strings.add_payment), onAdd)
    }
    items(payments, key = { StudentDetailKeys.payment(it.id) }, contentType = { "payment" }) { payment ->
        PaymentRow(
            payment = payment,
            onEdit = { onEdit(payment) },
            modifier = animateInPlace(),
        )
    }
}

/** "9 lessons · 12 h · 287.50 $ earned" — the earned part only once the month has taught something. */
@Composable
private fun monthSummary(month: LessonMonth): String {
    val lessons = pluralStringResource(Plurals.lesson_count, month.held, month.held)
    val hours = stringResource(Strings.hours_value, formatHours(month.minutes))
    return if (month.earned.isZero) {
        bulletJoin(lessons, hours)
    } else {
        bulletJoin(lessons, hours, stringResource(Strings.lesson_month_earned, month.earned.text()))
    }
}
