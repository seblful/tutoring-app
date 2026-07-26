package com.seblful.klas.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.repository.EnrollmentRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.format.MoneyField
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
    private val enrollment: EnrollmentRepository,
) : ViewModel() {

    data class Form(
        val name: String = "",
        val phone: String = "+375 ",
        val email: String = "",
        val note: String = "",
        val mode: ClassMode = ClassMode.IN_PERSON,
        /** subjectId -> its price field, for the subjects the user has checked. */
        val subjectPrices: Map<Long, MoneyField> = emptyMap(),
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
                form.subjectPrices.values.all { it.isValid }

        /**
         * Whether anything has been entered — what leaving without saving would cost.
         *
         * A new student's form is compared against a fresh [Form] because nothing else ever sets it:
         * there is no record to load and no default to prefill, so "as it was opened" and "as the
         * class declares it" are the same value. The dialled phone prefix is part of that default and
         * so does not, on its own, make the form dirty.
         */
        val isDirty: Boolean get() = form != Form()
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
            subjectPrices = if (on) form.value.subjectPrices + (subjectId to MoneyField())
            else form.value.subjectPrices - subjectId,
        )
    }

    fun setPriceText(subjectId: Long, text: String) {
        val field = form.value.subjectPrices[subjectId] ?: return
        form.value = form.value.copy(
            subjectPrices = form.value.subjectPrices + (subjectId to field.typed(text)),
        )
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
            // isValid guarantees every chosen subject has a parseable price.
            val pricedSubjects = f.subjectPrices.mapNotNull { (subjectId, price) ->
                price.amount?.let { subjectId to it }
            }.toMap()
            enrollment.setEnrollments(id, pricedSubjects)
            onSaved()
        }
    }
}
