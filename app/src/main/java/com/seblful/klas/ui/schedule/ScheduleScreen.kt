package com.seblful.klas.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.seblful.klas.R
import com.seblful.klas.domain.DaySummary
import com.seblful.klas.domain.LessonKind
import com.seblful.klas.domain.NowMarker
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.format.daySummaryText
import com.seblful.klas.format.formatDay
import com.seblful.klas.format.formatMonth
import com.seblful.klas.format.formatWeekRange
import com.seblful.klas.format.toLocalDate
import com.seblful.klas.format.toLocalTime
import com.seblful.klas.ui.common.FilterChipRow
import com.seblful.klas.ui.common.FilterMenuAction
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.ContentCard
import com.seblful.klas.ui.common.DatePickerModal
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.LessonKindIcon
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.SegmentedChoice
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.StatusBadge
import com.seblful.klas.ui.common.SwipeToConfirmBox
import com.seblful.klas.ui.common.TimePickerModal
import com.seblful.klas.ui.common.armedPop
import com.seblful.klas.ui.common.classModeLabel
import com.seblful.klas.ui.common.classStatusLabel
import com.seblful.klas.ui.common.lessonBadgeColor
import com.seblful.klas.ui.common.lessonBadgeLabel
import com.seblful.klas.ui.common.map
import com.seblful.klas.ui.common.popsOnChange
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.rememberRejectFeedback
import com.seblful.klas.ui.common.rememberConfirmFeedback
import com.seblful.klas.ui.common.swipeBetweenSegments
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.common.valueOrNull
import com.seblful.klas.ui.theme.klasColors
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onAddClass: () -> Unit,
    onEditClass: (Long) -> Unit,
    onAddStudent: () -> Unit,
    /**
     * Bumped when the evening digest's tray entry asks for today — see `AppRoot`.
     *
     * A ticket rather than a flag the screen has to clear: this screen is the graph's start destination
     * and its view model outlives every arrival at it, so "put it on today" has to be an *event*. Zero
     * is "nobody has asked", which is the launcher's ordinary case and every recomposition after one.
     */
    openTodayTicket: Int = 0,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    LaunchedEffect(openTodayTicket) { if (openTodayTicket > 0) viewModel.goToToday() }

    // One state for the body: only the active view's lessons are assembled (see ScheduleContent).
    val contentState by viewModel.content.collectAsState()
    // **Narrowed to the day rather than read for its day half.** The state can legitimately hold the
    // *week's* content while this composition is drawing a day — switching view re-keys the query and the
    // previous answer is still there — and `ScheduleContent` no longer lets one view answer the other's
    // question with an empty list. A mismatch is `Loading`, which draws the skeleton. See
    // [ScheduleContent.day].
    val dayContent = contentState.day()

    val date by viewModel.selectedDate.collectAsState()
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
    LaunchedEffect(viewMode) { listState.scrollToItem(0) }

    // The sheet acts on the whole lesson in it, a group's included: its actions are the lesson's
    // time and status, and who is in one is changed on the lesson's own form.
    var sheetFor by remember { mutableStateOf<ScheduleRow?>(null) }
    var movingRow by remember { mutableStateOf<ScheduleRow?>(null) }
    var deletingRow by remember { mutableStateOf<ScheduleRow?>(null) }

    // These actions are tapped in a sheet that closes as they are tapped, so a write that failed would
    // otherwise read as one that had not been tapped at all.
    val messages = rememberMessages()
    val actionFailedMsg = stringResource(R.string.action_failed)
    LaunchedEffect(Unit) {
        viewModel.events.stream.collect { messages.show(actionFailedMsg) }
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
                onClick = { if (canAddClass) onAddClass() else messages.show(cannotBookMsg, addStudentLabel, onAddStudent) },
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.action_add))
            }
        },
    ) { modifier ->
        // **The view also turns by dragging the page sideways** — see [swipeBetweenSegments]. It reaches the
        // whole body, including the list, and the lesson cards keep their own swipe regardless: a child
        // consumes the drag before an ancestor is offered it, so a gesture begun on a lesson still marks
        // that lesson taught and one begun in the space around them turns the page. The two never arbitrate,
        // because they never meet.
        Column(modifier.swipeBetweenSegments(ScheduleViewMode.entries, viewMode, viewModel::setViewMode)) {
            // Which view, said out loud. It used to hide behind an unlabelled calendar icon that
            // opened a menu — two taps to switch, and the icon never told you which of the three you
            // were in. A segmented control is one tap and is its own label; the app already picks
            // this way in Settings.
            ViewModeSelector(selected = viewMode, onSelect = viewModel::setViewMode)

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
                        date = date,
                        onPrevious = viewModel::goToPreviousDay,
                        onNext = viewModel::goToNextDay,
                        onToday = viewModel::goToToday,
                    )
                    // What the day comes to, under the day it names — the stepper's title is the date,
                    // so this is the one place in this view the summary can sit.
                    DaySummaryLine(
                        summary = dayContent.valueOrNull()?.summary ?: DaySummary.NONE,
                        modifier = Modifier.padding(horizontal = Spacing.gutter),
                    )
                    // Inset to the gutter, like the week and month dividers — those sit inside the list
                    // below and so pick it up from its `contentPadding`. This one is outside the list,
                    // so it has to say so, or switching view mode changes the divider's width.
                    HorizontalDivider(
                        Modifier.padding(top = Spacing.tight, start = Spacing.gutter, end = Spacing.gutter),
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
            // The grid scrolls with the classes rather than sitting above them: six week rows plus
            // a full day of classes doesn't fit a landscape phone.
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                // The gutter is the list's, not each row's — see [Spacing]. The calendar items in here
                // are laid out inside it too, which is why `MonthCalendar` and `WeekStrip` no longer
                // carry a narrower one of their own.
                contentPadding = PaddingValues(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    top = Spacing.gutter,
                    bottom = FabClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
            ) {
                when (viewMode) {
                    // The day list stands alone under its stepper.
                    ScheduleViewMode.DAY -> dayList(
                        state = dayContent.map { it.rows },
                        emptyReason = emptyReason,
                        nowMark = nowMark,
                        running = running,
                        onRowClick = { sheetFor = it },
                        onComplete = { viewModel.setStatus(it.session, LessonStatus.COMPLETED) },
                        onAddClass = onAddClass,
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
                        val week = contentState.week().valueOrNull()
                        if (week == null) {
                            item(key = "loading") { LessonsSkeleton() }
                        } else if (week.sections.isEmpty()) {
                            item(key = "empty") {
                                // The same three answers the day list gets, from the same place — this
                                // branch used to weigh two of them itself and offer nothing either way.
                                ScheduleEmptyState(
                                    reason = emptyReason,
                                    whole = ScheduleEmptySpan.WEEK,
                                    onAddClass = onAddClass,
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
                                items(section.rows, key = { it.klass.id }) { row ->
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
                                Spacer(Modifier.height(Spacing.itemGap))
                                SelectedDayHeader(
                                    date = date,
                                    summary = dayContent.valueOrNull()?.summary ?: DaySummary.NONE,
                                )
                                HorizontalDivider()
                            }
                        }
                        dayList(
                            state = dayContent.map { it.rows },
                            emptyReason = emptyReason,
                            nowMark = nowMark,
                            running = running,
                            onRowClick = { sheetFor = it },
                            onComplete = { viewModel.setStatus(it.session, LessonStatus.COMPLETED) },
                            onAddClass = onAddClass,
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
        ClassActionsSheet(
            row = row,
            running = row.session.lessonId in running,
            sheetState = rememberModalBottomSheetState(),
            homework = homework,
            // A tick leaves the sheet open, unlike every other action on it: a group lesson can have
            // several pieces attached, and the sheet closing after the first would be a reopening each.
            onHomeworkDone = viewModel::setHomeworkDone,
            onEdit = { onEditClass(row.klass.id); sheetFor = null },
            // **The same «done» the swipe gives**, because it is the same act. Marking a lesson taught by
            // dragging its row both buzzed and popped; doing it from this sheet only popped, so the app felt
            // different depending on which way the tutor reached the one thing that bills a lesson. The pop
            // is `popsOnChange` on the status badge and arrives either way (`ui/common/Pop`).
            onStatus = { confirmFeedback(); viewModel.setStatus(row.session, it); sheetFor = null },
            onMove = { movingRow = row; sheetFor = null },
            onDelete = { deletingRow = row; sheetFor = null },
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
        AlertDialog(
            onDismissRequest = viewModel::dismissTimeConflict,
            text = {
                Text(
                    stringResource(
                        R.string.class_time_conflict,
                        clash.who,
                        "${clash.slot.start.timeText()} – ${clash.slot.end.timeText()}",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissTimeConflict) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
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
            onConfirm = { viewModel.delete(row.session) },
            onDismiss = { deletingRow = null },
        )
    }
}

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
    reason: ScheduleEmpty,
    whole: ScheduleEmptySpan,
    onAddClass: () -> Unit,
    onAddStudent: () -> Unit,
    minHeight: Dp,
) {
    val modifier = Modifier.heightIn(min = minHeight)
    when (reason) {
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
    state: Loadable<List<ScheduleRow>>,
    emptyReason: ScheduleEmpty,
    nowMark: Int?,
    running: Set<Long>,
    onRowClick: (ScheduleRow) -> Unit,
    onComplete: (ScheduleRow) -> Unit,
    onAddClass: () -> Unit,
    onAddStudent: () -> Unit,
) {
    val rows = when (state) {
        Loadable.Loading -> {
            item(key = "loading") { LessonsSkeleton() }
            return
        }
        is Loadable.Ready -> state.value
    }
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
        // is no card left to draw it above — and an item with two roots would take `animateItem()` off
        // the card, which is what keeps a re-sorted list from teleporting.
        rows.forEachIndexed { index, row ->
            if (index == nowMark) item(key = NowKey) { NowLine(Modifier.animateItem()) }
            item(key = row.klass.id, contentType = "lesson") {
                SwipeToCompleteCard(
                    row = row,
                    running = row.session.lessonId in running,
                    onClick = { onRowClick(row) },
                    onComplete = { onComplete(row) },
                )
            }
        }
        if (nowMark == rows.size) item(key = NowKey) { NowLine(Modifier.animateItem()) }
    }
}

/**
 * The mark's list key. A constant, so the item keeps its identity as its *position* changes: the stable
 * key is what makes the move an existing item changing place rather than one item being removed and
 * another added, and `animateItem()` at both call sites is what makes that visible — the mark slides past
 * a lesson as it begins instead of blinking from one gap to the next. A constant is also what keeps it
 * unique, and a duplicate key crashes composition.
 */
private const val NowKey = "now"

/**
 * Lesson cards that have not arrived yet, at the gutter and gap the real ones use — so the first real
 * frame changes colour rather than layout.
 */
@Composable
private fun LessonsSkeleton() {
    ListSkeleton(rows = 3, contentPadding = PaddingValues(0.dp))
}

/** Chooses between the day, week and month views. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeSelector(selected: ScheduleViewMode, onSelect: (ScheduleViewMode) -> Unit) {
    SegmentedChoice(
        options = ScheduleViewMode.entries,
        selected = selected,
        onSelect = onSelect,
        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.tight),
        label = { mode -> stringResource(viewModeLabel(mode)) },
    )
}

private fun viewModeLabel(mode: ScheduleViewMode): Int = when (mode) {
    ScheduleViewMode.DAY -> R.string.schedule_view_day
    ScheduleViewMode.WEEK -> R.string.schedule_view_week
    ScheduleViewMode.MONTH -> R.string.schedule_view_month
}

/**
 * A lesson card that can be marked **completed** with one swipe.
 *
 * Marking a lesson done is the highest-frequency action in the product and cost two taps and a sheet,
 * every time, forever. A gesture on the row makes it one — and the row is exactly where the tutor is
 * already looking, having just taught it.
 *
 * Three deliberate limits:
 * - **Only where completing is legal.** A lesson already completed, or one whose members are in mixed
 *   states, gets a plain card: [ScheduleRow.canSwipeComplete] decides, from the same rule the sheet's
 *   actions are built from, so the gesture can never do something the menu would refuse.
 * - **It is taken back by sliding home.** The phone ticks at the mark and the lesson is marked only on
 *   the lift, so a swipe begun by accident — or aimed at the row below — costs a thumb moving back and
 *   nothing else. [SwipeToConfirmBox] owns that whole contract.
 * - **One direction, and it never sticks.** The row springs back rather than staying dismissed —
 *   nothing is being removed, and the status badge flipping to «Проведено» is the confirmation. A box
 *   left in its dismissed state would be a row hidden behind its own background until recomposed.
 */
@Composable
private fun LazyItemScope.SwipeToCompleteCard(
    row: ScheduleRow,
    running: Boolean,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!row.canSwipeComplete) {
        ScheduleCard(row, running, onClick, modifier.animateItem())
        return
    }
    SwipeToConfirmBox(
        onConfirm = onComplete,
        modifier = modifier.animateItem(),
        reveal = { armed ->
            Row(
                Modifier
                    .fillMaxSize()
                    // **Green, the container tone of the badge this gesture produces.** It was
                    // `tertiaryContainer`, which in this palette is a pale cyan — so the swipe promised
                    // «Проведено» in a blue, the colour a lesson *still to come* is written in, and then
                    // handed back a badge of another colour entirely. Now the panel and the badge are one
                    // green: what the reveal shows is what the row will say (see `classStatusColor`).
                    .background(klasColors.successContainer, MaterialTheme.shapes.medium)
                    .padding(horizontal = SheetGutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The tick's visible half: the check grows at the mark and stays grown while letting go
                // would act. It sits at the leading edge, which the thumb has already moved off.
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.class_mark_completed),
                    tint = klasColors.onSuccessContainer,
                    modifier = Modifier.armedPop(armed),
                )
                Spacer(Modifier.width(Spacing.inline))
                Text(
                    stringResource(R.string.class_status_completed),
                    style = MaterialTheme.typography.labelLarge,
                    color = klasColors.onSuccessContainer,
                )
            }
        },
    ) {
        ScheduleCard(row, running, onClick)
    }
}

@Composable
private fun ScheduleCard(
    row: ScheduleRow,
    running: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Whether the card recedes is the row's own answer — see [ScheduleRow.recedes]: a day with
    // four lessons and three cancellations must not look like a full day.
    val cancelled = row.recedes
    ContentCard(onClick = onClick, modifier = modifier, dimmed = cancelled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.klass.start.timeText(),
                style = MaterialTheme.typography.titleMedium,
                color = if (cancelled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
                // Struck through, so the hour reads as one that was given back.
                textDecoration = if (cancelled) TextDecoration.LineThrough else null,
            )
            // [Spacing.itemGap] rather than [Spacing.leading]: the time and the body are two peers
            // in the row, and every dp between them is width the lesson's own name does not get —
            // that name being the one thing on the card that can run long.
            Spacer(Modifier.width(Spacing.itemGap))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Both kinds are marked, not just the group ones: an icon that appears only
                    // sometimes reads as "this lesson has something extra", which leaves an individual
                    // lesson looking like a lesson with a missing icon rather than an individual one.
                    LessonKindIcon(LessonKind.of(row.klass), Modifier.padding(end = Spacing.inline))
                    // **One line.** A name long enough to wrap — «Группа Биология» — used to break
                    // across two, and because the time on the left is centred against the whole card
                    // it then sat beside the *second* line with a hole above it. Rows also stopped
                    // being a uniform height, which is what a scannable list is for. The weight is
                    // what makes the ellipsis land at the badge rather than the text pushing into it.
                    Text(
                        row.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    "${row.subjectName} · ${classModeLabel(row.klass.mode)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.isGroup) {
                    Text(
                        // The roster is the least important line on the card and the most likely to
                        // run long, so it is the one allowed to be cut.
                        row.members.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(Spacing.inline))
            SessionStatusBadge(row, running)
        }
    }
}

/**
 * The lesson's status — one of the three, whatever its members have been corrected to individually.
 * Which of them missed it is the lesson's own roster to say, not this badge's.
 *
 * **It pops when it changes**, because it is the answer to a swipe the tutor has just let go of — the row
 * springs home under their thumb and the badge is where the outcome lands, a moment later and 200dp away
 * (see [popsOnChange], which is silent for a badge that merely scrolled into view). It pops for a status
 * corrected from the sheet too: the same fact changed under them, by a different route.
 */
@Composable
private fun SessionStatusBadge(row: ScheduleRow, running: Boolean) {
    val status = row.session.status
    StatusBadge(
        text = lessonBadgeLabel(status, running),
        color = lessonBadgeColor(status, running),
        // Keyed on **what the badge says**, not on the status alone: a lesson beginning changes the word
        // without changing the status, and a pill that changed silently at four o'clock would be the one
        // moment in the app where a state change is not announced.
        modifier = Modifier.popsOnChange(status to running),
    )
}

@Composable
private fun DayNavigator(
    date: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    PageNavigator(
        title = date.formatDay(),
        previousDescription = stringResource(R.string.schedule_previous_day),
        nextDescription = stringResource(R.string.schedule_next_day),
        onPrevious = onPrevious,
        onNext = onNext,
        onToday = onToday,
    )
}

@Composable
private fun WeekNavigator(
    days: List<LocalDate>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    PageNavigator(
        title = formatWeekRange(days.first(), days.last()),
        previousDescription = stringResource(R.string.schedule_previous_week),
        nextDescription = stringResource(R.string.schedule_next_week),
        onPrevious = onPrevious,
        onNext = onNext,
        onToday = onToday,
    )
}

@Composable
private fun MonthNavigator(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    PageNavigator(
        title = formatMonth(month),
        previousDescription = stringResource(R.string.schedule_previous_month),
        nextDescription = stringResource(R.string.schedule_next_month),
        onPrevious = onPrevious,
        onNext = onNext,
        onToday = onToday,
    )
}

/** ‹ title › with a "jump to today" button — shared by the day stepper and the month pager. */
@Composable
private fun PageNavigator(
    title: String,
    previousDescription: String,
    nextDescription: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.inline),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) { Icon(Icons.Filled.ChevronLeft, previousDescription) }
        // The title takes the space the arrows leave: a week range spanning two long month names
        // is far wider than "Июль 2026", and must ellipsize rather than push the arrows off-screen.
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onToday) {
                Icon(Icons.Filled.Today, stringResource(R.string.schedule_today))
            }
        }
        IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, nextDescription) }
    }
}

