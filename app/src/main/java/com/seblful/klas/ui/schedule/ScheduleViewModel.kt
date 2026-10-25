package com.seblful.klas.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.homework.HomeworkRepository
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.data.lessons.UndoResult
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.di.FirstDayOfWeek
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.people.Captions
import com.seblful.klas.domain.schedule.CalendarLayout
import com.seblful.klas.domain.schedule.DayBounds
import com.seblful.klas.domain.schedule.DaySummary
import com.seblful.klas.domain.schedule.NowMarker
import com.seblful.klas.domain.schedule.ScheduleEmpty
import com.seblful.klas.domain.schedule.TimeConflict
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.domain.time.today
import com.seblful.klas.format.days
import com.seblful.klas.format.minutes
import com.seblful.klas.format.zones
import com.seblful.klas.lessons.LessonChange
import com.seblful.klas.lessons.LessonChanges
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.hold
import com.seblful.klas.ui.common.state.map
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.common.state.readyOrLoadingAfter
import com.seblful.klas.ui.common.state.shared
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.common.state.write
import com.seblful.klas.ui.homework.HomeworkActions
import com.seblful.klas.ui.navigation.Arrival
import com.seblful.klas.ui.navigation.ArrivalGate
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ScheduleViewModel @Inject constructor(
    private val classes: ClassRepository,
    private val students: StudentRepository,
    private val subjects: SubjectRepository,
    private val groups: GroupRepository,
    private val homework: HomeworkRepository,
    lessonChanges: LessonChanges,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
    private val clock: WallClock,

    @param:FirstDayOfWeek private val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) : ViewModel() {

    // From the clock, so the one thing that watches the date can be moved in a test. See TimeModule.
    private val zone: TimeZone get() = clock.zone

    private var pages = SchedulePages.on(clock.today(), weekStart)

    private val _selectedDate = MutableStateFlow(pages.selected)
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val askedLessonId = MutableStateFlow<Long?>(null)

    /** Today — **re-emitted when the date changes**, not read once ([days]). */
    val today: StateFlow<LocalDate> = clock.days()
        .onEach { followToday(it) }
        .hold(this, clock.today())

    private val _visibleMonth = MutableStateFlow(pages.month)
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    private val _visibleWeek = MutableStateFlow(pages.week)
    val visibleWeek: StateFlow<LocalDate> = _visibleWeek.asStateFlow()

    private val _viewMode = MutableStateFlow(pages.mode)
    val viewMode: StateFlow<ScheduleViewMode> = _viewMode.asStateFlow()

    /** The filter as asked, by ids — [filter] is it resolved against who still exists. */
    private val askedFilter = MutableStateFlow(ScheduleFilter())

    /** Every student, read once for the filter, the week's captions and the lesson sheet. */
    private val everyone: Flow<List<Student>> =
        students.observeAll().shared(this)

    /**
     * What the schedule is narrowed to, named — see [ScheduleFilter]. Somebody the database no longer holds
     * is no one to narrow to, so the filter falls back to everyone rather than to an empty schedule.
     */
    val filter: StateFlow<ScheduleFilter> =
        combine(askedFilter, everyone, groups.observeAll()) { asked, people, groupList ->
            asked.copy(
                who = when (val who = asked.who) {
                    null -> null
                    is ScheduleWho.Student ->
                        people.firstOrNull { it.id == who.id }?.let { ScheduleWho.Student(it.id, it.name) }
                    is ScheduleWho.Group ->
                        groupList.firstOrNull { it.id == who.id }?.let { ScheduleWho.Group(it.id, it.name) }
                },
            )
        }
            .derive(this, computation, ScheduleFilter())

    /** The students the filter menu offers — the active ones, in the repository's name order. */
    val filterableStudents: StateFlow<List<Student>> = students.observeActive().hold(this, emptyList())

    /** The groups the filter menu offers after the students. */
    val filterableGroups: StateFlow<List<Group>> = groups.observeActive().hold(this, emptyList())

    /** Narrows the schedule to one student, or to everyone again with null. */
    fun setStudentFilter(studentId: Long?) = setWho(studentId?.let { ScheduleWho.Student(it) })

    /** Narrows the schedule to one student's or group's lessons, or to everyone's with null. */
    fun setWho(who: ScheduleWho?) = askedFilter.update { it.copy(who = who) }

    /** Narrows the schedule to lessons in one status, or to every status with null. */
    fun setStatusFilter(status: LessonStatus?) = askedFilter.update { it.copy(status = status) }

    private val _timeConflict = MutableStateFlow<TimeConflict?>(null)
    val timeConflict: StateFlow<TimeConflict?> = _timeConflict.asStateFlow()

    /** One-shot signals from an action that did not go through. */
    enum class Event {
        /**
         * The write failed — a database or calendar error rather than a rule. These actions are tapped
         * in a sheet that closes as it is tapped, so without saying this the lesson simply appeared
         * not to change: the tutor marks a lesson completed, the sheet shuts, and the row still reads
         * «Запланировано» with nothing to explain it.
         */
        ACTION_FAILED,

        /**
         * A reminder was tapped for a lesson that has since been deleted. Said rather than silently
         * ignored: the tray named a lesson, and a schedule with no sign of it would read as the app
         * having lost it.
         */
        LESSON_GONE,
        CORRECTION_CHANGED,

        /** An undo found the lesson changed since, so the change it would have taken back stands. */
        UNDO_CHANGED,
    }

    /** A change from the sheet the tutor can take back from its message — see [undo]. */
    data class Undoable(val change: Change, val undo: LessonChange) {
        enum class Change { CANCELLED, REPLANNED, MOVED, ATTENDANCE }
    }

    val events = UiEvents<Event>()
    val completions = UiEvents<ClassSession>()
    val undoables = UiEvents<Undoable>()

    /** Lessons picked by a long-press, marked taught or cancelled together — see [LessonBatch]. */
    val batch = LessonBatch(classes, lessonChanges, appScope)

    private val changes = lessonChanges.inScope(appScope) { events.send(Event.ACTION_FAILED) }

    private val homeworkActions = HomeworkActions(homework, appScope, events, Event.ACTION_FAILED)

    /**
     * A class can only be booked once there is at least one student and one priced subject — a
     * class needs both, so the schedule's add button stays disabled until they exist.
     */
    val canAddClass: StateFlow<Loadable<Boolean>> =
        combine(students.observeActive(), subjects.observeAll()) { studentList, subjectList ->
            studentList.isNotEmpty() && subjectList.isNotEmpty()
        }
            .ready()
            .derive(this, computation, Loadable.Loading)

    /**
     * What an empty schedule means right now — asked by whichever view finds itself with nothing to show,
     * so day, month and week cannot answer it differently. See [ScheduleEmpty].
     */
    val emptyReason: StateFlow<Loadable<ScheduleEmpty>> =
        combine(filter, canAddClass) { filter, bookable ->
            bookable.map { ScheduleEmpty.of(filtered = filter.narrowed, canAddClass = it) }
        }
            // Loading until [canAddClass] is known, for its reason: an empty day read before the setup was
            // offered «Нечего планировать» and «Добавить ученика» for the frame the answer took.
            .derive(this, computation, Loadable.Loading)

    val monthGrid: StateFlow<CalendarLayout.MonthGrid> =
        _visibleMonth.map { CalendarLayout.monthGrid(it, weekStart) }
            .derive(this, computation, CalendarLayout.monthGrid(_visibleMonth.value, weekStart))

    /** The seven days the week strip shows. */
    val weekDays: StateFlow<List<LocalDate>> =
        _visibleWeek.map { CalendarLayout.week(it, weekStart) }
            .derive(this, computation, CalendarLayout.week(_visibleWeek.value, weekStart))

    /**
     * Per-day markers for the cells the active view is showing — the month page (including the
     * neighbouring-month days padding its first and last rows, so those cells aren't misleadingly
     * blank) or the week strip. Scoped to the active view so browsing far from the selected day
     * never widens the query, and so the day view pays for no markers at all.
     */
    val dayLoads: StateFlow<Loadable<Map<LocalDate, CalendarLayout.DayLoad>>> =
        combine(_viewMode, _visibleMonth, _visibleWeek, filter, clock.zones(), ::MarkerKey)
            .flatMapLatest { key ->
                val zone = key.zone
                val range = when (key.mode) {
                    ScheduleViewMode.DAY -> null
                    ScheduleViewMode.WEEK -> key.week to key.week.plus(DAYS_IN_WEEK, DateTimeUnit.DAY)
                    ScheduleViewMode.MONTH ->
                        CalendarLayout.monthGrid(key.month, weekStart)
                            .let { it.firstDate to it.endExclusive }
                }
                if (range == null) {
                    // The day view draws no calendar, so it queries none — and says **Loading** rather
                    // than "no markers", which is a claim about a month nobody asked it about. That claim
                    // used to survive the switch back: `Ready(emptyMap())` from the day view was still
                    // there when «Месяц» re-appeared, so the grid drew a free month for a frame.
                    flowOf(Loadable.Loading)
                } else {
                    val bounds = DayBounds.of(range.first, range.second, zone)
                    classes.observeBetween(bounds.start, bounds.end)
                        .map { CalendarLayout.dayLoads(key.narrow(it), zone) }
                        // Paging is a new question too: the tallies are keyed by date, so last month's
                        // map has no entry for any day of this one and those cells read as free — for
                        // the frames the new month takes to count, and no longer than the grace.
                        .readyOrLoadingAfter()
                }
            }
            .derive(this, computation, Loadable.Loading)

    private data class MarkerKey(
        val mode: ScheduleViewMode,
        val month: YearMonth,
        val week: LocalDate,
        val filter: ScheduleFilter,
        val zone: TimeZone,
    ) {
        fun narrow(classes: List<TutoringClass>): List<TutoringClass> = filter.narrow(classes)
    }

    /**
     * The lessons of **the week that holds what is on screen** — the visible week, which in the day and
     * month views is the selected day's (see [SchedulePages.select]).
     */
    val content: StateFlow<Loadable<ScheduleContent.Week>> =
        combine(_visibleWeek, filter, clock.zones(), ::Triple)
            .flatMapLatest { (week, filter, zone) ->
                val bounds = DayBounds.of(week, week.plus(DAYS_IN_WEEK, DateTimeUnit.DAY), zone)
                combine(
                    classes.observeBetween(bounds.start, bounds.end),
                    everyone,
                    subjects.observeAll(),
                    groups.observeAll(),
                ) { readClasses, studentList, subjectList, groupList ->
                    val visibleClasses = filter.narrow(readClasses)
                    // Built once per emission and asked per lesson — the three `associateBy` maps this
                    // replaces, plus the rule about which name titles a lesson. See [Captions].
                    val captions = Captions.of(studentList, subjectList, groupList)
                    // Sessions are collapsed per day, not across the week: grouping first would let a
                    // group lesson's members land in one row even if the days differed. Days without a
                    // class are left out — an agenda reads better as a list of what's on than a wall of
                    // empty days, and [day] knows a missing section in a week it covers is a free day.
                    ScheduleContent.Week(
                        start = week,
                        sections = visibleClasses
                            .groupBy { it.start.toLocalDateTime(zone).date }
                            .toSortedMap()
                            .map { (day, dayClasses) ->
                                val rows = sessionRows(dayClasses, captions)
                                WeekSection(date = day, rows = rows, summary = rows.summarised())
                            },
                    )
                }
                    // **A new question un-answers the old one — once it is late.** Inside the
                    // `flatMapLatest`, so a change of week or of filter goes back to Loading if the answer
                    // takes longer than the grace. Within it the previous week stays up, which is what
                    // keeps paging from blinking the list to a skeleton; [day] will not read a day out of
                    // a week that does not hold it, and a day's heading names the day its content carries.
                    .readyOrLoadingAfter()
            }
            // `ScheduleContent.Day(emptyList())` used to be the initial value, which is a *day with
            // no lessons* — so a cold open drew «Нет занятий» over the tutor's actual morning, with
            // the book-a-lesson button under it, until the query returned. See [Loadable].
            .derive(this, computation, Loadable.Loading)

    /**
     * **The day the day and month views draw** — the selected day read off [content], or, while the week
     * holding it is still being read, the day already on screen.
     */
    val shownDay: StateFlow<Loadable<ScheduleContent.Day>> =
        combine(content, _selectedDate, ::Pair)
            .scan(Loadable.Loading as Loadable<ScheduleContent.Day>) { shown, (week, date) ->
                val asked = week.day(date)
                if (asked is Loadable.Loading && week is Loadable.Ready) shown else asked
            }
            .derive(this, computation, Loadable.Loading)

    /**
     * Whether every lesson picked is cancelled, so the bar offers to return them to the plan. Read from the shown
     * rows: a pick that is not among them (the selection outlived its page) is not known to be cancelled.
     */
    val pickedAllCancelled: StateFlow<Boolean> =
        combine(batch.selected, content) { ids, loaded ->
            val rows = loaded.valueOrNull()?.rowsShown.orEmpty()
            ids.isNotEmpty() && ids.all { id ->
                rows.firstOrNull { row -> row.classes.any { it.id == id } }?.session?.status == LessonStatus.CANCELLED
            }
        }
            .derive(this, computation, false)

    private val minutes: Flow<Instant> = clock.minutes()

    /**
     * Where the **"you are here"** mark goes in the selected day's list: the index it sits before, or
     * null when there is no mark to draw — see [NowMarker]. Only the Day view draws one: under the month
     * the chosen day is a glance at a cell, not a timeline to place yourself on.
     */
    val nowMark: StateFlow<Int?> =
        combine(shownDay, minutes, _viewMode) { shown, now, mode ->
            if (mode != ScheduleViewMode.DAY) return@combine null
            // Among the rows that are drawn, on the day they are — [shownDay]'s date, which for the frames
            // a step into another week takes to read is still the previous day.
            val day = shown.valueOrNull() ?: return@combine null
            NowMarker.positionIn(day.rows.map { it.session }, day.date, now, zone)
        }
            .derive(this, computation, null)

    /**
     * The shown lessons the clock has something to say about **this minute**, by lesson id: being taught
     * («Идёт»), or over and unmarked («Не отмечено»). Read with [of]. Only those two, so a day of lessons
     * still to come is the same empty map as any other, and moving between such days redraws no row.
     */
    val phases: StateFlow<Map<Long, LessonPhase>> =
        combine(content, minutes) { loaded, now ->
            loaded.valueOrNull()?.rowsShown.orEmpty()
                .map { it.session.lessonId to LessonPhase.of(it.session, now) }
                .filter { (_, phase) -> phase == LessonPhase.IN_PROGRESS || phase == LessonPhase.OVERDUE }
                .toMap()
        }
            .derive(this, computation, emptyMap())

    /** Switches view, on the pages holding the selected day — see [SchedulePages.showing]. */
    fun setViewMode(mode: ScheduleViewMode) = page { it.showing(mode) }

    private fun followToday(date: LocalDate) {
        val moved = pages.dayChanged(date)
        if (moved.selected != pages.selected) askedLessonId.value = null
        page { moved }
    }

    private val arrivals = ArrivalGate()

    /**
     * Puts the schedule where a tap in the tray asked (see `AppRoot`), **once per tap**. The screen hands
     * the arrival over every time it comes back, since it leaves composition whenever a lesson or another
     * tab covers it; honouring it each time sent the tutor back to the notification's day after every
     * edit, for the rest of the process.
     */
    fun arrive(arrival: Arrival<ScheduleTarget>?) {
        when (val target = arrivals.admit(arrival) ?: return) {
            ScheduleTarget.Today -> goToToday()
            is ScheduleTarget.Day -> selectDate(target.date)
            is ScheduleTarget.Lesson -> viewModelScope.launch { showLesson(target.classId) }
        }
    }

    private suspend fun showLesson(classId: Long) {
        val session = classes.getSessionFor(classId)
        if (session == null) {
            events.send(Event.LESSON_GONE)
            return
        }
        // A filter that would hide the lesson the tray named is lifted, rather than opening nothing.
        val asked = askedFilter.value
        if (asked.narrowed && asked.narrow(session.classes).isEmpty()) askedFilter.value = ScheduleFilter()
        selectDate(session.start.toLocalDateTime(zone).date)
        askedLessonId.value = classId
    }

    /**
     * The row a reminder's tap asked to open, **once the page it is on has loaded** — null until then, and
     * after [sheetShown]. A flow over the content rather than a lookup, because the page is re-queried
     * for the day just selected and the row is not there to be found until that answers.
     */
    val askedSheet: StateFlow<ScheduleRow?> =
        combine(content, askedLessonId) { loaded, classId ->
            classId?.let { id ->
                loaded.valueOrNull()?.rowsShown?.firstOrNull { row -> row.classes.any { it.id == id } }
            }
        }
            .derive(this, computation, null)

    /** The asked-for sheet is open; the ask is spent. */
    fun sheetShown() {
        askedLessonId.value = null
    }

    /** Picks the day the day list shows and the FAB books into; the calendar pages follow it. */
    fun selectDate(date: LocalDate) {
        askedLessonId.value = null
        page { it.select(date) }
    }

    fun goToPreviousDay() = selectDate(pages.selected.minus(1, DateTimeUnit.DAY))

    fun goToNextDay() = selectDate(pages.selected.plus(1, DateTimeUnit.DAY))

    fun goToToday() = selectDate(clock.today())

    fun goToPreviousMonth() = page { it.previousMonth() }

    fun goToNextMonth() = page { it.nextMonth() }

    fun goToPreviousWeek() = page { it.previousWeek() }

    fun goToNextWeek() = page { it.nextWeek() }

    // Every action below takes a session, so it reads the same whether the tutor tapped a lesson for
    // one student or a whole group; the repository applies it per class and refreshes reminders once.
    //
    // Each write runs in [appScope] rather than `viewModelScope`. The sheet these are called from
    // closes as the action is tapped, and marking a lesson completed or deleting one must not be cut
    // in half because the screen went away a moment later — see [ApplicationScope].

    /**
     * Changes a whole lesson's status. Marking it taught is taken back by [correctCompletion]; cancelling
     * and re-planning by [undo].
     */
    fun setStatus(session: ClassSession, status: LessonStatus) {
        if (status == LessonStatus.COMPLETED) return complete(session)
        write {
            when (val changed = changes.setStatus(session, status)) {
                is LessonWrite.HourTaken -> reportHourTaken(changed.clash)
                is LessonWrite.Written -> offerUndo(
                    changed.value,
                    if (status == LessonStatus.CANCELLED) Undoable.Change.CANCELLED else Undoable.Change.REPLANNED,
                )
                // Deleted underneath the sheet: the schedule already shows it gone.
                LessonWrite.Gone -> Unit
            }
        }
    }

    private fun complete(session: ClassSession) {
        write {
            val changed = classes.setStatus(session, LessonStatus.COMPLETED)
            if (changed is LessonWrite.Written && changed.value.isNotEmpty()) {
                completions.send(ClassSession(session.groupId, changed.value))
            }
        }
    }

    fun correctCompletion(session: ClassSession, undo: Boolean = false) {
        write {
            if (!classes.correctCompletion(session, unchanged = session.takeIf { undo })) {
                events.send(Event.CORRECTION_CHANGED)
            }
        }
    }

    fun setAttendance(klass: TutoringClass, attended: Attended) {
        write {
            when (val marked = changes.setAttendance(klass, attended)) {
                is LessonWrite.Written -> offerUndo(marked.value, Undoable.Change.ATTENDANCE)
                LessonWrite.Gone -> events.send(Event.LESSON_GONE)
                is LessonWrite.HourTaken -> Unit
            }
        }
    }

    /**
     * Reschedules a whole lesson to [day] at [at], unless that hour is already taken — one tutor cannot
     * teach two lessons at once, so the repository refuses a move onto an occupied slot ([LessonWrite])
     * rather than silently doing nothing, and the refusal is reported through [timeConflict]. The lesson's
     * **own** classes never stand in their own way, so nudging it by ten minutes is fine.
     */
    fun moveTo(session: ClassSession, day: LocalDate, at: LocalTime) {
        val newStart = day.atTime(at).toInstant(zone)
        write {
            when (val moved = changes.move(session, newStart, now = clock.now())) {
                is LessonWrite.HourTaken -> reportHourTaken(moved.clash)
                is LessonWrite.Written -> offerUndo(moved.value, Undoable.Change.MOVED)
                LessonWrite.Gone -> Unit
            }
        }
    }

    /**
     * Takes [change] back while the lesson is still as the change left it, and its hour is free if it
     * needs one again — see [ClassRepository.undo]. Otherwise the change stands and the tutor is told why.
     */
    fun undo(change: Undoable) {
        write {
            when (val undone = changes.undo(change.undo)) {
                UndoResult.Restored -> Unit
                UndoResult.ChangedSince -> events.send(Event.UNDO_CHANGED)
                is UndoResult.HourTaken -> reportHourTaken(undone.clash)
                UndoResult.Gone -> events.send(Event.LESSON_GONE)
            }
        }
    }

    private fun offerUndo(undo: LessonChange?, change: Undoable.Change) {
        undo ?: return
        undoables.send(Undoable(change, undo))
    }

    private suspend fun reportHourTaken(clash: ClassSession) {
        _timeConflict.value = TimeConflict.of(
            clash = clash,
            groupName = clash.groupId?.let { groups.getById(it)?.name },
            studentName = students.getById(clash.first.studentId)?.name,
        )
    }

    /** Acknowledges the refusal message, so the dialog closes. */
    fun dismissTimeConflict() {
        _timeConflict.value = null
    }

    fun delete(session: ClassSession) {
        write { classes.delete(session) }
    }

    /**
     * The homework attached to [session], for its sheet — done ones included, so a tick given there stays
     * on screen to be taken back.
     */
    fun homeworkAt(session: ClassSession): Flow<List<SheetHomework>> =
        combine(
            homework.observeAttachedTo(session),
            everyone,
            groups.observeAll(),
        ) { attached, studentList, groupList ->
            val captions = Captions.of(studentList, emptyList(), groupList)
            attached.map { item ->
                val named = session.isGroup && item.assignee is Assignee.Student
                SheetHomework(item, who = captions.titleOf(item.assignee).takeIf { named })
            }
        }.flowOn(computation)

    /** Ticks [item] done from the sheet, or takes the tick back. */
    fun setHomeworkDone(item: Homework, done: Boolean) = homeworkActions.setDone(item, done)

    private fun write(block: suspend () -> Unit) = appScope.write(events, Event.ACTION_FAILED, block = block)

    private fun page(change: (SchedulePages) -> SchedulePages) {
        pages = change(pages)
        _selectedDate.value = pages.selected
        _visibleMonth.value = pages.month
        _visibleWeek.value = pages.week
        _viewMode.value = pages.mode
    }

    private fun sessionRows(dayClasses: List<TutoringClass>, captions: Captions): List<ScheduleRow> =
        SessionRules.sessions(dayClasses).map { ScheduleRow.of(it, captions) }

    private fun List<ScheduleRow>.summarised(): DaySummary = DaySummary.of(map { it.session })

    private companion object {
        /** A week's span as a day count, for the bounds of the week being read. */
        const val DAYS_IN_WEEK = CalendarLayout.DAYS_IN_WEEK.toLong()
    }
}
