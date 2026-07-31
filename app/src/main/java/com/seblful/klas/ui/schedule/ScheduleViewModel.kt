package com.seblful.klas.ui.schedule

import androidx.lifecycle.ViewModel
import com.seblful.klas.di.Computation
import com.seblful.klas.di.FirstDayOfWeek
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.LessonWrite
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.CalendarLayout
import com.seblful.klas.domain.Captions
import com.seblful.klas.domain.ClassSession
import com.seblful.klas.domain.DaySummary
import com.seblful.klas.domain.GroupRules
import com.seblful.klas.domain.NowMarker
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.UiEvents
import com.seblful.klas.ui.common.derive
import com.seblful.klas.ui.common.hold
import com.seblful.klas.ui.common.ready
import com.seblful.klas.ui.common.valueOrNull
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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

/**
 * One row of the day's schedule: a lesson, which is either one student's class or a whole group
 * session.
 *
 * [members] names a group lesson's students so the row can say who is in the room. Names only, and
 * not a row per member: a lesson is acted on as a whole here, and **who** is in one is changed on
 * the lesson's own form — the schedule's actions are about the lesson's time and status.
 */
data class ScheduleRow(
    val session: ClassSession,
    /** Group name for a group lesson, student name otherwise. */
    val title: String,
    val subjectName: String,
    /** A group lesson's students, in class order. Empty for an individual lesson — [title] is them. */
    val members: List<String> = emptyList(),
) {
    val klass: TutoringClass get() = session.first
    val isGroup: Boolean get() = session.isGroup
    val classes: List<TutoringClass> get() = session.classes

    /** What deleting this lesson has to warn about — see [DeleteWarning]. */
    val deleteWarning: DeleteWarning get() = DeleteWarning.of(session)

    /**
     * The statuses this lesson may be moved to — what the sheet's segmented control enables, and
     * what [canSwipeComplete] is gated on. [GroupRules.sessionTransitions] asked once, here, where
     * a test can reach it; it used to be asked by two composables, so the rule gating the product's
     * highest-frequency gesture could be exercised only by swiping a card on a phone.
     */
    val statusChoices: Set<LessonStatus> get() = GroupRules.sessionTransitions(session)

    /**
     * Whether the swipe-to-complete gesture is offered at all: completing must be legal for some
     * member, so the gesture can never do what the sheet's menu would refuse.
     *
     * It cannot contradict the card's badge, which is worth knowing rather than guarding: only a
     * *scheduled* class may be completed, and a lesson with a scheduled member reads «Запланировано»
     * ([ClassSession.status]). So a lesson badged «Проведено» — including a taught one whose absentee
     * was cancelled afterwards — offers no swipe, because reinstating that member is a correction to
     * their class and not a thing done to the whole lesson.
     */
    val canSwipeComplete: Boolean get() = LessonStatus.COMPLETED in statusChoices

    /**
     * Whether the card recedes — dimmed, its time struck through. A cancelled lesson is a record,
     * not a plan, and the question a glance at the day answers is "how full is it": a lesson whose
     * every class was called off gave its hour back. One whose members disagree keeps full strength,
     * because some of it did happen — which is exactly what [ClassSession.status] answers.
     */
    val recedes: Boolean get() = session.status == LessonStatus.CANCELLED

    companion object {
        /**
         * [session] captioned by [captions] — the whole of turning a stored lesson into a drawable row.
         *
         * Here rather than in the view model's private helper because the rule it carries is small,
         * shared and easy to get subtly wrong: a group lesson is titled by its **group** and names its
         * members, an individual one is titled by its **student** and names nobody. It was reachable only
         * through 1,352 lines of `ScheduleViewModelTest` before, being private to the view model.
         */
        fun of(session: ClassSession, captions: Captions): ScheduleRow = ScheduleRow(
            session = session,
            title = captions.titleOf(session),
            subjectName = captions.subjectOf(session),
            members = captions.membersOf(session),
        )
    }
}

/**
 * Which of the four warnings deleting a lesson owes, and how many classes go with it.
 *
 * Two facts decide it, and both are about the lesson rather than about the screen: whether any of its
 * classes has **billed**, because deleting a completed lesson takes recognized income with it, and
 * whether it is a group's, because then it is several students' classes at once.
 *
 * **[kind] is the decision; the four strings are the screen's.** This type used to publish the two facts
 * instead, and the four-armed `when` combining them stayed in the composable — so what had moved out was
 * the two extractions, not the *picking*, which is the part that can be got wrong and the part no test
 * could reach. Named cases make the screen's `when` exhaustive over an answer somebody else gave, which
 * is the shape `SaveIntent` and `SubjectNotice` already have.
 */
