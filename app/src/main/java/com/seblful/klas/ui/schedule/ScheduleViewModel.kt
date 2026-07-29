package com.seblful.klas.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.di.Computation
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.CalendarLayout
import com.seblful.klas.domain.ClassSession
import com.seblful.klas.domain.GroupRules
import com.seblful.klas.domain.Scheduling
import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.ui.common.UiEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
}

/**
 * Which of the four warnings deleting a lesson owes, and how many classes go with it.
 *
 * Two facts decide it, and both are about the lesson rather than about the screen: whether any of its
 * classes has **billed**, because deleting a completed lesson takes recognized income with it, and
 * whether it is a group's, because then it is several students' classes at once. `ScheduleScreen` used
 * to read both off the session in a composable and pick a string from a four-armed `when` — the picking
 * being the decision, and the strings being all that actually belongs there.
 */
data class DeleteWarning(val billed: Boolean, val group: Boolean, val classes: Int) {
    companion object {
        fun of(session: ClassSession): DeleteWarning = DeleteWarning(
            billed = session.classes.any { it.status == ClassStatus.COMPLETED },
            group = session.isGroup,
            classes = session.size,
        )
    }
}

/** One day's section of the week agenda. Only days that actually have classes get one. */
data class WeekSection(val date: LocalDate, val rows: List<ScheduleRow>)

/**
 * How the schedule presents its classes: one day at a time, a whole week's agenda, or a month grid
 * over the selected day's list.
 */
enum class ScheduleViewMode { DAY, WEEK, MONTH }

/**
 * The lessons the schedule body is currently showing — one shape per way of showing them.
 *
 * There is deliberately one of these rather than a flow per view: the schedule assembles only what
 * the active view needs, so the other views hold no live query. The accessor a view doesn't use is
 * empty by construction, not merely stale.
 */
sealed interface ScheduleContent {

    /** The selected day's rows — what the day and month views both show. Empty for a week. */
    val rows: List<ScheduleRow> get() = emptyList()

    /** The visible week's sections, earliest day first. Empty for a single day. */
    val sections: List<WeekSection> get() = emptyList()

    data class Day(override val rows: List<ScheduleRow>) : ScheduleContent

