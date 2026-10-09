package com.seblful.klas.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.DeleteWarning
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.schedule.DaySummary
import com.seblful.klas.domain.schedule.ScheduleEmpty
import com.seblful.klas.domain.schedule.TimeConflict
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.FabListPadding
import com.seblful.klas.ui.common.FilterChipRow
import com.seblful.klas.ui.common.FilterMenuAction
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.NoticeDialog
import com.seblful.klas.ui.common.SegmentedPage
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.rememberConfirmFeedback
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.rememberRejectFeedback
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.navigation.Arrival
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    /** Books a lesson, opening its date picker on the day given — the one the schedule is on. */
    onAddClass: (LocalDate) -> Unit,
    onEditClass: (Long) -> Unit,
    onAddStudent: () -> Unit,
    /**
     * The latest tap in the tray that asked for the schedule — a day, or a lesson's sheet — or null when
     * nothing has, the launcher's ordinary case. See `AppRoot`.
     *
     * An arrival rather than a flag the screen has to clear: this screen is the graph's start destination
     * and its view model outlives every return to it, so "put it on this day" has to be an *event* — and
     * the view model, not this effect, remembers which one it has already honoured (see
     * [ScheduleViewModel.arrive]).
     */
    arrival: Arrival<ScheduleTarget>? = null,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    LaunchedEffect(arrival) { viewModel.arrive(arrival) }

    // One state for the body: the week holding what is on screen (see [ScheduleViewModel.content]), and
    // the day the day and month views read out of it — which names its own date, see [shownDay].
    val contentState by viewModel.content.collectAsState()
    val dayContent by viewModel.shownDay.collectAsState()
    val date by viewModel.selectedDate.collectAsState()

    // Every way in to a booking — the add button, an empty day's — books into the day on screen.
    val addClass = { onAddClass(date) }
    val canAddClass by viewModel.canAddClass.collectAsState()
    // From the view model, so it rolls over at midnight rather than being fixed at composition.
    val today by viewModel.today.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val month by viewModel.visibleMonth.collectAsState()
    val grid by viewModel.monthGrid.collectAsState()
    val week by viewModel.weekDays.collectAsState()
    val loads by viewModel.dayLoads.collectAsState()
    val timeConflict by viewModel.timeConflict.collectAsState()
    // Where "now" falls in the selected day's list, or null when that day is not today — see [NowMarker].
    val nowMark by viewModel.nowMark.collectAsState()
    // Which lessons are being taught right this minute — see [ScheduleViewModel.running].
    val running by viewModel.running.collectAsState()
    // Whose lessons are on screen, or null for everyone's. Both the chip and the queries read this one
    // value, so the schedule can never be quietly narrowed with nothing saying so.
    val studentFilter by viewModel.studentFilter.collectAsState()
    val filterableStudents by viewModel.filterableStudents.collectAsState()
    // Why an empty schedule is empty — one answer for all three views, so they cannot disagree about
    // whether the tutor is looking at a quiet week or a narrowed one. See [ScheduleEmpty].
    val emptyReason by viewModel.emptyReason.collectAsState()

    /**
     * **A change of view is a new page, and a page starts at the top.**
     *
     * The three views share one `LazyColumn`, and switching between them changes what its *first item* is:
     * the month grid and the week strip are `item`s in the list, so «Месяц» prepends one and «День» takes
     * it away. A `LazyColumn` keeps the item that was on top on top — it re-finds it by key across a
     * content change, which is what stops a list jumping when a row is inserted above — so the grid was
     * inserted *above the viewport* and «Месяц» opened on the lesson list, with only the hairline under
     * the calendar showing where a month had been drawn off-screen.
     *
     * The scroll position is therefore per page, and this is the only thing in this screen that says so;
     * no view of the schedule has ever reset it. Note what does **not** fix it: the day's flow going back
     * to `Loadable.Loading` on a new key does replace the anchor row with a skeleton, but `stateIn`
     * conflates, so when the re-query answers inside the frame the composition never sees the `Loading`
     * at all and the anchor survives. Waiting is about *what is drawn*; this is about *where*.
     */
    val listState = rememberLazyListState()
    // Keyed on the view the list was last put at the top *for*, and saved with it: an effect keyed on the
    // mode alone also ran on every return to the screen, so scrolling the week to Friday, editing a lesson
    // and coming back landed on Monday again.
    var topFor by rememberSaveable { mutableStateOf(viewMode) }
    LaunchedEffect(viewMode) {
        if (viewMode != topFor) {
            listState.scrollToItem(0)
            topFor = viewMode
        }
    }

    // The sheet acts on the whole lesson in it, a group's included: its actions are the lesson's
    // time and status, and who is in one is changed on the lesson's own form.
    var sheetFor by remember { mutableStateOf<ScheduleRow?>(null) }
    var movingRow by remember { mutableStateOf<ScheduleRow?>(null) }
    var deletingRow by remember { mutableStateOf<ScheduleRow?>(null) }

    // A reminder's tap opens its lesson the way tapping the card does, once the day it is on has loaded.
    val askedSheet by viewModel.askedSheet.collectAsState()
    LaunchedEffect(askedSheet) {
        askedSheet?.let { row ->
            sheetFor = row
            viewModel.sheetShown()
        }
    }

    // These actions are tapped in a sheet that closes as they are tapped, so a write that failed would
    // otherwise read as one that had not been tapped at all.
    val messages = rememberMessages()
    val actionFailedMsg = stringResource(R.string.action_failed)
    val lessonGoneMsg = stringResource(R.string.class_gone)
    ShowMessagesFor(viewModel.events, messages) { event ->
        when (event) {
            ScheduleViewModel.Event.ACTION_FAILED -> actionFailedMsg
            ScheduleViewModel.Event.LESSON_GONE -> lessonGoneMsg
        }
    }

    val confirmFeedback = rememberConfirmFeedback()
    val rejectFeedback = rememberRejectFeedback()

    // **The app's one hard "no", felt.** One tutor, one lesson at a time: an overlapping hour is refused
    // outright rather than warned about, and until now that arrived as a dialog and nothing else. Keyed on
    // the conflict rather than fired from the action, because `timeConflict` is *state* — it has to survive
    // a rotation — so the buzz belongs to the refusal arriving and not to each recomposition that draws it.
    LaunchedEffect(timeConflict) {
        if (timeConflict != null) rejectFeedback()
    }

    // What the add button says on a day nothing can be booked into yet — and the step that fixes it.
    val cannotBookMsg = stringResource(R.string.schedule_need_setup)
    val addStudentLabel = stringResource(R.string.schedule_add_student)

    KlasScreen(
        title = stringResource(R.string.schedule_title),
        page = viewMode,
        messages = messages,
        actions = {
            // Offered only once there is somebody to narrow to — a menu whose every entry is «Все
            // ученики» is a control that cannot do anything.
            if (filterableStudents.isNotEmpty()) {
                FilterMenuAction(
                    name = stringResource(R.string.schedule_filter_student),
                    allLabel = stringResource(R.string.schedule_filter_all),
                    options = filterableStudents,
                    selected = filterableStudents.firstOrNull { it.id == studentFilter?.studentId },
                    optionLabel = { it.name },
                    onSelect = { viewModel.setStudentFilter(it?.id) },
                    narrowed = studentFilter != null,
                )
            }
        },
        floatingActionButton = {
            // **It stays put, and says why instead.** It was hidden while nothing could be booked, which
            // solved the wrong half of the problem: the version before that popped a toast naming what
            // was missing and left the tutor to go and find it — a control whose only behaviour is to
            // decline — so it was removed. But a button that comes and goes on the app's busiest screen
            // is its own defect: the tutor's thumb learns where it is, and on the one day it is absent
            // the schedule reads as broken rather than as unready.
            //
            // What was actually wrong with the toast is that it was a dead end. This is not: the message
            // carries «Добавить ученика», which is the step that unblocks booking, so the refusal and the
            // way out arrive together. The empty state below still offers the same step for a tutor who
            // reads the page instead of reaching for the button.
            FloatingActionButton(
                onClick = {
                    // Before the setup has been read there is no answer yet, and the tap waits for it.
                    when (canAddClass.valueOrNull()) {
                        true -> addClass()
                        false -> messages.show(cannotBookMsg, addStudentLabel, onAddStudent)
                        null -> Unit
                    }
                },
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.schedule_add_class))
            }
        },
    ) { modifier ->
        // **The view also turns by dragging the page sideways** — see [swipeBetweenSegments]. It reaches the
        // whole body, including the list, and the lesson cards keep their own swipe regardless: a child
        // consumes the drag before an ancestor is offered it, so a gesture begun on a lesson still marks
        // that lesson taught and one begun in the space around them turns the page. The two never arbitrate,
        // because they never meet.
        //
        // Which view, said out loud. It used to hide behind an unlabelled calendar icon that opened a menu —
        // two taps to switch, and the icon never told you which of the three you were in. A segmented control
        // is one tap and is its own label; the app already picks this way in Settings. The view, the filter
        // and the pager are painted with the bar, so they and it tint as one block when the agenda scrolls
        // beneath — see [SegmentedPage].
        SegmentedPage(
            options = ScheduleViewMode.entries,
            selected = viewMode,
            onSelect = viewModel::setViewMode,
            label = { mode -> stringResource(viewModeLabel(mode)) },
            modifier = modifier,
            header = {
                // **The chip is what makes the filter honest.** A narrowed schedule and a genuinely quiet
                // week look identical, so for as long as one student's lessons are what is on screen their
                // name stays above it — carrying its own way out, which is the difference between a filter
                // and a mode the tutor can get stuck in.
                studentFilter?.let { filter ->
                    FilterChipRow(
                        name = filter.name,
                        onClear = { viewModel.setStudentFilter(null) },
                    )
                }

                // The navigator stays pinned in every mode, so paging is always one tap away.
                when (viewMode) {
                    ScheduleViewMode.DAY -> {
                        DayNavigator(
                            // The day the list *is* — see [ScheduleContent.Day.date]. Paging still
                            // steps from the selected day; this only keeps the title and the rows
                            // turning over in the same frame.
                            date = dayContent.valueOrNull()?.date ?: date,
                            onPrevious = viewModel::goToPreviousDay,
                            onNext = viewModel::goToNextDay,
                            onToday = viewModel::goToToday,
                        )
                        // What the day comes to, under the day it names — the stepper's title is the date,
                        // so this is the one place in this view the summary can sit. No rule under it: the
                        // week's and month's divide a calendar grid from the agenda, and here there is no
                        // grid — the cards begin on surfaces of their own, and once they pass beneath, the
                        // tinted header is the edge ([ScreenHeader]).
                        DaySummaryLine(
                            summary = dayContent.valueOrNull()?.summary ?: DaySummary.NONE,
                            modifier = Modifier.padding(horizontal = Spacing.gutter),
                        )
                    }
                    ScheduleViewMode.WEEK -> WeekNavigator(
                        days = week,
                        onPrevious = viewModel::goToPreviousWeek,
                        onNext = viewModel::goToNextWeek,
                        onToday = viewModel::goToToday,
                    )
                    ScheduleViewMode.MONTH -> MonthNavigator(
                        month = month,
                        onPrevious = viewModel::goToPreviousMonth,
                        onNext = viewModel::goToNextMonth,
                        onToday = viewModel::goToToday,
                    )
                }
            },
        ) {
            // The grid scrolls with the classes rather than sitting above them: six week rows plus
            // a full day of classes doesn't fit a landscape phone.
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                // The gutter is the list's, not each row's — see [Spacing]. The calendar items in here
                // are laid out inside it too, which is why `MonthCalendar` and `WeekStrip` no longer
                // carry a narrower one of their own.
                contentPadding = FabListPadding,
                verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
            ) {
                when (viewMode) {
                    // The day list stands alone under its stepper.
                    ScheduleViewMode.DAY -> dayList(
                        view = ScheduleViewMode.DAY,
                        state = dayContent,
                        emptyReason = emptyReason,
                        nowMark = nowMark,
                        running = running,
                        onRowClick = { sheetFor = it },
                        onComplete = { viewModel.setStatus(it.session, LessonStatus.COMPLETED) },
                        onAddClass = addClass,
                        onAddStudent = onAddStudent,
                    )

                    // A whole week at once: the strip is an overview and a day picker for the add
                    // button, while the agenda below always shows every day that has classes.
                    ScheduleViewMode.WEEK -> {
                        item(key = "week-strip", contentType = "calendar") {
                            Column {
                                WeekStrip(
                                    days = week,
                                    loads = loads,
                                    selected = date,
                                    today = today,
                                    onSelect = viewModel::selectDate,
                                )
                                Spacer(Modifier.height(Spacing.itemGap))
                                HorizontalDivider()
                            }
                        }
                        val week = contentState.valueOrNull()
                        if (week == null) {
                            item(key = "loading") { LessonsSkeleton() }
                        } else if (week.sections.isEmpty()) {
                            item(key = "empty") {
                                // The same three answers the day list gets, from the same place — this
                                // branch used to weigh two of them itself and offer nothing either way.
                                ScheduleEmptyState(
                                    reason = emptyReason,
                                    whole = ScheduleEmptySpan.WEEK,
                                    onAddClass = addClass,
                                    onAddStudent = onAddStudent,
                                    minHeight = 200.dp,
                                )
                            }
                        } else {
                            week.sections.forEach { section ->
                                item(key = "day-${section.date}", contentType = "day-header") {
                                    DaySectionHeader(
                                        date = section.date,
                                        isSelected = section.date == date,
                                        summary = section.summary,
                                    )
                                }
                                items(section.rows, key = { lessonKey(ScheduleViewMode.WEEK, it) }) { row ->
                                    SwipeToCompleteCard(
                                        row = row,
                                        running = row.session.lessonId in running,
                                        onClick = { sheetFor = row },
                                        onComplete = {
                                            viewModel.setStatus(row.session, LessonStatus.COMPLETED)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // The calendar takes over from the day stepper: the grid is how you move
                    // between days, and the header below it names the day the list is showing.
                    ScheduleViewMode.MONTH -> {
                        item(key = "calendar", contentType = "calendar") {
                            Column {
                                MonthCalendar(
                                    grid = grid,
                                    loads = loads,
                                    selected = date,
                                    today = today,
                                    onSelect = viewModel::selectDate,
                                )
                                // Grid, rule, then the day — the order the week strip has, so the rule
                                // always divides the calendar from the agenda and the day's heading sits
                                // with the lessons it names, not above a line cutting it off from them.
                                Spacer(Modifier.height(Spacing.itemGap))
                                HorizontalDivider()
                                // The gap the week list's own spacing puts between its rule and heading.
                                Spacer(Modifier.height(Spacing.itemGap))
                                // Named by the day the list below *is*, which for the frames a tap on
                                // another day takes to read is still the previous one — see
                                // [ScheduleContent.Day.date]. The grid's ring has already moved.
                                SelectedDayHeader(
                                    date = dayContent.valueOrNull()?.date ?: date,
                                    summary = dayContent.valueOrNull()?.summary ?: DaySummary.NONE,
                                )
                            }
                        }
                        dayList(
                            view = ScheduleViewMode.MONTH,
                            state = dayContent,
                            emptyReason = emptyReason,
                            nowMark = nowMark,
                            running = running,
                            onRowClick = { sheetFor = it },
                            onComplete = { viewModel.setStatus(it.session, LessonStatus.COMPLETED) },
                            onAddClass = addClass,
                            onAddStudent = onAddStudent,
                        )
                    }
                }
            }
        }
    }

    sheetFor?.let { row ->
        // Keyed on the lesson, so it is one read per sheet and not one per recomposition of it.
        val homework by remember(row.session.lessonId) { viewModel.homeworkAt(row.session) }
            .collectAsState(initial = emptyList())
        val sheetState = rememberModalBottomSheetState()
        val sheetScope = rememberCoroutineScope()
        // An action closes the sheet the way a swipe down does — sliding — and only then does what comes
        // next (the date picker of a move, the question of a delete). Clearing `sheetFor` outright cut it
        // off mid-screen.
        fun closeSheet(then: () -> Unit = {}) {
            sheetScope.launch { sheetState.hide() }.invokeOnCompletion { sheetFor = null; then() }
        }
        ClassActionsSheet(
            row = row,
            running = row.session.lessonId in running,
            sheetState = sheetState,
            homework = homework,
            // A tick leaves the sheet open, unlike every other action on it: a group lesson can have
            // several pieces attached, and the sheet closing after the first would be a reopening each.
            onHomeworkDone = viewModel::setHomeworkDone,
            onEdit = { onEditClass(row.klass.id); sheetFor = null },
            // **The same «done» the swipe gives**, because it is the same act. Marking a lesson taught by
            // dragging its row both buzzed and popped; doing it from this sheet only popped, so the app felt
            // different depending on which way the tutor reached the one thing that bills a lesson. The pop
            // is `popsOnChange` on the status badge and arrives either way (`ui/common/Pop`).
            //
            // Only for «Проведено»: that is the act the swipe confirms, and a cancellation buzzing the same
            // «done» as a lesson taught said the wrong thing — the segment's own tick is enough for it.
            onStatus = { status ->
                if (status == LessonStatus.COMPLETED) confirmFeedback()
                viewModel.setStatus(row.session, status)
                closeSheet()
            },
            onMove = { closeSheet { movingRow = row } },
            onDelete = { closeSheet { deletingRow = row } },
            onDismiss = { sheetFor = null },
        )
    }

    movingRow?.let { row ->
        MoveClassFlow(
            start = row.klass.start,
            onMoved = { day, at -> viewModel.moveTo(row.session, day, at) },
            onDone = { movingRow = null },
        )
    }

    // A move, or a lesson being returned to the schedule, is refused when that hour is already
    // taken; either way, say which lesson holds it.
    timeConflict?.let { clash ->
        NoticeDialog(text = timeConflictText(clash), onDismiss = viewModel::dismissTimeConflict)
    }

    deletingRow?.let { row ->
        // Which warning applies is `row.deleteWarning`'s — see [DeleteWarning]. This `when` maps a named
        // case to a string and combines nothing, which is the difference between drawing and deciding: it
        // used to weigh the two facts itself, four ways, with the picking untestable in here.
        val warning = row.deleteWarning
        val count = warning.classes
        val confirmText = when (warning.kind) {
            DeleteWarning.Kind.BILLED_GROUP ->
                pluralStringResource(R.plurals.class_delete_group_completed_confirm, count, count)
            DeleteWarning.Kind.BILLED -> stringResource(R.string.class_delete_completed_confirm)
            DeleteWarning.Kind.GROUP ->
                pluralStringResource(R.plurals.class_delete_group_confirm, count, count)
            DeleteWarning.Kind.PLAIN -> stringResource(R.string.class_delete_confirm)
        }
        ConfirmDialog(
            text = confirmText,
            destructive = true,
            onConfirm = { viewModel.delete(row.session) },
            onDismiss = { deletingRow = null },
        )
    }
}

/**
 * «Занято: Мария Петрова 14:00 – 15:30» — which lesson holds the hour a booking or a move asked for. Said by
 * the booking form under its time and by the schedule's refusal dialog, so the two name a clash one way.
 */
@Composable
internal fun timeConflictText(conflict: TimeConflict): String = stringResource(
    R.string.class_time_conflict,
    conflict.who,
    "${conflict.slot.start.timeText()} – ${conflict.slot.end.timeText()}",
)

/** Whether an empty stretch of schedule is one day or a whole week — all that differs in the wording. */
private enum class ScheduleEmptySpan { DAY, WEEK }

/**
 * What the schedule says when it has nothing to show — the **words** for a [ScheduleEmpty], and nothing
 * else. Which of the three applies is the view model's ([ScheduleViewModel.emptyReason]); a day whose
 * lessons have not been read yet is not an empty day at all, and that is `Loadable`'s.
 *
 * One composable for all three views. The day and month views used to weigh three cases here and the week
 * view two, twenty lines apart, and they had already drifted: the week never grew the no-students arm, so
 * a tutor with nothing set up met «Добавить ученика» on «День» and a dead end on «Неделя».
 */
@Composable
private fun ScheduleEmptyState(
    reason: Loadable<ScheduleEmpty>,
    whole: ScheduleEmptySpan,
    onAddClass: () -> Unit,
    onAddStudent: () -> Unit,
    minHeight: Dp,
) {
    val modifier = Modifier.heightIn(min = minHeight)
    // Which empty this is waits on whether anything can be booked — see [ScheduleViewModel.emptyReason].
    val known = when (reason) {
        Loadable.Loading -> {
            LessonsSkeleton()
            return
        }
        is Loadable.Ready -> reason.value
    }
    when (known) {
        ScheduleEmpty.FILTERED -> EmptyState(
            title = stringResource(R.string.schedule_filter_empty),
            detail = stringResource(R.string.schedule_filter_empty_detail),
            icon = Icons.Outlined.EventBusy,
            modifier = modifier,
        )

        ScheduleEmpty.QUIET -> EmptyState(
            title = stringResource(
                when (whole) {
                    ScheduleEmptySpan.DAY -> R.string.schedule_empty
                    ScheduleEmptySpan.WEEK -> R.string.schedule_week_empty
                },
            ),
            detail = stringResource(R.string.schedule_empty_detail),
            icon = Icons.Outlined.EventBusy,
            actionLabel = stringResource(R.string.schedule_add_class),
            onAction = onAddClass,
            modifier = modifier,
        )

        ScheduleEmpty.NEEDS_STUDENT -> EmptyState(
            title = stringResource(R.string.schedule_empty_no_setup),
            detail = stringResource(R.string.schedule_empty_no_setup_detail),
            icon = Icons.Outlined.PersonAddAlt,
            actionLabel = stringResource(R.string.schedule_add_student),
            onAction = onAddStudent,
            modifier = modifier,
        )
    }
}

/** The selected day's classes, or an empty state. Shared by the day and month views. */
private fun LazyListScope.dayList(
    view: ScheduleViewMode,
    state: Loadable<ScheduleContent.Day>,
    emptyReason: Loadable<ScheduleEmpty>,
    nowMark: Int?,
    running: Set<Long>,
    onRowClick: (ScheduleRow) -> Unit,
    onComplete: (ScheduleRow) -> Unit,
    onAddClass: () -> Unit,
    onAddStudent: () -> Unit,
) {
    val day = when (state) {
        Loadable.Loading -> {
            item(key = "loading") { LessonsSkeleton() }
            return
        }
        is Loadable.Ready -> state.value
    }
    val rows = day.rows
    if (rows.isEmpty()) {
        item(key = "empty") {
            ScheduleEmptyState(
                reason = emptyReason,
                whole = ScheduleEmptySpan.DAY,
                onAddClass = onAddClass,
                onAddStudent = onAddStudent,
                minHeight = 260.dp,
            )
        }
    } else {
        // A row at a time rather than `items(rows)`, so the mark can be its own item *between* two
        // lessons. It has to be one: `nowMark == rows.size` means the whole day is behind us, and there
        // is no card left to draw it above — and an item with two roots would take [animateInPlace] off
        // the card, which is what keeps a re-sorted list from teleporting.
        rows.forEachIndexed { index, row ->
            if (index == nowMark) item(key = nowKey(view, day.date)) { NowLine(animateInPlace()) }
            item(key = lessonKey(view, row, day.date), contentType = "lesson") {
                SwipeToCompleteCard(
                    row = row,
                    running = row.session.lessonId in running,
                    onClick = { onRowClick(row) },
                    onComplete = { onComplete(row) },
                )
            }
        }
        if (nowMark == rows.size) item(key = nowKey(view, day.date)) { NowLine(animateInPlace()) }
    }
}

/**
 * A lesson's list key: the lesson, **on this page** — the view, and for the day and month views the day.
 *
 * The id alone was the same key in all three views, so Compose read a change of view or of day as the
 * same lessons *moving*: switching «День» to «Месяц» slid every card down across the month grid as it
 * was drawn, with the ones leaving fading out over it. A new page is a new set of items, swapped in
 * whole; only a change *within* a page — a lesson moved to another hour — is a move, and slides.
 */
private fun lessonKey(view: ScheduleViewMode, row: ScheduleRow, day: LocalDate? = null): String =
    "$view/${day ?: ""}/${row.klass.id}"

/**
 * The mark's list key — one per page, for [lessonKey]'s reason. Within the page it is constant, so the
 * item keeps its identity as its *position* changes: the stable key is what makes the move an existing
 * item changing place rather than one item being removed and another added, and [animateInPlace] at both
 * call sites is what makes that visible — the mark slides past a lesson as it begins instead of blinking
 * from one gap to the next. One per page is also what keeps it unique, and a duplicate key crashes
 * composition.
 */
private fun nowKey(view: ScheduleViewMode, day: LocalDate): String = "$view/$day/now"

/**
 * Lesson cards that have not arrived yet, at the gutter and gap the real ones use — so the first real
 * frame changes colour rather than layout.
 */
@Composable
private fun LessonsSkeleton() {
    ListSkeleton(rows = 3, contentPadding = PaddingValues(0.dp))
}

private fun viewModeLabel(mode: ScheduleViewMode): Int = when (mode) {
    ScheduleViewMode.DAY -> R.string.schedule_view_day
    ScheduleViewMode.WEEK -> R.string.schedule_view_week
    ScheduleViewMode.MONTH -> R.string.schedule_view_month
}