data class DeleteWarning(val kind: Kind, val classes: Int) {

    /** What deleting this lesson costs, in the four combinations that read differently. */
    enum class Kind {
        /** One student's class, nothing billed: the ordinary case. */
        PLAIN,

        /** One student's class that has billed — deleting it removes recognized income. */
        BILLED,

        /** A group's lesson, nothing billed: several students' classes at once. */
        GROUP,

        /** A group's lesson that has billed: both at once, and the only case naming both. */
        BILLED_GROUP,
    }

    companion object {
        fun of(session: ClassSession): DeleteWarning {
            val billed = session.classes.any { it.status == LessonStatus.COMPLETED }
            return DeleteWarning(
                kind = when {
                    billed && session.isGroup -> Kind.BILLED_GROUP
                    billed -> Kind.BILLED
                    session.isGroup -> Kind.GROUP
                    else -> Kind.PLAIN
                },
                classes = session.size,
            )
        }
    }
}

/**
 * **Why the schedule has nothing to show** — and therefore what, if anything, to offer instead.
 *
 * One answer for every view, because the two that ask were answering it separately and had already
 * drifted: the day and month views weighed three cases and the week view two, so a tutor with no students
 * met «Добавить ученика» on «День» and a dead end on «Неделя». An empty list carrying no way out of
 * itself is the defect `EmptyState` exists to prevent, and it was shipping on one of the app's three
 * busiest surfaces.
 *
 * Both copies lived in composables, so neither could be tested; this can be, and is.
 */
enum class ScheduleEmpty {
    /**
     * A filter is on. **Takes precedence over the other two** — a schedule narrowed to one student is not
     * a quiet one, and the way out is the chip rather than anything on the page. It offers no action
     * deliberately: «Записать занятие» here would book into the day being *looked at* and not for the
     * student being looked *for*, which is not what the tutor asked. The searchable lists follow the same
     * rule — a list empty because nothing matched says so and offers nothing.
     */
    FILTERED,

    /** Genuinely nothing booked, and something can be: the ordinary case, offering to book one. */
    QUIET,

    /**
     * Nothing booked and nothing bookable — there is no student, or no priced subject, yet. Naming the
     * emptiness and stopping there would leave the tutor to find the step that unblocks it, so this
     * offers that step instead.
     */
    NEEDS_STUDENT,
    ;

    companion object {
        /** @param filtered whether the schedule is narrowed to one student — see [StudentFilter]. */
        fun of(filtered: Boolean, canAddClass: Boolean): ScheduleEmpty = when {
            filtered -> FILTERED
            canAddClass -> QUIET
            else -> NEEDS_STUDENT
        }
    }
}

/**
 * One day's section of the week agenda. Only days that actually have classes get one.
 *
 * @param summary what the day adds up to — see [DaySummary]. Carried rather than computed by the header
 *   that prints it, because it sums money over every class of every lesson and belongs in the derivation
 *   that already runs off the main thread.
 */
data class WeekSection(
    val date: LocalDate,
    val rows: List<ScheduleRow>,
    val summary: DaySummary,
)

/**
 * How the schedule presents its classes: one day at a time, a whole week's agenda, or a month grid
 * over the selected day's list.
 */
enum class ScheduleViewMode { DAY, WEEK, MONTH }

/**
 * The schedule narrowed to one student — who, and the name the chip saying so is drawn from.
 *
 * **The name is carried rather than looked up by the screen**, and that is the point of the type: it is
 * read from every student rather than the active ones, so archiving somebody the schedule is currently
 * filtered by leaves the chip naming them instead of going blank. The same value is what the lesson
 * queries narrow on, so the chip and the list cannot disagree about whether a filter is applied — a
 * filtered calendar looking empty for no visible reason is the failure this whole feature has to avoid.
 *
 * Resolving to null when the id matches nobody is deliberate and is why both read this and not the id: a
 * restored backup can take a student away, and an empty schedule filtered by a person who no longer
 * exists would have no way out.
 */
data class StudentFilter(val studentId: Long, val name: String)

