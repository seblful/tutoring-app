package com.seblful.repetit.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.repository.ClassRepository
import com.seblful.repetit.data.repository.StudentRepository
import com.seblful.repetit.data.repository.SubjectRepository
import com.seblful.repetit.domain.CalendarLayout
import com.seblful.repetit.domain.model.ClassStatus
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

data class ScheduleRow(val klass: TutoringClass, val studentName: String, val subjectName: String)

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
            ) { weekClasses, studentList, subjectList ->
                val studentsById = studentList.associateBy { it.id }
                val subjectsById = subjectList.associateBy { it.id }
                weekClasses
                    .sortedBy { it.start }
                    .map { it.toRow(studentsById, subjectsById) }
                    .groupBy { it.klass.start.atZone(zone).toLocalDate() }
                    .map { (date, dayRows) -> WeekSection(date, dayRows) }
                    .sortedBy { it.date }
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
            ) { dayClasses, studentList, subjectList ->
                val studentsById = studentList.associateBy { it.id }
                val subjectsById = subjectList.associateBy { it.id }
                dayClasses.sortedBy { it.start }.map { it.toRow(studentsById, subjectsById) }
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

    fun setStatus(classId: Long, status: ClassStatus) {
        viewModelScope.launch { classes.setStatus(classId, status) }
    }

    fun move(classId: Long, newStart: Instant) {
        viewModelScope.launch { classes.move(classId, newStart) }
    }

    fun delete(klass: TutoringClass) {
        viewModelScope.launch { classes.delete(klass) }
    }

    /** Points both calendar pages at [date], so whichever view opens next contains it. */
    private fun showPagesFor(date: LocalDate) {
        _visibleMonth.value = YearMonth.from(date)
        _visibleWeek.value = CalendarLayout.startOfWeek(date)
    }

    private fun TutoringClass.toRow(students: Map<Long, Student>, subjects: Map<Long, Subject>) =
        ScheduleRow(
            klass = this,
            studentName = students[studentId]?.name ?: "",
            subjectName = subjects[subjectId]?.name ?: "",
        )

    private companion object {
        /** A week's span as a day count, for the date arithmetic that pages the agenda. */
        const val DAYS_IN_WEEK = CalendarLayout.DAYS_IN_WEEK.toLong()
    }
}
