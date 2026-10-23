package com.seblful.klas.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seblful.klas.R
import com.seblful.klas.domain.lesson.DeleteWarning
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.schedule.DaySummary
import com.seblful.klas.domain.schedule.ScheduleEmpty
import com.seblful.klas.domain.schedule.TimeConflict
import com.seblful.klas.ui.common.ConfirmDialog
import com.seblful.klas.ui.common.EmptyState
import com.seblful.klas.ui.common.FabClearance
import com.seblful.klas.ui.common.FabListPadding
import com.seblful.klas.ui.common.KlasScreen
import com.seblful.klas.ui.common.ListSkeleton
import com.seblful.klas.ui.common.MaxContentWidth
import com.seblful.klas.ui.common.NoticeDialog
import com.seblful.klas.ui.common.ScrollsToTopOnReselect
import com.seblful.klas.ui.common.SegmentedPage
import com.seblful.klas.ui.common.Selection
import com.seblful.klas.ui.common.SettingsGear
import com.seblful.klas.ui.common.ShowMessagesFor
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.common.SupportingPaneMaxWidth
import com.seblful.klas.ui.common.animateInPlace
import com.seblful.klas.ui.common.plusInset
import com.seblful.klas.ui.common.rememberColumnTimeWidth
import com.seblful.klas.ui.common.rememberConfirmFeedback
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.common.rememberRejectFeedback
import com.seblful.klas.ui.common.roomForSupportingPane
import com.seblful.klas.ui.common.sectionSurface
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.common.timeText
import com.seblful.klas.ui.designsystem.component.KlasDivider
import com.seblful.klas.ui.designsystem.component.KlasFab
import com.seblful.klas.ui.designsystem.component.SectionAction
import com.seblful.klas.ui.designsystem.component.SlicePosition
import com.seblful.klas.ui.navigation.Arrival
import com.seblful.klas.ui.theme.KlasIcons
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    /** Books a lesson, opening its date picker on the day given — the one the schedule is on. */
    onAddClass: (LocalDate) -> Unit,
    onEditClass: (Long) -> Unit,
    onAddStudent: () -> Unit,
    /** Books a lesson like the one with this class id, a week later. */
    onBookLike: (Long) -> Unit = {},
    onOpenStudent: (Long) -> Unit = {},
    onOpenGroup: (Long) -> Unit = {},
    onSetStudentHomework: (Long) -> Unit = {},
    onSetGroupHomework: (Long) -> Unit = {},
    onRecordPayment: (Long) -> Unit = {},
    onSettings: () -> Unit = {},
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
    viewModel: ScheduleViewModel = metroViewModel(),
) {
    LaunchedEffect(arrival) { viewModel.arrive(arrival) }

    // One state for the body: the week holding what is on screen (see [ScheduleViewModel.content]), and
    // the day the day and month views read out of it — which names its own date, see [shownDay].
    val contentState by viewModel.content.collectAsStateWithLifecycle()
    val dayContent by viewModel.shownDay.collectAsStateWithLifecycle()
    val date by viewModel.selectedDate.collectAsStateWithLifecycle()

    // Every way in to a booking — the add button, an empty day's — books into the day on screen.
    val addClass = { onAddClass(date) }
    val canAddClass by viewModel.canAddClass.collectAsStateWithLifecycle()
    // From the view model, so it rolls over at midnight rather than being fixed at composition.
    val today by viewModel.today.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val month by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val grid by viewModel.monthGrid.collectAsStateWithLifecycle()
    val week by viewModel.weekDays.collectAsStateWithLifecycle()
    val loads by viewModel.dayLoads.collectAsStateWithLifecycle()
    val timeConflict by viewModel.timeConflict.collectAsStateWithLifecycle()
    // Where "now" falls in the selected day's list, or null when that day is not today — see [NowMarker].
    val nowMark by viewModel.nowMark.collectAsStateWithLifecycle()
    // Where each lesson stands this minute — being taught, or over and unmarked — see [ScheduleViewModel.phases].
    val phases by viewModel.phases.collectAsStateWithLifecycle()
    // What the schedule is narrowed to. Both the chips and the queries read this one value, so the schedule
    // can never be quietly narrowed with nothing saying so.
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val filterableStudents by viewModel.filterableStudents.collectAsStateWithLifecycle()
    val filterableGroups by viewModel.filterableGroups.collectAsStateWithLifecycle()
    // Why an empty schedule is empty — one answer for all three views, so they cannot disagree about
    // whether the tutor is looking at a quiet week or a narrowed one. See [ScheduleEmpty].
    val emptyReason by viewModel.emptyReason.collectAsStateWithLifecycle()
    // Lessons picked by a long-press: while any are, the bar is theirs — see [LessonBatch].
    val picked by viewModel.batch.selected.collectAsStateWithLifecycle()

    /** *A change of view is a new page, and a page starts at the top.** */
    val listState = rememberLazyListState()
    val listScope = rememberCoroutineScope()
    ScrollsToTopOnReselect { listState.animateScrollToItem(0) }
    val cancelDayLabel = stringResource(R.string.schedule_cancel_day)
    val openDay = { viewModel.setViewMode(ScheduleViewMode.DAY) }
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
    var paymentFor by remember { mutableStateOf<ScheduleRow?>(null) }
    var correctingRow by remember { mutableStateOf<ScheduleRow?>(null) }

    // A reminder's tap opens its lesson the way tapping the card does, once the day it is on has loaded.
    val askedSheet by viewModel.askedSheet.collectAsStateWithLifecycle()
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
    val correctionChangedMsg = stringResource(R.string.class_correction_changed)
    val undoChangedMsg = stringResource(R.string.class_undo_changed)
    ShowMessagesFor(viewModel.events, messages) { event ->
        when (event) {
            ScheduleViewModel.Event.ACTION_FAILED -> actionFailedMsg
            ScheduleViewModel.Event.LESSON_GONE -> lessonGoneMsg
            ScheduleViewModel.Event.CORRECTION_CHANGED -> correctionChangedMsg
            ScheduleViewModel.Event.UNDO_CHANGED -> undoChangedMsg
        }
    }
    val completedMsg = stringResource(R.string.class_completed_message)
    LaunchedEffect(viewModel, completedMsg) {
        viewModel.completions.stream.collect { completed ->
            messages.undoable(completedMsg) { viewModel.correctCompletion(completed, undo = true) }
        }
    }
    val undoableMsgs = UndoableChange.entries.associateWith { stringResource(it.message) }
    LaunchedEffect(viewModel, undoableMsgs) {
        viewModel.undoables.stream.collect { change ->
            messages.undoable(undoableMsgs.getValue(change.change)) { viewModel.undo(change) }
        }
    }

    BatchMessages(viewModel.batch, messages)

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

    // On an expanded window the month is Material's supporting pane: the grid beside the day it picks.
    val monthBeside = viewMode == ScheduleViewMode.MONTH && roomForSupportingPane()
    val picking = LessonPicking(picked) { viewModel.batch.toggle(it.session.first.id) }
    CompositionLocalProvider(LocalLessonPicking provides picking) {
        KlasScreen(
            title = stringResource(R.string.schedule_title),
            page = viewMode,
            messages = messages,
            maxContentWidth = if (monthBeside) SupportingPaneMaxWidth else MaxContentWidth,
            selection = Selection(picked.size, viewModel.batch::clear) { BatchActions(viewModel.batch) },
            actions = { SettingsGear(onSettings) },
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
                KlasFab(
                    label = stringResource(R.string.class_book),
                    onClick = {
                        // Before the setup has been read there is no answer yet, and the tap waits for it.
                        when (canAddClass.valueOrNull()) {
                            true -> addClass()
                            false -> messages.show(cannotBookMsg, addStudentLabel, onAddStudent)
                            null -> Unit
                        }
                    },
                )
            },
        ) { modifier, bottomInset ->
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
                // Beside the day, the month's pager sits over the grid it turns.
                headerAlignment = if (monthBeside) Alignment.Start else Alignment.CenterHorizontally,
                headerMaxWidth = if (monthBeside) CalendarPaneWidth else MaxContentWidth,
                header = {
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
                                selected = date,
                                onPick = viewModel::selectDate,
                            )
                        }
                        ScheduleViewMode.WEEK -> WeekNavigator(
                            days = week,
                            onPrevious = viewModel::goToPreviousWeek,
                            onNext = viewModel::goToNextWeek,
                            onToday = viewModel::goToToday,
                            selected = date,
                            onPick = viewModel::selectDate,
                        )
                        ScheduleViewMode.MONTH -> MonthNavigator(
                            month = month,
                            onPrevious = viewModel::goToPreviousMonth,
                            onNext = viewModel::goToNextMonth,
                            onToday = viewModel::goToToday,
                            selected = date,
                            onPick = viewModel::selectDate,
                        )
                    }
                    // **The chips are what make the filter honest**: a narrowed schedule and a quiet week look
                    // alike, so the chips name what is shown and are the way back. The Day view's summary
                    // sits beside them — what the day comes to, under the day it names — so the header is
                    // one height whatever is chosen ([ScreenHeader]).
                    ScheduleFilterRow(
                        filter = filter,
                        students = filterableStudents,
                        groups = filterableGroups,
                        onWho = viewModel::setWho,
                        onStatus = viewModel::setStatusFilter,
                    ) {
                        if (viewMode == ScheduleViewMode.DAY) {
                            DaySummaryLine(
                                summary = dayContent.valueOrNull()?.summary ?: DaySummary.NONE,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                },
            ) {
                // The grid scrolls with the classes rather than sitting above them: six week rows plus
                // a full day of classes doesn't fit a landscape phone.
                // Measured here, once for every card in the list — see [ColumnTime].
                val timeWidth = rememberColumnTimeWidth(MaterialTheme.typography.titleMedium)
                if (monthBeside) {
                    Row(Modifier.fillMaxSize()) {
                        // The grid keeps its phone width, scrolling alone if a short window cuts it off.
                        MonthCalendar(
                            grid = grid,
                            loads = loads,
                            selected = date,
                            today = today,
                            onSelect = viewModel::selectDate,
                            modifier = Modifier
                                .width(CalendarPaneWidth)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(Spacing.gutter)
                                .padding(bottomInset),
                        )
                        LazyColumn(
                            Modifier.weight(1f).fillMaxHeight(),
                            state = listState,
                            // The grid's gutter and this make Material's 24 dp between panes.
                            contentPadding = PaddingValues(
                                start = Spacing.sectionGap - Spacing.gutter,
                                top = Spacing.gutter,
                                end = Spacing.gutter,
                                bottom = FabClearance,
                            ).plusInset(bottomInset),
                            // A day's slices touch; the gap before the day is its header's own.
                            verticalArrangement = Arrangement.Top,
                        ) {
                            item(key = "selected-day", contentType = "day-header") {
                                ChosenDayHeader(dayContent, date, onOpenDay = openDay)
                            }
                            dayList(
                                sliced = true,
                                view = ScheduleViewMode.MONTH,
                                state = dayContent,
                                emptyReason = emptyReason,
                                nowMark = nowMark,
                                phases = phases,
                                timeWidth = timeWidth,
                                onRowClick = { sheetFor = it },
                                onComplete = { viewModel.setStatus(it.session, LessonStatus.COMPLETED) },
                                onAddClass = addClass,
                                onAddStudent = onAddStudent,
                            )
                        }
                    }
                    return@SegmentedPage
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    // The gutter is the list's, not each row's — see [Spacing]. The calendar items in here
                    // are laid out inside it too, which is why `MonthCalendar` and `WeekStrip` no longer
                    // carry a narrower one of their own.
                    contentPadding = FabListPadding.plusInset(bottomInset),
                    // The Day view's lessons are cards with a gap between; Week's and Month's are the slices of
                    // a day's section and touch, the gap before each day being its header's own.
                    verticalArrangement = if (viewMode == ScheduleViewMode.DAY) {
                        Arrangement.spacedBy(Spacing.itemGap)
                    } else {
                        Arrangement.Top
                    },
                ) {
                    when (viewMode) {
                        // The day list stands alone under its stepper.
                        ScheduleViewMode.DAY -> dayList(
                            view = ScheduleViewMode.DAY,
                            state = dayContent,
                            emptyReason = emptyReason,
                            nowMark = nowMark,
                            phases = phases,
                            timeWidth = timeWidth,
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
                                        onSelect = { day ->
                                            viewModel.selectDate(day)
                                            // To the day's section, which is what the tap was for: the
                                            // selection alone only moved where the button books, unseen.
                                            contentState.valueOrNull()?.indexOfDay(day)?.let { index ->
                                                listScope.launch { listState.animateScrollToItem(index) }
                                            }
                                        },
                                    )
                                    Spacer(Modifier.height(Spacing.itemGap))
                                    KlasDivider()
                                }
                            }
                            val week = contentState.valueOrNull()
                            if (week == null) {
                                item(key = "loading", contentType = "loading") { LessonsSkeleton() }
                            } else if (week.sections.isEmpty()) {
                                item(key = "empty", contentType = "empty") {
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
                                            position = SlicePosition.FIRST,
                                            action = section.plannedIds().takeIf { it.isNotEmpty() }?.let { ids ->
                                                SectionAction(cancelDayLabel) { viewModel.batch.cancel(ids) }
                                            },
                                        )
                                    }
                                    itemsIndexed(
                                        section.rows,
                                        key = { _, row -> lessonKey(ScheduleViewMode.WEEK, row) },
                                        contentType = { _, _ -> "lesson" },
                                    ) { index, row ->
                                        SwipeToCompleteCard(
                                            row = row,
                                            phase = phases.of(row),
                                            timeWidth = timeWidth,
                                            onClick = { sheetFor = row },
                                            onComplete = {
                                                viewModel.setStatus(row.session, LessonStatus.COMPLETED)
                                            },
                                            slice = lastOrMiddle(index, section.rows.size),
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
                                    KlasDivider()
                                    // Named by the day the list below *is*, which for the frames a tap on
                                    // another day takes to read is still the previous one — see
                                    // [ScheduleContent.Day.date]. The grid's ring has already moved.
                                    ChosenDayHeader(dayContent, date, onOpenDay = openDay)
                                }
                            }
                            dayList(
                                sliced = true,
                                view = ScheduleViewMode.MONTH,
                                state = dayContent,
                                emptyReason = emptyReason,
                                nowMark = nowMark,
                                phases = phases,
                                timeWidth = timeWidth,
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
    }

    sheetFor?.let { opened ->
        val row = contentState.valueOrNull()?.rowsShown
            ?.firstOrNull { it.session.lessonId == opened.session.lessonId } ?: opened
        // Keyed on the lesson, so it is one read per sheet and not one per recomposition of it.
        val homework by remember(row.session.lessonId) { viewModel.homeworkAt(row.session) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
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
            phase = phases.of(row),
            sheetState = sheetState,
            homework = homework,
            // A tick leaves the sheet open, unlike every other action on it: a group lesson can have
            // several pieces attached, and the sheet closing after the first would be a reopening each.
            onHomeworkDone = viewModel::setHomeworkDone,
            onAttendance = viewModel::setAttendance,
            onEdit = { onEditClass(row.klass.id); sheetFor = null },
            onBookNext = { closeSheet { onBookLike(row.klass.id) } },
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
            onCorrectCompletion = { closeSheet { correctingRow = row } },
            onSetHomework = {
                closeSheet {
                    row.session.groupId?.let(onSetGroupHomework) ?: onSetStudentHomework(row.klass.studentId)
                }
            },
            onRecordPayment = {
                closeSheet { if (row.isGroup) paymentFor = row else onRecordPayment(row.klass.studentId) }
            },
            onOpenProfile = {
                closeSheet { row.session.groupId?.let(onOpenGroup) ?: onOpenStudent(row.klass.studentId) }
            },
        )
    }

    paymentFor?.let { row ->
        LessonPayerDialog(row, onRecordPayment, onDismiss = { paymentFor = null })
    }
    correctingRow?.let { row ->
        ConfirmDialog(
            text = stringResource(R.string.class_correct_planned_confirm),
            confirmLabel = stringResource(R.string.class_correct_planned),
            onConfirm = { viewModel.correctCompletion(row.session); correctingRow = null },
            onDismiss = { correctingRow = null },
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

@Composable
internal fun timeConflictText(conflict: TimeConflict): String = stringResource(
    R.string.class_time_conflict,
    conflict.who,
    "${conflict.slot.start.timeText()} – ${conflict.slot.end.timeText()}",
)

private enum class ScheduleEmptySpan { DAY, WEEK }

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
            icon = KlasIcons.Outlined.EventBusy,
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
            icon = KlasIcons.Outlined.EventBusy,
            actionLabel = stringResource(R.string.schedule_add_class),
            onAction = onAddClass,
            modifier = modifier,
        )

        ScheduleEmpty.NEEDS_STUDENT -> EmptyState(
            title = stringResource(R.string.schedule_empty_no_setup),
            detail = stringResource(R.string.schedule_empty_no_setup_detail),
            icon = KlasIcons.Outlined.PersonAdd,
            actionLabel = stringResource(R.string.schedule_add_student),
            onAction = onAddStudent,
            modifier = modifier,
        )
    }
}

private fun LazyListScope.dayList(
    view: ScheduleViewMode,
    sliced: Boolean = false,
    state: Loadable<ScheduleContent.Day>,
    emptyReason: Loadable<ScheduleEmpty>,
    nowMark: Int?,
    phases: Map<Long, LessonPhase>,
    timeWidth: Dp,
    onRowClick: (ScheduleRow) -> Unit,
    onComplete: (ScheduleRow) -> Unit,
    onAddClass: () -> Unit,
    onAddStudent: () -> Unit,
) {
    val day = when (state) {
        Loadable.Loading -> {
            item(key = "loading", contentType = "loading") { LessonsSkeleton() }
            return
        }
        is Loadable.Ready -> state.value
    }
    val rows = day.rows
    if (rows.isEmpty()) {
        item(key = "empty", contentType = "empty") {
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
            if (index == nowMark) item(key = nowKey(view, day.date), contentType = "now") { DayNowLine(sliced) }
            item(key = lessonKey(view, row, day.date), contentType = "lesson") {
                SwipeToCompleteCard(
                    row = row,
                    phase = phases.of(row),
                    timeWidth = timeWidth,
                    onClick = { onRowClick(row) },
                    onComplete = { onComplete(row) },
                    slice = if (sliced) lastOrMiddle(index, rows.size) else null,
                )
            }
        }
        if (nowMark == rows.size) item(key = nowKey(view, day.date), contentType = "now") { DayNowLine(sliced) }
    }
}

/** The now-line between a day's lessons — on the day's section surface when they are its slices. */
@Composable
private fun LazyItemScope.DayNowLine(sliced: Boolean) {
    val surface = if (sliced) Modifier.background(MaterialTheme.colorScheme.sectionSurface) else Modifier
    NowLine(animateInPlace().then(surface))
}

/** The chosen day of the Month view: the top slice of its section, with «Open day» to the Day view (R1). */
@Composable
private fun ChosenDayHeader(dayContent: Loadable<ScheduleContent.Day>, selected: LocalDate, onOpenDay: () -> Unit) {
    val day = dayContent.valueOrNull()
    SelectedDayHeader(
        date = day?.date ?: selected,
        summary = day?.summary ?: DaySummary.NONE,
        position = if (day?.rows.isNullOrEmpty()) SlicePosition.ONLY else SlicePosition.FIRST,
        action = SectionAction(stringResource(R.string.schedule_open_day), onOpenDay),
    )
}

/** A day's lesson [index] of [count] as a slice under its header: the last one rounds the card off. */
private fun lastOrMiddle(index: Int, count: Int): SlicePosition =
    if (index == count - 1) SlicePosition.LAST else SlicePosition.MIDDLE

private fun lessonKey(view: ScheduleViewMode, row: ScheduleRow, day: LocalDate? = null): String =
    "$view/${day ?: ""}/${row.klass.id}"

private fun nowKey(view: ScheduleViewMode, day: LocalDate): String = "$view/$day/now"

@Composable
private fun LessonsSkeleton() {
    ListSkeleton(rows = 3, contentPadding = PaddingValues(0.dp))
}

private fun viewModeLabel(mode: ScheduleViewMode): Int = when (mode) {
    ScheduleViewMode.DAY -> R.string.schedule_view_day
    ScheduleViewMode.WEEK -> R.string.schedule_view_week
    ScheduleViewMode.MONTH -> R.string.schedule_view_month
}

/** A month grid's own width beside the day it opens: Material's fixed supporting pane. */
private val CalendarPaneWidth = 360.dp

private typealias UndoableChange = ScheduleViewModel.Undoable.Change

private val UndoableChange.message: Int
    get() = when (this) {
        UndoableChange.CANCELLED -> R.string.class_cancelled_message
        UndoableChange.REPLANNED -> R.string.class_replanned_message
        UndoableChange.MOVED -> R.string.class_moved_message
        UndoableChange.ATTENDANCE -> R.string.class_attendance_message
    }
