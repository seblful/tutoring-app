package com.seblful.repetit.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.repository.PriceRepository
import com.seblful.repetit.data.repository.StudentRepository
import com.seblful.repetit.data.repository.SubjectRepository
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.Student
import com.seblful.repetit.domain.model.Subject
import com.seblful.repetit.format.parseMoneyOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the "add student" screen. A new student must be created with at least one subject *and* a
 * valid price for each chosen subject — a class can only be booked for a priced subject, so a
 * student with none would be unusable. This is enforced here ([UiState.isValid]) and by the screen.
 */
@HiltViewModel
class StudentFormViewModel @Inject constructor(
    private val students: StudentRepository,
    subjectsRepo: SubjectRepository,
    private val prices: PriceRepository,
) : ViewModel() {

    data class Form(
        val name: String = "",
        val phone: String = "+375 ",
        val email: String = "",
        val note: String = "",
        val mode: ClassMode = ClassMode.IN_PERSON,
        /** subjectId -> raw price text, for the subjects the user has checked. */
        val subjectPrices: Map<Long, String> = emptyMap(),
    )

    private val form = MutableStateFlow(Form())

    data class UiState(
        val form: Form = Form(),
        val allSubjects: List<Subject> = emptyList(),
    ) {
        val selectedSubjectIds: Set<Long> get() = form.subjectPrices.keys

        /** Name set, at least one subject chosen, and every chosen subject has a valid price. */
        val isValid: Boolean
            get() = form.name.isNotBlank() &&
                form.subjectPrices.isNotEmpty() &&
                form.subjectPrices.values.all { parseMoneyOrNull(it) != null }
    }

    val state: StateFlow<UiState> = combine(
        form,
        subjectsRepo.observeAll(),
    ) { f, subjects -> UiState(f, subjects) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun update(transform: (Form) -> Form) {
        form.value = transform(form.value)
    }

    fun toggleSubject(subjectId: Long, on: Boolean) {
        form.value = form.value.copy(
            subjectPrices = if (on) form.value.subjectPrices + (subjectId to "")
            else form.value.subjectPrices - subjectId,
        )
    }

    fun setPriceText(subjectId: Long, text: String) {
        if (subjectId !in form.value.subjectPrices) return
        form.value = form.value.copy(subjectPrices = form.value.subjectPrices + (subjectId to text))
    }

    fun save(onSaved: () -> Unit) {
        val s = state.value
        if (!s.isValid) return
        val f = s.form
        viewModelScope.launch {
            val id = students.upsert(
                Student(
                    name = f.name.trim(),
                    phone = f.phone.trim(),
                    email = f.email.trim(),
                    note = f.note.trim(),
                    defaultMode = f.mode,
                )
            )
            students.setSubjects(id, f.subjectPrices.keys)
            f.subjectPrices.forEach { (subjectId, text) ->
                parseMoneyOrNull(text)?.let { prices.setPrice(id, subjectId, it) }
            }
            onSaved()
        }
    }
}
