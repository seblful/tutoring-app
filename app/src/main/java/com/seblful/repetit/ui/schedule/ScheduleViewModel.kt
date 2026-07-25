package com.seblful.repetit.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.repository.ClassRepository
import com.seblful.repetit.data.repository.GroupRepository
import com.seblful.repetit.data.repository.StudentRepository
import com.seblful.repetit.data.repository.SubjectRepository
import com.seblful.repetit.domain.CalendarLayout
import com.seblful.repetit.domain.ClassSession
import com.seblful.repetit.domain.GroupRules
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Group
import com.seblful.repetit.domain.model.Student
import com.seblful.repetit.domain.model.Subject
import com.seblful.repetit.domain.model.TutoringClass
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * One row of the day's schedule: a lesson, which is either one student's class or a whole group
 * session. [members] is filled for a group row — each entry is the same row shape for one member's
 * class, so tapping a member re-uses the very same actions on just their class (that is how "this
 * one student didn't come" stays expressible without a second kind of screen).
 */
data class ScheduleRow(
    val session: ClassSession,
    /** Group name for a group lesson, student name otherwise. */
    val title: String,
    val subjectName: String,
    val members: List<ScheduleRow> = emptyList(),
) {
    val klass: TutoringClass get() = session.first
    val isGroup: Boolean get() = session.isGroup
    val classes: List<TutoringClass> get() = session.classes
}

/** One day's section of the week agenda. Only days that actually have classes get one. */
data class WeekSection(val date: LocalDate, val rows: List<ScheduleRow>)

/**
 * How the schedule presents its classes: one day at a time, a whole week's agenda, or a month grid
 * over the selected day's list.
 */
enum class ScheduleViewMode { DAY, WEEK, MONTH }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val classes: ClassRepository,
    students: StudentRepository,
    subjects: SubjectRepository,
    groups: GroupRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val _selectedDate = MutableStateFlow(LocalDate.now(zone))
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

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
     * A class can only be booked once there is at least one student and one priced subject — a
     * class needs both, so the schedule's add button stays disabled until they exist.
     */
    val canAddClass: StateFlow<Boolean> =
        combine(students.observeActive(), subjects.observeAll()) { studentList, subjectList ->
            studentList.isNotEmpty() && subjectList.isNotEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val monthGrid: StateFlow<CalendarLayout.MonthGrid> =
        _visibleMonth.map { CalendarLayout.monthGrid(it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                CalendarLayout.monthGrid(_visibleMonth.value),
            )

    /** The seven days the week strip shows. */
    val weekDays: StateFlow<List<LocalDate>> =
        _visibleWeek.map { CalendarLayout.week(it) }
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
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * The visible week's classes grouped by day, earliest first. Days without a class are left out
     * — an agenda of the whole week reads better as a list of what's on than a wall of empty days.
     */
    val weekSections: StateFlow<List<WeekSection>> =
        _visibleWeek.flatMapLatest { week ->
            combine(
                classes.observeBetween(
                    week.atStartOfDay(zone).toInstant(),
                    week.plusDays(DAYS_IN_WEEK).atStartOfDay(zone).toInstant(),
                ),
                students.observeAll(),
                subjects.observeAll(),
                groups.observeAll(),
            ) { weekClasses, studentList, subjectList, groupList ->
                // Sessions are collapsed per day, not across the week: grouping first would let a
                // group lesson's members land in one row even if the days somehow differed.
                weekClasses
                    .groupBy { it.start.atZone(zone).toLocalDate() }
                    .toSortedMap()
                    .map { (date, dayClasses) ->
                        WeekSection(
                            date = date,
                            rows = sessionRows(
                                dayClasses,
                                studentList.associateBy { it.id },
                                subjectList.associateBy { it.id },
                                groupList.associateBy { it.id },
                            ),
                        )
                    }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rows: StateFlow<List<ScheduleRow>> =
        _selectedDate.flatMapLatest { date ->
            val from = date.atStartOfDay(zone).toInstant()
            val to = date.plusDays(1).atStartOfDay(zone).toInstant()
            combine(
                classes.observeBetween(from, to),
                students.observeAll(),
                subjects.observeAll(),
                groups.observeAll(),
            ) { dayClasses, studentList, subjectList, groupList ->
                sessionRows(
                    dayClasses,
                    studentList.associateBy { it.id },
                    subjectList.associateBy { it.id },
                    groupList.associateBy { it.id },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun goToToday() = selectDate(LocalDate.now(zone))

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

    fun setStatus(session: ClassSession, status: ClassStatus) {
        viewModelScope.launch { classes.setStatusForSession(session, status) }
    }

    fun move(session: ClassSession, newStart: Instant) {
        viewModelScope.launch { classes.moveSession(session, newStart) }
    }

    fun delete(session: ClassSession) {
        viewModelScope.launch { classes.deleteSession(session) }
    }

    /** Points both calendar pages at [date], so whichever view opens next contains it. */
    private fun showPagesFor(date: LocalDate) {
        _visibleMonth.value = YearMonth.from(date)
        _visibleWeek.value = CalendarLayout.startOfWeek(date)
    }

    /**
     * Turns one day's classes into schedule rows: the members' classes of a group lesson collapse
     * into a single row carrying them as [ScheduleRow.members]. Shared by the day list and the week
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
                    members = session.classes.map { c ->
                        ScheduleRow(
                            session = ClassSession(null, listOf(c)),
                            title = students[c.studentId]?.name ?: "",
                            subjectName = subjectName,
                        )
                    },
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
