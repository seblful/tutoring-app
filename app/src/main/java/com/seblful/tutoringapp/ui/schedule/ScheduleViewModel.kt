package com.seblful.tutoringapp.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.tutoringapp.data.repository.ClassRepository
import com.seblful.tutoringapp.data.repository.StudentRepository
import com.seblful.tutoringapp.data.repository.SubjectRepository
import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.work.WorkScheduler
import com.seblful.tutoringapp.domain.model.TutoringClass
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class ScheduleRow(val klass: TutoringClass, val studentName: String, val subjectName: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val classes: ClassRepository,
    private val workScheduler: WorkScheduler,
    students: StudentRepository,
    subjects: SubjectRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    val selectedDate = MutableStateFlow(LocalDate.now(zone))

    val rows: StateFlow<List<ScheduleRow>> =
        selectedDate.flatMapLatest { date ->
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

    fun goToPreviousDay() {
        selectedDate.value = selectedDate.value.minusDays(1)
    }

    fun goToNextDay() {
        selectedDate.value = selectedDate.value.plusDays(1)
    }

    fun goToToday() {
        selectedDate.value = LocalDate.now(zone)
    }

    fun setStatus(classId: Long, status: ClassStatus) {
        viewModelScope.launch {
            classes.setStatus(classId, status)
            workScheduler.refreshNow()
        }
    }

    fun move(classId: Long, newStart: Instant) {
        viewModelScope.launch {
            classes.move(classId, newStart)
            workScheduler.refreshNow()
        }
    }

    fun delete(klass: TutoringClass) {
        viewModelScope.launch {
            classes.delete(klass)
            workScheduler.refreshNow()
        }
    }
}