    data class Week(override val sections: List<WeekSection>) : ScheduleContent
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now(clock))

    /**
     * The month the calendar page shows. Kept separate from [selectedDate] so paging through months
     * only browses — it never silently re-points the day list at a day the user did not pick.
     */
    private val _visibleMonth = MutableStateFlow(YearMonth.from(_selectedDate.value))
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    /** The Monday of the week the agenda shows. Browses independently of [selectedDate], as above. */
    private val _visibleWeek = MutableStateFlow(CalendarLayout.startOfWeek(_selectedDate.value))
    val visibleWeek: StateFlow<LocalDate> = _visibleWeek.asStateFlow()

    private val _viewMode = MutableStateFlow(ScheduleViewMode.DAY)
    val viewMode: StateFlow<ScheduleViewMode> = _viewMode.asStateFlow()

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
            .flowOn(computation)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val monthGrid: StateFlow<CalendarLayout.MonthGrid> =
        _visibleMonth.map { CalendarLayout.monthGrid(it) }
            .flowOn(computation)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                CalendarLayout.monthGrid(_visibleMonth.value),
            )

    /** The seven days the week strip shows. */
    val weekDays: StateFlow<List<LocalDate>> =
        _visibleWeek.map { CalendarLayout.week(it) }
            .flowOn(computation)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                CalendarLayout.week(_visibleWeek.value),
            )

    /**
     * Per-day markers for the cells the active view is showing — the month page (including the
     * neighbouring-month days padding its first and last rows, so those cells aren't misleadingly
     * blank) or the week strip. Scoped to the active view so browsing far from the selected day
     * never widens the query, and so the day view pays for no markers at all.
     */
    val dayLoads: StateFlow<Map<LocalDate, CalendarLayout.DayLoad>> =
        combine(_viewMode, _visibleMonth, _visibleWeek, ::Triple)
            .flatMapLatest { (mode, month, week) ->
                val range = when (mode) {
                    ScheduleViewMode.DAY -> null
                    ScheduleViewMode.WEEK -> week to week.plusDays(DAYS_IN_WEEK)
                    ScheduleViewMode.MONTH ->
                        CalendarLayout.monthGrid(month).let { it.firstDate to it.endExclusive }
                }
                if (range == null) {
                    flowOf(emptyMap())
                } else {
                    classes.observeBetween(
                        range.first.atStartOfDay(zone).toInstant(),
                        range.second.atStartOfDay(zone).toInstant(),
                    ).map { CalendarLayout.dayLoads(it, zone) }
                }
            }
                .flowOn(computation)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * The lessons the **active view** needs, and no others.
     *
     * The day and month views both show the selected day; the week view shows the whole visible
     * week. Keying one assembly on [viewMode] is what stops the views each keeping a live query
     * running while off-screen: switching view swaps the query, and the student/subject/group join
     * and the session collapse happen once rather than once per view. This is the same rule
     * [dayLoads] already follows for calendar markers.
     */
    val content: StateFlow<ScheduleContent> =
        combine(_viewMode, _selectedDate, _visibleWeek, ::Triple)
            .flatMapLatest { (mode, date, week) ->
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
                ) { visibleClasses, studentList, subjectList, groupList ->
                    val studentsById = studentList.associateBy { it.id }
                    val subjectsById = subjectList.associateBy { it.id }
                    val groupsById = groupList.associateBy { it.id }
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
                                    WeekSection(
                                        date = day,
                                        rows = sessionRows(dayClasses, studentsById, subjectsById, groupsById),
                                    )
                                },
                        )
                    } else {
                        ScheduleContent.Day(
                            sessionRows(visibleClasses, studentsById, subjectsById, groupsById),
                        )
                    }
                }
            }
                .flowOn(computation)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleContent.Day(emptyList()))

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
     * Returning one **to the schedule** is checked against the hour first, because it is the one
     * status change that re-*occupies* time. A cancelled class gives its slot back on purpose
     * ([ClassStatus.occupiesTime]) — that is what lets the tutor re-book the freed hour for somebody
     * else — so by the time they un-cancel, the hour may be held by that somebody. Without this the
     * one-tutor-one-lesson rule that [move] and booking both enforce had a way in behind it: the
     * schedule simply drew two lessons at 10:00, and neither the form nor the sheet had said no.
     *
     * The refusal is reported through the same [timeConflict] a refused move uses, since it is the
     * same fact — this hour is taken, by that lesson. Every other status change occupies no new time
     * and goes straight to the write.
     */
    fun setStatus(session: ClassSession, status: ClassStatus) {
        if (status != ClassStatus.SCHEDULED) {
            write { classes.setStatus(session, status) }
            return
        }
        viewModelScope.launch {
            if (refuseIfTaken(session, session.start)) return@launch
            write { classes.setStatus(session, status) }
        }
    }

    /**
     * Reschedules a whole lesson, unless that hour is already taken — one tutor cannot teach two
     * lessons at once, so a move onto an occupied slot is refused and reported through [timeConflict]
     * rather than silently doing nothing. The lesson's **own** classes are ignored, so nudging it by
     * ten minutes doesn't clash with where it currently is.
     */
    fun move(session: ClassSession, newStart: Instant) {
        viewModelScope.launch {
            // The refusal is this screen's to report — [_timeConflict] is state the dialog reads — so
            // the check stays here and only the move itself is handed to the scope that outlives it.
            if (refuseIfTaken(session, newStart)) return@launch
            write { classes.move(session, newStart) }
        }
    }

    /**
     * Whether [session] starting at [start] would collide with another lesson — and, when it would,
     * records which one in [timeConflict] for the dialog to read.
     *
     * The lesson's **own** classes are excluded by id, so a lesson never stands in its own way.
     *
     * A session whose stored duration is not positive is reported as free rather than asked about.
     * Such a row should not exist (`BackupIntegrity` refuses one), but `Scheduling.Slot` *requires* a
     * positive length, so constructing one here would throw — inside a `launch`, which is a crash
     * rather than a refusal. It occupies no time by its own arithmetic anyway, so nothing can be in
     * its way; the read path takes the same view (`Scheduling.conflicts` skips such rows).
     */
    private suspend fun refuseIfTaken(session: ClassSession, start: Instant): Boolean {
        val minutes = session.first.durationMinutes
        if (minutes <= 0) return false
        val slot = Scheduling.Slot(start, minutes)
        val clash = classes.conflictsWith(slot, session.classes.map { it.id }.toSet()).firstOrNull()
            ?: return false
        _timeConflict.value = TimeConflict.of(
            clash = clash,
            groupName = clash.groupId?.let { groups.getById(it)?.name },
            studentName = students.getById(clash.first.studentId)?.name,
        )
        return true
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
        _visibleWeek.value = CalendarLayout.startOfWeek(date)
    }

    /**
     * Turns one day's classes into schedule rows: the members' classes of a group lesson collapse
     * into a single row naming them in [ScheduleRow.members]. Shared by the day list and the week
     * agenda, so a group lesson reads as one lesson in both — not as one row per member in one view
     * and a single row in the other.
     */
    private fun sessionRows(
        dayClasses: List<TutoringClass>,
        students: Map<Long, Student>,
        subjects: Map<Long, Subject>,
        groups: Map<Long, Group>,
    ): List<ScheduleRow> =
        GroupRules.sessions(dayClasses).map { session ->
            val subjectName = subjects[session.first.subjectId]?.name ?: ""
            if (session.isGroup) {
                ScheduleRow(
                    session = session,
                    title = groups[session.groupId]?.name ?: "",
                    subjectName = subjectName,
                    members = session.classes.map { students[it.studentId]?.name ?: "" },
                )
            } else {
                ScheduleRow(
                    session = session,
                    title = students[session.first.studentId]?.name ?: "",
                    subjectName = subjectName,
                )
            }
        }

    private companion object {
        /** A week's span as a day count, for the date arithmetic that pages the agenda. */
        const val DAYS_IN_WEEK = CalendarLayout.DAYS_IN_WEEK.toLong()
    }
}