/**
 * The lessons the schedule body is currently showing — one shape per way of showing them.
 *
 * There is deliberately one of these rather than a flow per view: the schedule assembles only what
 * the active view needs, so the other views hold no live query. The accessor a view doesn't use is
 * empty by construction, not merely stale.
 */
sealed interface ScheduleContent {

    /**
     * The selected day's rows and what they add up to — what the day and month views both show.
     */
    data class Day(
        val rows: List<ScheduleRow>,
        val summary: DaySummary,
    ) : ScheduleContent

    /** The visible week's sections, earliest day first; each day carries its own summary. */
    data class Week(val sections: List<WeekSection>) : ScheduleContent
}

/**
 * The selected day's content, and **[Loadable.Loading] while what has arrived is the week's.**
 *
 * This is the second half of the loaded-yet problem, and `Loadable` alone could not see it: switching view
 * re-keys the query, and a `StateFlow` holds its previous value meanwhile — so the state was `Ready`, about
 * the view the tutor had just left. `ScheduleContent` used to help it lie: `rows`, `sections` and `summary`
 * were all defaulted on the interface, so a `Week` answered "what are this day's lessons" with
 * `emptyList()` and a `Day` answered "what are this week's days" the same way. Tapping «Неделя» therefore
 * drew «Нет занятий на этой неделе» over a full week, and «День» drew «Нет занятий» with the
 * book-a-lesson button under it — the exact defect [Loadable] was introduced to remove, one frame later
 * and by a different route. The defaults are gone and the narrowing says so instead: content about another
 * view is not an answer, it is the absence of one.
 *
 * Paging is the same question changing, and is covered by the same rule — the day's flow re-emits
 * [Loadable.Loading] when its key moves, so yesterday's lessons are never listed under today's date, and
 * an empty Tuesday cannot say «Нет занятий» about a busy Wednesday.
 */
fun Loadable<ScheduleContent>.day(): Loadable<ScheduleContent.Day> = narrowed()

/** The visible week's content, and [Loadable.Loading] while what has arrived is a single day's. */
fun Loadable<ScheduleContent>.week(): Loadable<ScheduleContent.Week> = narrowed()

