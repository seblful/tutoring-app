package com.seblful.tutoringapp.ui.schedule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.tutoringapp.data.repository.ClassRepository
import com.seblful.tutoringapp.data.repository.PriceRepository
import com.seblful.tutoringapp.data.repository.SettingsRepository
import com.seblful.tutoringapp.data.repository.StudentRepository
import com.seblful.tutoringapp.data.repository.SubjectRepository
import com.seblful.tutoringapp.domain.ClassRules
import com.seblful.tutoringapp.domain.Recurrence
import com.seblful.tutoringapp.domain.Scheduling
import com.seblful.tutoringapp.domain.model.ClassMode
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.Student
import com.seblful.tutoringapp.domain.model.Subject
import com.seblful.tutoringapp.domain.model.TutoringClass
import com.seblful.tutoringapp.format.parseMoneyOrNull
import com.seblful.tutoringapp.format.toAmountInput
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * The class-booking form. Date and times start **empty** — the user must choose them (the pickers
 * still open on a sensible default). Duration is expressed as a time range: [timeTo] defaults to one
 * hour after [timeFrom] the moment a start is chosen, and stays editable. [priceText] is the raw
 * price input; it is pre-filled from the student's price for the subject and must be a valid amount.
 */
data class ClassForm(
    val studentId: Long? = null,
    val subjectId: Long? = null,
    val date: LocalDate? = null,
    val timeFrom: LocalTime? = null,
    val timeTo: LocalTime? = null,
    val priceText: String = "",
    val mode: ClassMode = ClassMode.IN_PERSON,
    val repeatWeekly: Boolean = false,
    val weeks: Int = 4,
) {
    /** Minutes between [timeFrom] and [timeTo], or null when the range isn't a positive duration. */
    val durationMinutes: Int?
        get() {
            if (timeFrom == null || timeTo == null) return null
            val minutes = Duration.between(timeFrom, timeTo).toMinutes()
            return if (minutes > 0) minutes.toInt() else null
        }

    val price: Money? get() = parseMoneyOrNull(priceText)

    val isValid: Boolean
        get() = studentId != null && subjectId != null && date != null &&
            timeFrom != null && timeTo != null && (durationMinutes ?: 0) > 0 &&
            price != null && weeks in 1..MAX_WEEKS

    companion object {
        /** Cap on a weekly series so one booking can't generate an unbounded flood of classes. */
        const val MAX_WEEKS = 52
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClassEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val classes: ClassRepository,
    private val studentsRepo: StudentRepository,
    private val subjectsRepo: SubjectRepository,
    private val pricesRepo: PriceRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val classId: Long = savedStateHandle["id"] ?: 0L

    val isNew: Boolean = classId == 0L

    /** Pre-fills the end time when a start is picked; loaded from settings. */
    private var defaultLessonMinutes: Long = 60

    /** The class being edited, if any — preserves status/charge/recurring group on save. */
    private var original: TutoringClass? = null

    val students: StateFlow<List<Student>> = studentsRepo.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val form = MutableStateFlow(ClassForm())

    /**
     * Subjects the chosen student may be booked for — only those that have a price set for them, so
     * the class can be billed at completion. Empty until a student is selected, and recomputed
     * whenever the selected student or their prices change.
     */
    val subjects: StateFlow<List<Subject>> =
        form.map { it.studentId }.distinctUntilChanged()
            .flatMapLatest { studentId ->
                if (studentId == null) {
                    flowOf(emptyList<Subject>())
                } else {
                    combine(
                        pricesRepo.observeForStudent(studentId),
                        subjectsRepo.observeAll(),
                    ) { prices, all -> Scheduling.assignableSubjects(all, prices.map { it.subjectId }) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (!isNew) {
            viewModelScope.launch {
                classes.getById(classId)?.let { c ->
                    original = c
                    val local = c.start.atZone(zone)
                    val from = local.toLocalTime()
                    form.value = ClassForm(
                        studentId = c.studentId,
                        subjectId = c.subjectId,
                        date = local.toLocalDate(),
                        timeFrom = from,
                        timeTo = from.plusMinutes(c.durationMinutes.toLong()),
                        priceText = c.price.toAmountInput(),
                        mode = c.mode,
                    )
                }
            }
        }
        viewModelScope.launch { defaultLessonMinutes = settingsRepo.get().defaultLessonMinutes.toLong() }
        // Auto-select the only student there is (a new class, before any choice). With several
        // students, the user picks — we don't guess.
        if (isNew) {
            viewModelScope.launch {
                students.collect { list ->
                    if (form.value.studentId == null && list.size == 1) {
                        selectStudent(list.first().id)
                    }
                }
            }
        }
        // Auto-select the only subject a student has (and pre-fill its price). With several
        // subjects, the user picks — we don't guess.
        viewModelScope.launch {
            subjects.collect { list ->
                val f = form.value
                if (f.studentId != null && f.subjectId == null && list.size == 1) {
                    selectSubject(list.first().id)
                }
            }
        }
    }

    fun update(transform: (ClassForm) -> ClassForm) {
        form.value = transform(form.value)
    }

    /**
     * Choosing a (different) student clears the subject and price (they belonged to the previous
     * student) and pre-fills the class mode from the new student's default.
     */
    fun selectStudent(studentId: Long) {
        if (form.value.studentId == studentId) return
        val mode = students.value.firstOrNull { it.id == studentId }?.defaultMode ?: ClassMode.IN_PERSON
        form.value = form.value.copy(studentId = studentId, subjectId = null, priceText = "", mode = mode)
    }

    /** Selecting a subject pre-fills the price from the student's price for it (if any). */
    fun selectSubject(subjectId: Long) {
        form.value = form.value.copy(subjectId = subjectId)
        val studentId = form.value.studentId ?: return
        viewModelScope.launch {
            val price = pricesRepo.get(studentId, subjectId)?.amount
            // Only auto-fill an empty field — never clobber a price the user already typed.
            if (price != null && form.value.subjectId == subjectId && form.value.priceText.isBlank()) {
                form.value = form.value.copy(priceText = price.toAmountInput())
            }
        }
    }

    /** Picking a start time defaults the end time to the default lesson length later (if unset). */
    fun setTimeFrom(time: LocalTime) {
        val f = form.value
        form.value = f.copy(timeFrom = time, timeTo = f.timeTo ?: time.plusMinutes(defaultLessonMinutes))
    }

    fun setTimeTo(time: LocalTime) {
        form.value = form.value.copy(timeTo = time)
    }

    fun save(onDone: () -> Unit) {
        val f = form.value
        if (!f.isValid) return
        val start = f.date!!.atTime(f.timeFrom!!).atZone(zone).toInstant()
        val duration = f.durationMinutes!!
        val price = f.price!!
        viewModelScope.launch {
            val existing = original
            if (existing != null) {
                // A class in any status may be edited. Apply the field changes, then resolve status
                // and charge consistently:
                //  - the new start goes through the same re-planning as a move (ClassRules.move):
                //    shifting it into the future re-plans the class (scheduled, charge voided),
                //    keeping a completed class from ending up "completed in the future";
                //  - rebill then keeps the charge in step with the (possibly new) price/duration —
                //    re-billing a completed class that stays in the past and re-attributing its
                //    income to the current student/subject, while scheduled/cancelled stay at zero.
                val edited = existing.copy(
                    studentId = f.studentId!!,
                    subjectId = f.subjectId!!,
                    durationMinutes = duration,
                    price = price,
                    mode = f.mode,
                )
                val resolved = ClassRules.rebill(ClassRules.move(edited, start, Instant.now()))
                classes.add(resolved)
            } else {
                val base = TutoringClass(
                    studentId = f.studentId!!,
                    subjectId = f.subjectId!!,
                    start = start,
                    durationMinutes = duration,
                    price = price,
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
