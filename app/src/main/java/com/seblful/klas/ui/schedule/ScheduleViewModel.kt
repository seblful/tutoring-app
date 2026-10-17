package com.seblful.klas.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.HomeworkRepository
import com.seblful.klas.data.repository.LessonWrite
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.di.FirstDayOfWeek
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassSession
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
import com.seblful.klas.format.zones
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.days
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.hold
import com.seblful.klas.ui.common.state.map
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.common.state.readyOrLoadingAfter
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.common.state.write
import com.seblful.klas.ui.homework.HomeworkActions
import com.seblful.klas.ui.navigation.Arrival
import com.seblful.klas.ui.navigation.ArrivalGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * These classes as the schedule shows them under [filter]: **whole lessons** the student is in, never
 * their class pulled out of one — see [SessionRules.lessonsWith] — or all of them with no filter on. A
 * filter changes what is on screen, not what a lesson is, so the sheet's actions still act on the session
 * the lesson consists of. One rule for the list and the calendar's tallies, so a cell cannot count lessons
 * the list under it does not show.
 */
private fun List<TutoringClass>.shownFor(filter: StudentFilter?): List<TutoringClass> =
    filter?.let { SessionRules.lessonsWith(this, it.studentId) } ?: this

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val classes: ClassRepository,
    private val students: StudentRepository,
    private val subjects: SubjectRepository,
    private val groups: GroupRepository,
    private val homework: HomeworkRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
    private val clock: Clock,
    /** The phone's own — see [FirstDayOfWeek]. Everything the calendar lays out is anchored on it. */
    @param:FirstDayOfWeek private val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) : ViewModel() {

    // From the clock, so the one thing that watches the date can be moved in a test. See TimeModule.
    private val zone: ZoneId get() = clock.zone

    private val _selectedDate = MutableStateFlow(LocalDate.now(clock))
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /**
     * The class whose lesson a reminder's tap asked to see, until its sheet is open — see [askedSheet].
     * Declared this early because [selectDate] clears it, and nothing may call that before it exists.
     */
    private val askedLessonId = MutableStateFlow<Long?>(null)

    /**
     * Today — **re-emitted when the date changes**, not read once ([days]).
     *
     * The screen used to compute `LocalDate.now()` in its own body, which is evaluated per composition
     * and then never again on its own. An app left open overnight — which is exactly what a phone on a
     * desk does — went on marking yesterday as today: the highlighted cell, the day headers and the
     * «сегодня» affordance were all a day behind until something else happened to recompose.
     *
     * **A test that collects this must advance time by a stated amount, never `advanceUntilIdle()`** —
     * see [days]. `WhileSubscribed` is what keeps that off every other test: nothing is collecting this
     * unless a test says so.
     */
    val today: StateFlow<LocalDate> = clock.days()
        .onEach { followToday(it) }
        .hold(this, LocalDate.now(clock))

    /**
     * The month the calendar page shows. Kept separate from [selectedDate] so paging through months
     * only browses — it never silently re-points the day list at a day the user did not pick.
     */
    private val _visibleMonth = MutableStateFlow(YearMonth.from(_selectedDate.value))
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    /**
     * The first day of the week the agenda shows — [weekStart], not necessarily a Monday. Browses
     * independently of [selectedDate], as above.
     */
    private val _visibleWeek = MutableStateFlow(CalendarLayout.startOfWeek(_selectedDate.value, weekStart))
    val visibleWeek: StateFlow<LocalDate> = _visibleWeek.asStateFlow()

    private val _viewMode = MutableStateFlow(ScheduleViewMode.DAY)
    val viewMode: StateFlow<ScheduleViewMode> = _viewMode.asStateFlow()

    private val filterStudentId = MutableStateFlow<Long?>(null)

    /**
     * Whose lessons the schedule is showing, or null for everyone's — see [StudentFilter].
     *
     * Declared **before** [dayLoads] and [content] because both narrow on it: properties initialise in
     * declaration order, and a flow combined into them before it exists is a null reference at
     * construction rather than at first collection.
     */
    val studentFilter: StateFlow<StudentFilter?> =
        combine(filterStudentId, students.observeAll()) { id, all ->
            id?.let { wanted -> all.firstOrNull { it.id == wanted } }
                ?.let { StudentFilter(it.id, it.name) }
        }
            .derive(this, computation, null)

    /**
     * The students the filter menu offers — the active ones, in the repository's name order.
     *
     * Archived students are left out for the reason they are left out of the booking form's picker: the
     * menu is a way of *starting* to look at somebody, and a retired student is not who a tutor is
     * planning around. One already being filtered by survives regardless, since [studentFilter] reads the
     * full list.
     */
    val filterableStudents: StateFlow<List<Student>> = students.observeActive().hold(this, emptyList())

    /** Narrows the schedule to one student, or shows everyone again with null. */
    fun setStudentFilter(studentId: Long?) {
        filterStudentId.value = studentId
    }

    /**
     * The lesson a refused [move] or [setStatus] would have collided with, until it is acknowledged.
     * State rather than a fire-and-forget event, so the message survives a recomposition or a rotation.
     */
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
    }

    val events = UiEvents<Event>()

    /** The sheet's homework tick, the same guarded write the «Задания» tab and both cards make. */
    private val homeworkActions = HomeworkActions(homework, appScope, events, Event.ACTION_FAILED)

    /**
     * A class can only be booked once there is at least one student and one priced subject — a
     * class needs both, so the schedule's add button stays disabled until they exist.
     *
     * [Loadable], because `false` before the first read is a claim: a tap on the add button during a cold
     * start was answered «Сначала добавьте предмет и ученика» in an app with forty students.
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
     *
     * A `StateFlow` rather than something the empty branch computes, because both facts behind it are
     * already live here and neither is a fact about drawing.
     */
    val emptyReason: StateFlow<Loadable<ScheduleEmpty>> =
        combine(studentFilter, canAddClass) { filter, bookable ->
            bookable.map { ScheduleEmpty.of(filtered = filter != null, canAddClass = it) }
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
     *
     * [Loadable] because **a cell with no tally is a statement that the day is free** — the calendar's
     * whole job is "how full is this week", answered in a glance nobody re-takes, and an empty map is the
     * same value a fortnight off gives. `DayTallies` therefore draws a placeholder rather than nothing
     * until the counts have been read. Note what stays unwrapped either side of it: [monthGrid] and
     * [weekDays] are arithmetic on a date and are known without the database, so those cells appear at
     * once and it is only their numbers that arrive.
     */
    val dayLoads: StateFlow<Loadable<Map<LocalDate, CalendarLayout.DayLoad>>> =
        combine(_viewMode, _visibleMonth, _visibleWeek, studentFilter, clock.zones(), ::MarkerKey)
            .flatMapLatest { key ->
                val zone = key.zone
                val range = when (key.mode) {
                    ScheduleViewMode.DAY -> null
                    ScheduleViewMode.WEEK -> key.week to key.week.plusDays(DAYS_IN_WEEK)
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
                        .readyOrLoadingAfter(PAGE_TURN_GRACE)
                }
            }
            .derive(this, computation, Loadable.Loading)

    /**
     * What one page of calendar markers is *about*: which view, which page, and whose lessons.
     *
     * A named type because there are four of them now and `combine`'s `::Triple` had run out — but also
     * because [narrow] belongs beside them. The filter is applied to the tallies as well as to the list
     * under them on purpose: a cell counting lessons the list does not show is the calendar contradicting
     * the day it is a picker for.
     */
    private data class MarkerKey(
        val mode: ScheduleViewMode,
        val month: YearMonth,
        val week: LocalDate,
        val filter: StudentFilter?,
        val zone: ZoneId,
    ) {
        fun narrow(classes: List<TutoringClass>): List<TutoringClass> = classes.shownFor(filter)
    }

    /**
     * The lessons of **the week that holds what is on screen** — the visible week, which in the day and
     * month views is the selected day's (see [showPagesFor]).
     *
     * One week rather than one query per view: the views used to each assemble their own — a day for
     * «День» and «Месяц», a week for «Неделя» — so every view switch and every day tap re-keyed the query,
     * and the list blinked out for the frames the re-read took. Now switching view inside a week, and
     * stepping between its days, reads nothing; only crossing into another week does. A week of lessons
     * is a few dozen rows, and the student/subject/group join and the session collapse still happen once
     * per emission rather than once per view. The views read their day out of it with [day].
     */
    val content: StateFlow<Loadable<ScheduleContent.Week>> =
        combine(_visibleWeek, studentFilter, clock.zones(), ::Triple)
            .flatMapLatest { (week, filter, zone) ->
                val bounds = DayBounds.of(week, week.plusDays(DAYS_IN_WEEK), zone)
                combine(
                    classes.observeBetween(bounds.start, bounds.end),
                    students.observeAll(),
                    subjects.observeAll(),
                    groups.observeAll(),
                ) { readClasses, studentList, subjectList, groupList ->
                    val visibleClasses = readClasses.shownFor(filter)
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
                            .groupBy { it.start.atZone(zone).toLocalDate() }
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
                    .readyOrLoadingAfter(PAGE_TURN_GRACE)
            }
            // `ScheduleContent.Day(emptyList())` used to be the initial value, which is a *day with
            // no lessons* — so a cold open drew «Нет занятий» over the tutor's actual morning, with
            // the book-a-lesson button under it, until the query returned. See [Loadable].
            .derive(this, computation, Loadable.Loading)

    /**
     * **The day the day and month views draw** — the selected day read off [content], or, while the week
     * holding it is still being read, the day already on screen.
     *
     * Stepping from Sunday into the next week re-keys [content], and until the new week arrives the old one
     * cannot answer for Monday ([day] says so). Drawing that as Loading blinked the list out for the frames
     * the read took; keeping the previous day up does not, and is honest because the day carries its own
     * date and the screen names it by that. It is bounded by [content]'s own grace: once that withdraws the
     * old week as late, this goes to Loading with it.
     */
    val shownDay: StateFlow<Loadable<ScheduleContent.Day>> =
        combine(content, _selectedDate, ::Pair)
            .scan(Loadable.Loading as Loadable<ScheduleContent.Day>) { shown, (week, date) ->
                val asked = week.day(date)
                if (asked is Loadable.Loading && week is Loadable.Ready) shown else asked
            }
            .derive(this, computation, Loadable.Loading)

    /**
     * The wall clock, once a minute, aligned to the minute — so the mark moves as the phone's clock
     * changes rather than a stray number of seconds after this screen was opened.
     *
     * Sleeping to the next boundary rather than polling a second at a time, for the reason [today] sleeps
     * to the next midnight: the answer changes on a schedule, and it is knowable when.
     */
    private val minutes: Flow<Instant> = flow {
        while (true) {
            val instant = clock.instant()
            emit(instant)
            val nextMinute = instant.plusSeconds(SECONDS_PER_MINUTE)
                .minusNanos(instant.nano.toLong())
                .minusSeconds(instant.epochSecond.mod(SECONDS_PER_MINUTE))
            // At least a tick: a clock less than a millisecond short of the boundary truncates to a
            // zero-length sleep, and this loop would spin on it.
            delay(Duration.between(instant, nextMinute).toMillis().coerceAtLeast(1))
        }
    }

    /**
     * Where the **"you are here"** mark goes in the selected day's list: the index it sits before, or
     * null when there is no mark to draw — see [NowMarker].
     *
     * Only the day list has one, which is both views that show a single day. The week agenda
     * deliberately does not: it is a list of *days*, and a mark inside one of them would be answering a
     * question ("where am I in today") that the view is not asking.
     *
     * **Re-derived every minute, and emitted only when it moves.** The tick is what makes it true — a
     * mark computed once at composition drifts for as long as the screen is open, which on a schedule is
     * all day. What keeps it cheap is that the tick produces the *same* index for most of an hour, and a
     * `StateFlow` conflates an equal value: sixty ticks between two lessons cost sixty comparisons and no
     * recomposition at all.
     *
     * **A test that collects this must advance time by a stated amount, never `advanceUntilIdle()`** —
     * the same rule, for the same reason, as [today]: a clock has no last emission, so "until idle" never
     * arrives. `WhileSubscribed` is what keeps that off every other test.
     */
    val nowMark: StateFlow<Int?> =
        combine(shownDay, minutes) { shown, now ->
            // Among the rows that are drawn, on the day they are — [shownDay]'s date, which for the frames
            // a step into another week takes to read is still the previous day. Not gated on the view:
            // the week agenda draws no mark, so a mark computed under it is simply never read.
            val day = shown.valueOrNull() ?: return@combine null
            NowMarker.positionIn(day.rows.map { it.session }, day.date, now, zone)
        }
            .derive(this, computation, null)

    /**
     * The lessons **happening right now**, by lesson id — what the schedule badges «Идёт».
     *
     * A set of ids rather than a phase per row, for the reason [nowMark] is an index rather than a
     * re-assembled list: the tick fires every minute and a `StateFlow` conflates an equal value, so an
     * hour of ticks over an unchanged set costs sixty comparisons and no recomposition at all. Rebuilding
     * the rows each minute to carry their own phase would instead redraw the whole day, sixty times an
     * hour, for a word that changes twice.
     *
     * A **set** and not a single id, although one tutor teaching one lesson at a time is an invariant the
     * repository enforces on every write ([ClassRepository] refuses an occupied hour): a restored backup
     * is reproduced as given and is not checked for overlaps, so two rows can overlap in a database this
     * app did not write. A set says that without needing the guarantee to hold.
     *
     * Derived over the rows the active view is showing, so it covers all three: a week agenda badges the
     * running lesson in the day it belongs to, and the month's day list badges it like the day view.
     *
     * **A test that collects this must advance time by a stated amount, never `advanceUntilIdle()`** —
     * see [nowMark], which has the same clock underneath it.
     */
    val running: StateFlow<Set<Long>> =
        combine(content, minutes) { loaded, now ->
            loaded.valueOrNull()?.rowsShown.orEmpty()
                .filter { LessonPhase.of(it.session, now).isRunning }
                .mapTo(mutableSetOf()) { it.session.lessonId }
        }
            .derive(this, computation, emptySet())

    /**
     * Switches view. Opening a calendar view on a page that doesn't contain the selected day would
     * read as nothing being selected, so the page snaps back to it.
     */
    fun setViewMode(mode: ScheduleViewMode) {
        _viewMode.value = mode
        showPagesFor(_selectedDate.value)
    }

    /**
     * The today [followToday] last saw — a field rather than a local of the [today] flow, because that
     * flow stops while the schedule is off screen, and a night spent on another tab is still a night.
     */
    private var lastToday: LocalDate = _selectedDate.value

    /**
     * A selection left on today moves on with it at midnight; one the tutor browsed to stays. Without it
     * an app left open overnight opened the Day view on yesterday, with no «now» line — [today] rolled
     * over and the selection, set once at construction, did not.
     */
    private fun followToday(date: LocalDate) {
        if (date == lastToday) return
        if (_selectedDate.value == lastToday) selectDate(date)
        lastToday = date
    }

    private val arrivals = ArrivalGate()

    /**
     * Puts the schedule where a tap in the tray asked (see `AppRoot`), **once per tap**. The screen hands
     * the arrival over every time it comes back, since it leaves composition whenever a lesson or another
     * tab covers it; honouring it each time sent the tutor back to the notification's day after every
     * edit, for the rest of the process. See [Arrival].
     */
    fun arrive(arrival: Arrival<ScheduleTarget>?) {
        when (val target = arrivals.admit(arrival) ?: return) {
            ScheduleTarget.Today -> goToToday()
            is ScheduleTarget.Day -> selectDate(target.date)
            is ScheduleTarget.Lesson -> viewModelScope.launch { showLesson(target.classId) }
        }
    }

    /**
     * The lesson [classId] is part of, on the day it is on now, with a filter that would hide it lifted —
     * the sheet waits for a row, and a row the filter narrows away is never drawn. Lifting it is shown
     * rather than quiet: the filter chip goes.
     */
    private suspend fun showLesson(classId: Long) {
        val session = classes.getSessionFor(classId)
        if (session == null) {
            events.send(Event.LESSON_GONE)
            return
        }
        val filter = filterStudentId.value
        if (filter != null && session.classes.none { it.studentId == filter }) setStudentFilter(null)
        selectDate(session.start.atZone(zone).toLocalDate())
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

    /**
     * Picks the day the day list shows and the FAB books into; the calendar pages follow it.
     *
     * A lesson still waiting to have its sheet opened (see [askedSheet]) is given up: the tutor has gone
     * somewhere else, and a sheet arriving later over a page they chose would be about a tap they have
     * moved on from.
     */
    fun selectDate(date: LocalDate) {
        askedLessonId.value = null
        _selectedDate.value = date
        showPagesFor(date)
    }

    fun goToPreviousDay() = selectDate(_selectedDate.value.minusDays(1))

    fun goToNextDay() = selectDate(_selectedDate.value.plusDays(1))

    fun goToToday() = selectDate(LocalDate.now(clock))

    fun goToPreviousMonth() {
        _visibleMonth.value = _visibleMonth.value.minusMonths(1)
    }

    fun goToNextMonth() {
        _visibleMonth.value = _visibleMonth.value.plusMonths(1)
    }

    fun goToPreviousWeek() {
        _visibleWeek.value = _visibleWeek.value.minusDays(DAYS_IN_WEEK)
    }

    fun goToNextWeek() {
        _visibleWeek.value = _visibleWeek.value.plusDays(DAYS_IN_WEEK)
    }

    // Every action below takes a session, so it reads the same whether the tutor tapped a lesson for
    // one student or a whole group; the repository applies it per class and refreshes reminders once.
    //
    // Each write runs in [appScope] rather than `viewModelScope`. The sheet these are called from
    // closes as the action is tapped, and marking a lesson completed or deleting one must not be cut
    // in half because the screen went away a moment later — see [ApplicationScope].

    /**
     * Changes a whole lesson's status.
     *
     * Returning one **to the schedule** can be refused: it is the one status change that re-*occupies*
     * time. A cancelled class gives its slot back on purpose ([LessonStatus.occupiesTime]) — that is
     * what lets the tutor re-book the freed hour for somebody else — so by the time they un-cancel,
     * the hour may be held by that somebody. The *check* is the repository's, made by every way into
     * an hour ([LessonWrite]); what this screen owns is the wording, and the clash arrives as data for
     * [reportHourTaken] — through the same [timeConflict] a refused move uses, since it is the same
     * fact: this hour is taken, by that lesson.
     */
    fun setStatus(session: ClassSession, status: LessonStatus) {
        write {
            when (val changed = classes.setStatus(session, status)) {
                is LessonWrite.HourTaken -> reportHourTaken(changed.clash)
                is LessonWrite.Written -> Unit
            }
        }
    }

    /**
     * Reschedules a whole lesson to [day] at [at], unless that hour is already taken — one tutor cannot
     * teach two lessons at once, so the repository refuses a move onto an occupied slot ([LessonWrite])
     * rather than silently doing nothing, and the refusal is reported through [timeConflict]. The lesson's
     * **own** classes never stand in their own way, so nudging it by ten minutes is fine.
     *
     * **It takes a date and a wall-clock time, not an `Instant`.** «Перенести» asks the tutor for those
     * two, and an `Instant` parameter obliged the caller to turn them into one — which `MoveClassFlow` duly
     * did, inside a composable, with `ZoneId.systemDefault()` of its own. That was a third definition of
     * "which zone" (against `clock.zone` here and in `ClassEditViewModel`), on the path that changes *when*
     * a lesson happens, and it is exactly what `domain/billing/PaymentDate` was extracted to stop being possible on
     * the path that records money. The conversion crosses the seam as the values the tutor picked, and the
     * one statement of the zone is this class's.
     */
    fun moveTo(session: ClassSession, day: LocalDate, at: LocalTime) {
        val newStart = day.atTime(at).atZone(zone).toInstant()
        write {
            when (val moved = classes.move(session, newStart, now = clock.instant())) {
                is LessonWrite.HourTaken -> reportHourTaken(moved.clash)
                is LessonWrite.Written -> Unit
            }
        }
    }

    /**
     * Words a refused write for the dialog: [clash] holds the hour, and the tutor is told *which*
     * lesson that is — its group's name, or its student's. The naming stays here because it is this
     * screen's job; the refusal itself is no longer its to make.
     */
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
     *
     * A flow per sheet rather than a `StateFlow` here: it is keyed on the lesson the sheet was opened for,
     * lives exactly as long as that sheet, and the schedule's own state has no use for it. Off the main
     * thread like everything derived here, since it walks the lessons leading up to this one.
     */
    fun homeworkAt(session: ClassSession): Flow<List<SheetHomework>> =
        combine(
            homework.observeAttachedTo(session),
            students.observeAll(),
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

    /** One lesson write, a failure reported as [Event.ACTION_FAILED] — see `ui/common/state/Writes.kt`. */
    private fun write(block: suspend () -> Unit) = appScope.write(events, Event.ACTION_FAILED, block = block)

    /** Points both calendar pages at [date], so whichever view opens next contains it. */
    private fun showPagesFor(date: LocalDate) {
        _visibleMonth.value = YearMonth.from(date)
        _visibleWeek.value = CalendarLayout.startOfWeek(date, weekStart)
    }

    /**
     * Turns one day's classes into schedule rows: the members' classes of a group lesson collapse
     * into a single row naming them in [ScheduleRow.members]. Shared by the day list and the week
     * agenda, so a group lesson reads as one lesson in both — not as one row per member in one view
     * and a single row in the other.
     */
    private fun sessionRows(dayClasses: List<TutoringClass>, captions: Captions): List<ScheduleRow> =
        SessionRules.sessions(dayClasses).map { ScheduleRow.of(it, captions) }

    /** What these rows come to — [DaySummary] over the lessons they stand for. */
    private fun List<ScheduleRow>.summarised(): DaySummary = DaySummary.of(map { it.session })

    private companion object {
        /** A week's span as a day count, for the date arithmetic that pages the agenda. */
        const val DAYS_IN_WEEK = CalendarLayout.DAYS_IN_WEEK.toLong()

        /**
         * How long the previous page's answer may stand while the next is read. Long enough to cover a
         * re-query on a slow phone, short enough that a stuck one is never mistaken for an answer.
         */
        val PAGE_TURN_GRACE = 250.milliseconds

        /** For the minute the "now" mark is aligned to. */
        const val SECONDS_PER_MINUTE = 60L
    }
}