private inline fun <reified T : ScheduleContent> Loadable<ScheduleContent>.narrowed(): Loadable<T> =
    when (this) {
        Loadable.Loading -> Loadable.Loading
        is Loadable.Ready -> (value as? T)?.let { Loadable.Ready(it) } ?: Loadable.Loading
    }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val classes: ClassRepository,
    private val students: StudentRepository,
    subjects: SubjectRepository,
    private val groups: GroupRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
    private val clock: Clock,
    /** The phone's own — see [FirstDayOfWeek]. Everything the calendar lays out is anchored on it. */
    @param:FirstDayOfWeek private val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) : ViewModel() {

    // From the clock, so the one thing that watches the date can be moved in a test. See TimeModule.
    private val zone: ZoneId = clock.zone

    private val _selectedDate = MutableStateFlow(LocalDate.now(clock))
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /**
     * Today — **re-emitted when the date changes**, not read once.
     *
     * The screen used to compute `LocalDate.now()` in its own body, which is evaluated per composition
     * and then never again on its own. An app left open overnight — which is exactly what a phone on a
     * desk does — went on marking yesterday as today: the highlighted cell, the day headers and the
     * «сегодня» affordance were all a day behind until something else happened to recompose.
     *
     * Sleeping until the next local midnight rather than polling: the answer changes once a day and it
     * is knowable when. `delay` is virtual time under a test scheduler, so the rollover is testable
     * without waiting for it.
     *
     * **A test that collects this must advance time by a stated amount, never `advanceUntilIdle()`.**
     * A clock has no last emission, so there is always another `delay` on the scheduler and "until idle"
     * never arrives — it hangs rather than failing. `WhileSubscribed` is what keeps that off every other
     * test: nothing is collecting this unless a test says so.
     */
    val today: StateFlow<LocalDate> = flow {
        while (true) {
            val date = LocalDate.now(clock)
            emit(date)
            val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant()
            // At least a tick, so a clock already past midnight cannot spin on a zero-length sleep.
            delay(Duration.between(clock.instant(), nextMidnight).toMillis().coerceAtLeast(1))
        }
    }.hold(this, LocalDate.now(clock))

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

    private val _filterStudentId = MutableStateFlow<Long?>(null)

    /**
     * Whose lessons the schedule is showing, or null for everyone's — see [StudentFilter].
     *
     * Declared **before** [dayLoads] and [content] because both narrow on it: properties initialise in
     * declaration order, and a flow combined into them before it exists is a null reference at
     * construction rather than at first collection.
     */
    val studentFilter: StateFlow<StudentFilter?> =
        combine(_filterStudentId, students.observeAll()) { id, all ->
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
        _filterStudentId.value = studentId
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
    }

    val events = UiEvents<Event>()

    /**
     * A class can only be booked once there is at least one student and one priced subject — a
     * class needs both, so the schedule's add button stays disabled until they exist.
     */
    val canAddClass: StateFlow<Boolean> =
        combine(students.observeActive(), subjects.observeAll()) { studentList, subjectList ->
            studentList.isNotEmpty() && subjectList.isNotEmpty()
        }
            .derive(this, computation, false)

    /**
     * What an empty schedule means right now — asked by whichever view finds itself with nothing to show,
     * so day, month and week cannot answer it differently. See [ScheduleEmpty].
     *
     * A `StateFlow` rather than something the empty branch computes, because both facts behind it are
     * already live here and neither is a fact about drawing.
     */
    val emptyReason: StateFlow<ScheduleEmpty> =
        combine(studentFilter, canAddClass) { filter, bookable ->
            ScheduleEmpty.of(filtered = filter != null, canAddClass = bookable)
        }
            .derive(this, computation, ScheduleEmpty.of(filtered = false, canAddClass = false))

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
        combine(_viewMode, _visibleMonth, _visibleWeek, studentFilter, ::MarkerKey)
            .flatMapLatest { key ->
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
                    classes.observeBetween(
                        range.first.atStartOfDay(zone).toInstant(),
                        range.second.atStartOfDay(zone).toInstant(),
                    )
                        .map { CalendarLayout.dayLoads(key.narrow(it), zone) }
                        .ready()
                        // Paging is a new question too: the tallies are keyed by date, so last month's
                        // map has no entry for any day of this one — every cell would read as free.
                        .onStart { emit(Loadable.Loading) }
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
    ) {
        fun narrow(classes: List<TutoringClass>): List<TutoringClass> =
            filter?.let { GroupRules.lessonsWith(classes, it.studentId) } ?: classes
    }

    /**
     * The lessons the **active view** needs, and no others.
     *
     * The day and month views both show the selected day; the week view shows the whole visible
     * week. Keying one assembly on [viewMode] is what stops the views each keeping a live query
     * running while off-screen: switching view swaps the query, and the student/subject/group join
     * and the session collapse happen once rather than once per view. This is the same rule
     * [dayLoads] already follows for calendar markers.
     */
    val content: StateFlow<Loadable<ScheduleContent>> =
        combine(_viewMode, _selectedDate, _visibleWeek, studentFilter, ::ContentKey)
            .flatMapLatest { key ->
                val (mode, date, week, filter) = key
                val isWeek = mode == ScheduleViewMode.WEEK
                val from = if (isWeek) week else date
                val to = if (isWeek) week.plusDays(DAYS_IN_WEEK) else date.plusDays(1)
                combine(
                    classes.observeBetween(
                        from.atStartOfDay(zone).toInstant(),
                        to.atStartOfDay(zone).toInstant(),
                    ),
                    students.observeAll(),
                    subjects.observeAll(),
                    groups.observeAll(),
                ) { readClasses, studentList, subjectList, groupList ->
                    // Whole **lessons** the student is in, never their class pulled out of one — see
                    // [GroupRules.lessonsWith]. A filter changes what is on screen, not what a lesson is,
                    // so the sheet's actions still act on the session the lesson consists of.
                    val visibleClasses =
                        filter?.let { GroupRules.lessonsWith(readClasses, it.studentId) } ?: readClasses
                    // Built once per emission and asked per lesson — the three `associateBy` maps this
                    // replaces, plus the rule about which name titles a lesson. See [Captions].
                    val captions = Captions.of(studentList, subjectList, groupList)
                    if (isWeek) {
                        // Sessions are collapsed per day, not across the week: grouping first would
                        // let a group lesson's members land in one row even if the days differed.
                        // Days without a class are left out — an agenda reads better as a list of
                        // what's on than a wall of empty days.
                        ScheduleContent.Week(
                            visibleClasses
                                .groupBy { it.start.atZone(zone).toLocalDate() }
                                .toSortedMap()
                                .map { (day, dayClasses) ->
                                    val rows = sessionRows(dayClasses, captions)
                                    WeekSection(date = day, rows = rows, summary = rows.summarised())
                                },
                        )
                    } else {
                        val rows = sessionRows(visibleClasses, captions)
                        ScheduleContent.Day(rows = rows, summary = rows.summarised())
                    }
                }
                    .ready()
                    // **A new question un-answers the old one.** Inside the `flatMapLatest`, so every
                    // change of key — the view, the day, the week, the filter — goes back to Loading
                    // rather than leaving the previous answer on screen as though it were this one's.
                    // Without it, `Ready` outlived the question it answered: tapping «Неделя» kept a
                    // single day's content, whose `sections` were empty, and the agenda said the week
                    // was free. See [day] for the whole account.
                    .onStart { emit(Loadable.Loading) }
            }
                // `ScheduleContent.Day(emptyList())` used to be the initial value, which is a *day with
                // no lessons* — so a cold open drew «Нет занятий» over the tutor's actual morning, with
                // the book-a-lesson button under it, until the query returned. See [Loadable].
                .derive(this, computation, Loadable.Loading)

    /**
     * What one assembly of the schedule body is *about*: which view, which day or week, and whose
     * lessons. [MarkerKey]'s counterpart, kept separate because the two views page independently — the
     * body follows the **selected day** while the markers follow the visible month.
     */
    private data class ContentKey(
        val mode: ScheduleViewMode,
        val date: LocalDate,
        val week: LocalDate,
        val filter: StudentFilter?,
    )

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
        combine(content, _selectedDate, minutes) { loaded, date, now ->
            // **Narrowed, not gated on the view.** This used to take `_viewMode` as a fourth source and
            // return null for a week, because `ScheduleContent.Week` answered `rows` with an empty list by
            // default — and an empty list on a day that *is* today answers 0, "the mark goes at the top",
            // so the flow held a mark all through a week view that never drew one. With the defaults gone,
            // the week's content simply is not a day's and there is nothing to gate: see [day].
            val rows = loaded.day().valueOrNull()?.rows ?: return@combine null
            NowMarker.positionIn(rows.map { it.session }, date, now, zone)
        }
            .derive(this, computation, null)

    /**
     * Switches view. Opening a calendar view on a page that doesn't contain the selected day would
     * read as nothing being selected, so the page snaps back to it.
     */
    fun setViewMode(mode: ScheduleViewMode) {
        _viewMode.value = mode
        showPagesFor(_selectedDate.value)
    }

    /** Picks the day the day list shows and the FAB books into; the calendar pages follow it. */
    fun selectDate(date: LocalDate) {
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
     * a lesson happens, and it is exactly what `domain/PaymentDate` was extracted to stop being possible on
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
     * Runs one lesson write in the application scope, reporting a failure as [Event.ACTION_FAILED]
     * rather than letting it escape.
     *
     * The scope is the point: the sheet these are tapped in closes as the action is tapped, so a write
     * left in `viewModelScope` could be cut in half by the screen going away. What that scope does
     * *not* give is a reply — a plain `appScope.launch` reports a failure only to the scope's handler,
     * which records it to the diagnostics log and, quite correctly, says nothing to anybody. So a
     * failed status change looked exactly like a status change that had not been tapped.
     *
     * [runCatchingCancellable] rather than `runCatching`: a cancelled write is not a failed one, and
     * telling the tutor an action failed when the process was being torn down would be a lie.
     */
    private fun write(block: suspend () -> Unit) {
        appScope.launch {
            runCatchingCancellable { block() }.onFailure { events.send(Event.ACTION_FAILED) }
        }
    }

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
        GroupRules.sessions(dayClasses).map { ScheduleRow.of(it, captions) }

    /** What these rows come to — [DaySummary] over the lessons they stand for. */
    private fun List<ScheduleRow>.summarised(): DaySummary = DaySummary.of(map { it.session })

    private companion object {
        /** A week's span as a day count, for the date arithmetic that pages the agenda. */
        const val DAYS_IN_WEEK = CalendarLayout.DAYS_IN_WEEK.toLong()

        /** For the minute the "now" mark is aligned to. */
        const val SECONDS_PER_MINUTE = 60L
    }
}