/** Names the day the list below belongs to, since the calendar's title is the month. */
@Composable
private fun SelectedDayHeader(date: LocalDate, summary: DaySummary) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.tight)) {
        Text(
            text = date.formatDay(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DaySummaryLine(summary)
    }
}

/**
 * Divides one day's classes from the next in the week agenda. The selected day is tinted so the
 * strip's selection stays traceable in the list below it.
 */
@Composable
private fun DaySectionHeader(date: LocalDate, isSelected: Boolean, summary: DaySummary) {
    Column(
        // One extra [Spacing.itemGap] above, so the gap that opens *before* a header is a section
        // break while the header still sits close to the day it names. The list's uniform `spacedBy`
        // cannot say that on its own.
        Modifier.fillMaxWidth().padding(top = Spacing.itemGap),
    ) {
        Text(
            text = date.formatDay(),
            style = MaterialTheme.typography.titleSmall,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        DaySummaryLine(summary)
    }
}

/**
 * What the day comes to: «4 занятия · 5 ч · 240 BYN».
 *
 * The screen listed a day's lessons and left the tutor to add them up — including the money, which is
 * what the app is for. It goes wherever a day is *named*: under the stepper in the day view, under the
 * selected-day heading in the month view, and on each heading of the week agenda. One rule, no
 * exceptions to remember.
 *
 * The arithmetic is [DaySummary]'s and the **wording is [daySummaryText]'s** — shared with the morning
 * digest, which summarises the same day into the notification tray. Two copies of this sentence could
 * add one day up two ways, and both numbers would look right. A **cancelled** lesson is named apart
 * rather than folded in: it counts towards nothing, and a day of four lessons and one cancellation must
 * not read as five.
 */
@Composable
private fun DaySummaryLine(summary: DaySummary, modifier: Modifier = Modifier) {
    // Null is "nothing to say about this day" — the empty state below already says it at length, and a
    // «0 занятий · 0 ч» above that would be the same news twice in a colder voice.
    val text = daySummaryText(
        LocalContext.current.resources,
        summary,
        LocalCurrency.current,
    ) ?: return

    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = Spacing.tight),
    )
}

