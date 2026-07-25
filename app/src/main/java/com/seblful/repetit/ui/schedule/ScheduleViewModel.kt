package com.seblful.repetit.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.repository.ClassRepository
import com.seblful.repetit.data.repository.StudentRepository
import com.seblful.repetit.data.repository.SubjectRepository
import com.seblful.repetit.domain.CalendarMonth
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.TutoringClass
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class ScheduleRow(val klass: TutoringClass, val studentName: String, val subjectName: String)

/** How the schedule presents its days: one day at a time, or a month grid above the day's list. */
enum class ScheduleViewMode { DAY, MONTH }

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

    val monthGrid: StateFlow<CalendarMonth.Grid> =
        _visibleMonth.map { CalendarMonth.grid(it) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                CalendarMonth.grid(_visibleMonth.value),
            )

    /**
     * Per-day markers for every cell of the visible page — including the neighbouring-month days
     * padding its first and last rows, so those cells aren't misleadingly blank.
     */
    val dayLoads: StateFlow<Map<LocalDate, CalendarMonth.DayLoad>> =
        _visibleMonth.flatMapLatest { month ->
            val grid = CalendarMonth.grid(month)
            classes.observeBetween(
                grid.firstDate.atStartOfDay(zone).toInstant(),
                grid.endExclusive.atStartOfDay(zone).toInstant(),
            ).map { CalendarMonth.dayLoads(it, zone) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
                dayClasses
                    .sortedBy { it.start }
                    .map { c ->
                        ScheduleRow(
                            klass = c,
                            studentName = studentsById[c.studentId]?.name ?: "",
                            subjectName = subjectsById[c.subjectId]?.name ?: "",
                        )
                    }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleViewMode() {
        _viewMode.value = when (_viewMode.value) {
            ScheduleViewMode.DAY -> ScheduleViewMode.MONTH
            ScheduleViewMode.MONTH -> ScheduleViewMode.DAY
        }
        // Opening the calendar on a page that doesn't contain the selected day would read as
        // nothing being selected, so snap the page back to it.
        if (_viewMode.value == ScheduleViewMode.MONTH) showMonthOf(_selectedDate.value)
    }

    /** Picks the day whose classes the list shows; the calendar page follows it. */
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        showMonthOf(date)
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

    fun setStatus(classId: Long, status: ClassStatus) {
        viewModelScope.launch { classes.setStatus(classId, status) }
    }

    fun move(classId: Long, newStart: Instant) {
        viewModelScope.launch { classes.move(classId, newStart) }
    }

    fun delete(klass: TutoringClass) {
        viewModelScope.launch { classes.delete(klass) }
    }

    private fun showMonthOf(date: LocalDate) {
        _visibleMonth.value = YearMonth.from(date)
    }
}
