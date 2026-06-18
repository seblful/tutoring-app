package com.seblful.tutoringapp.ui.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.tutoringapp.data.repository.ClassRepository
import com.seblful.tutoringapp.data.repository.StudentRepository
import com.seblful.tutoringapp.data.repository.SubjectRepository
import com.seblful.tutoringapp.domain.Recurrence
import com.seblful.tutoringapp.domain.Scheduling
import com.seblful.tutoringapp.domain.model.ClassMode
import com.seblful.tutoringapp.domain.model.Student
import com.seblful.tutoringapp.domain.model.Subject
import com.seblful.tutoringapp.domain.model.TutoringClass
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class ClassForm(
    val studentId: Long? = null,
    val subjectId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.of(15, 0),
    val durationMinutes: Int = 60,
    val mode: ClassMode = ClassMode.IN_PERSON,
    val repeatWeekly: Boolean = false,
    val weeks: Int = 4,
) {
    val isValid: Boolean get() = studentId != null && subjectId != null && durationMinutes > 0 && weeks >= 1
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClassEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val classes: ClassRepository,
    private val studentsRepo: StudentRepository,
    private val subjectsRepo: SubjectRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val classId: Long = savedStateHandle["id"] ?: 0L
    private val defaultEpochDay: Long = savedStateHandle["date"] ?: -1L

    val isNew: Boolean = classId == 0L

    /** The class being edited, if any — preserves status/charge/recurring group on save. */
    private var original: TutoringClass? = null

    val students: StateFlow<List<Student>> = studentsRepo.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val form = MutableStateFlow(
        ClassForm(date = if (defaultEpochDay >= 0) LocalDate.ofEpochDay(defaultEpochDay) else LocalDate.now(zone))
    )

    /**
     * Subjects the chosen student may be booked for — only the ones assigned to them. Empty until a
     * student is selected, and recomputed whenever the selected student or their subjects change.
     */
    val subjects: StateFlow<List<Subject>> =
        form.map { it.studentId }.distinctUntilChanged()
            .flatMapLatest { studentId ->
                if (studentId == null) {
                    flowOf(emptyList<Subject>())
                } else {
                    combine(
                        studentsRepo.observeSubjectIds(studentId),
                        subjectsRepo.observeAll(),
                    ) { assignedIds, all -> Scheduling.assignableSubjects(all, assignedIds) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (!isNew) {
            viewModelScope.launch {
                classes.getById(classId)?.let { c ->
                    original = c
                    val local = c.start.atZone(zone)
                    form.value = ClassForm(
                        studentId = c.studentId,
                        subjectId = c.subjectId,
                        date = local.toLocalDate(),
                        time = local.toLocalTime(),
                        durationMinutes = c.durationMinutes,
                        mode = c.mode,
                        repeatWeekly = false,
                    )
                }
            }
        }
    }

    fun update(transform: (ClassForm) -> ClassForm) {
        form.value = transform(form.value)
    }

    /**
     * Choosing a (different) student clears the subject, which belonged to the previous student,
     * and pre-fills the class mode from the new student's default (still editable afterwards).
     */
    fun selectStudent(studentId: Long) {
        if (form.value.studentId == studentId) return
        val mode = students.value.firstOrNull { it.id == studentId }?.defaultMode ?: ClassMode.IN_PERSON
        form.value = form.value.copy(studentId = studentId, subjectId = null, mode = mode)
    }

    fun save(onDone: () -> Unit) {
        val f = form.value
        if (!f.isValid) return
        val start = f.date.atTime(f.time).atZone(zone).toInstant()
        viewModelScope.launch {
            val existing = original
            if (existing != null) {
                classes.add(
                    existing.copy(
                        studentId = f.studentId!!,
                        subjectId = f.subjectId!!,
                        start = start,
                        durationMinutes = f.durationMinutes,
                        mode = f.mode,
                    )
                )
            } else {
                val base = TutoringClass(
                    studentId = f.studentId!!,
                    subjectId = f.subjectId!!,
                    start = start,
                    durationMinutes = f.durationMinutes,
                    mode = f.mode,
                )
                if (f.repeatWeekly && f.weeks > 1) {
                    val groupId = System.currentTimeMillis()
                    classes.addAll(Recurrence.weekly(base, zone, f.weeks, groupId))
                } else {
                    classes.add(base)
                }
            }
            onDone()
        }
    }
}