/**
 * The hairline that says **where now is** in today's list.
 *
 * A day is a column of times with nothing marking which of them have been — so the next lesson, the one
 * row the tutor can still act on, has to be found by reading the clock and then reading the list. The
 * mark is drawn only on today ([NowMarker] decides, not this) and it moves as the clock does.
 *
 * A dot and a rule in the brand colour, with the word: a bare tinted line among lesson cards would read
 * as a divider, and the label is also the only part of this a screen reader can announce.
 */
@Composable
private fun NowLine(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(NowDotSize)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            stringResource(R.string.schedule_now),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.inline),
        )
        HorizontalDivider(
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The mark's dot: a measurement of a drawn shape, not a gap — see `Spacing`. */
private val NowDotSize = 6.dp

/**
 * What can be done to the lesson that was tapped: change its status, tick off the homework attached to it,
 * edit it, move it, delete it.
 *
 * **Status comes first and is a control, not a list of verbs.** It is the most common reason the sheet
 * is open by a wide margin, and it used to be one or two ordinary rows in the middle of a flat menu,
 * indistinguishable from «Перенести» — so the sheet answered "what can I do" without answering "what
 * is this lesson now".
 *
 * Every action is the **whole lesson's**, a group's included. The sheet deliberately does not list
 * the members as a roster: who a lesson covers is picked while booking it, on the form, and a menu that
 * changes a lesson's status is not where a roster belongs. The one place it names one is the homework, when
 * a member's own is attached to a group's lesson — see [SheetHomework.who].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassActionsSheet(
    row: ScheduleRow,
    running: Boolean,
    sheetState: androidx.compose.material3.SheetState,
    homework: List<SheetHomework>,
    onHomeworkDone: (Homework, Boolean) -> Unit,
    onEdit: () -> Unit,
    onStatus: (LessonStatus) -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val klass = row.klass
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = SheetGutter)) {
            // Header: which lesson these actions apply to.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = SheetGutter, vertical = Spacing.tight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LessonKindIcon(LessonKind.of(klass), Modifier.padding(end = Spacing.inline))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${klass.start.timeText()} · ${row.title}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${row.subjectName} · ${classModeLabel(klass.mode)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SessionStatusBadge(row, running)
            }

            // The lesson's state, as a state: every status it can be in, with the current one shown
            // selected. Offering only the transitions (as a list of actions did) meant the sheet never
            // said what the lesson *is* — the tutor had to infer it from which verbs were absent.
            StatusSelector(row = row, onStatus = onStatus)

            // What was set for this lesson, **with its ticks on the sheet**: the start of a lesson is when
            // the tutor asks for it, and this is the screen they are on.
            if (homework.isNotEmpty()) AttachedHomework(homework, onHomeworkDone)

            HorizontalDivider(Modifier.padding(vertical = Spacing.inline))

            // Editing details (student/subject/price) is allowed in any status; saving a completed
            // class re-bills it so its recognized income tracks the edit (see ClassRules.rebill).
            // A group lesson edits as a whole — every member's class moves and re-bills together.
            SheetAction(stringResource(R.string.action_edit), Icons.Filled.Edit, onClick = onEdit)
            // Move (reschedule) is offered for any status: moving a lesson to the future re-plans it,
            // moving it to the past just corrects its recorded time. A group lesson moves as one.
            SheetAction(stringResource(R.string.class_move), Icons.Filled.EditCalendar, onClick = onMove)
            // Delete is offered for any status. Deleting a completed class removes its recognized
            // income (the confirm dialog warns about this); the repository deletes the calendar
            // event and refreshes reminders regardless of status.
            HorizontalDivider(Modifier.padding(vertical = Spacing.inline))
            SheetAction(
                stringResource(R.string.action_delete),
                Icons.Filled.DeleteOutline,
                onClick = onDelete,
                destructive = true,
            )
        }
    }
}

/**
 * The homework attached to this lesson (`HomeworkRules.attachedToLesson`), each with a tick — the same
 * «done» the tab's swipe gives, kept on screen once given so a wrong tap is taken back where it was made.
 * Capped at a handful of lines each: the sheet is a set of actions, and the tab has the whole text.
 */
@Composable
private fun AttachedHomework(items: List<SheetHomework>, onDone: (Homework, Boolean) -> Unit) {
    Column(Modifier.padding(horizontal = SheetGutter, vertical = Spacing.inline)) {
        Text(
            stringResource(R.string.homework_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        items.forEach { item ->
            val done = item.homework.isDone
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = done, role = Role.Checkbox) { onDone(item.homework, it) }
                    .padding(vertical = Spacing.tight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // No handler of its own: the row is the target, so the whole line ticks it.
                Checkbox(checked = done, onCheckedChange = null)
                Column(Modifier.weight(1f).padding(start = Spacing.inline)) {
                    item.who?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                    Text(
                        item.homework.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        maxLines = SheetHomeworkLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** How much of one piece of homework the actions sheet shows before the tab is the way to read the rest. */
private const val SheetHomeworkLines = 4

/**
 * The lesson's status as a segmented control.
 *
 * Shows all three, with the lesson's own selected — so the sheet states the fact as well as offering
 * the change. There is always one selected, [ClassSession.status] being one of the three even for a
 * lesson whose members were corrected apart; the segments it *enables* are still the union of what
 * those members can each move to ([GroupRules.sessionTransitions]).
 *
 * **The current status is enabled and does nothing.** [ClassRules.availableTransitions] excludes the
 * status a lesson is already in — quite rightly, it drove a menu of *actions* — so gating `enabled`
 * on it alone greyed out the one segment that is selected, every single time. The control then read
 * as broken rather than as informative. Being tappable-but-inert is how a segmented control's chosen
 * option behaves anyway; what stays disabled is the genuinely unreachable transition, which is real
 * information (a completed lesson can be cancelled but not un-completed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusSelector(row: ScheduleRow, onStatus: (LessonStatus) -> Unit) {
    val current = row.session.status
    val allowed = row.statusChoices
    Column(Modifier.padding(horizontal = SheetGutter, vertical = Spacing.inline)) {
        Text(
            stringResource(R.string.class_status_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.inline),
        )
        SegmentedChoice(
            options = LessonStatus.entries,
            selected = current,
            onSelect = onStatus,
            // Which statuses this lesson may move to is `GroupRules.sessionTransitions`', arriving as
            // `row.statusChoices` — the same rule the swipe is offered from.
            enabled = { status -> status in allowed },
            label = { status -> classStatusLabel(status) },
        )
    }
}

/**
 * How much empty list is left under the last lesson, so the add button never covers one. A size, not a
 * gap — it is measured against the FAB rather than set to the app's rhythm, which is why it is here and
 * not in `Spacing`.
 */
private val FabClearance = 88.dp

/** A bottom sheet's own gutter, wider than the screen's: Material insets sheet content further in. */
private val SheetGutter = 24.dp

@Composable
private fun SheetAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = SheetGutter, vertical = Spacing.itemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(Spacing.leading))
        Text(label, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Picks a new date, then a new time, and reports **the two the tutor chose**.
 *
 * It used to combine them here — `date.atTime(time).atZone(ZoneId.systemDefault()).toInstant()` — which put
 * a wall-clock-to-instant conversion, and a third definition of the app's zone, inside a composable on the
 * path that changes when a lesson happens. `ScheduleViewModel.moveTo` does it now, against the injected
 * clock's zone; see `domain/PaymentDate` for the same argument made about the path that records money.
 */
@Composable
private fun MoveClassFlow(
    start: java.time.Instant,
    onMoved: (LocalDate, java.time.LocalTime) -> Unit,
    onDone: () -> Unit,
) {
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    // Held in a local so the time step can use the date without asserting it: `pickedDate` is a `by`
    // delegate over mutable state, which Kotlin re-reads on each access and so cannot smart-cast, and
    // the `null` branch below is precisely what establishes that it is set.
    val date = pickedDate
    if (date == null) {
        DatePickerModal(
            initial = start.toLocalDate(),
            onConfirm = { pickedDate = it },
            onDismiss = onDone,
        )
    } else {
        TimePickerModal(
            initial = start.toLocalTime(),
            onConfirm = { time ->
                onMoved(date, time)
                onDone()
            },
            onDismiss = onDone,
        )
    }
}
