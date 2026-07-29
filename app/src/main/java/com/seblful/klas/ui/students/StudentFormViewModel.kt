package com.seblful.klas.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.di.Computation
import com.seblful.klas.data.repository.EnrollmentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.format.MoneyField
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.ui.common.LeavableForm
import com.seblful.klas.ui.common.UiEvents
import com.seblful.klas.ui.common.derive
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the "add student" screen. A new student must be created with at least one subject *and* a
 * valid price for each chosen subject — a class can only be booked for a priced subject, so a
 * student with none would be unusable. This is enforced here ([UiState.isValid]) and by the screen.
 */
@HiltViewModel
class StudentFormViewModel @Inject constructor(
    subjectsRepo: SubjectRepository,
    private val enrollment: EnrollmentRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
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
    ) : LeavableForm {
        val selectedSubjectIds: Set<Long> get() = form.subjectPrices.keys

        /** Name set, at least one subject chosen, and every chosen subject has a valid price. */
        override val canSave: Boolean
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
        override val isDirty: Boolean get() = form != Form()
    }

    val state: StateFlow<UiState> = combine(
        form,
        subjectsRepo.observeAll(),
    ) { f, subjects -> UiState(f, subjects) }
        .derive(this, computation, UiState())

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

    /** One-shot signals from a save that could not go through. */
    enum class Event {
        /** The write failed — a database error rather than anything the form got wrong. */
        SAVE_FAILED,
    }

    val events = UiEvents<Event>()

    /**
     * Creates the student and their priced subjects.
     *
     * **One write, in the application scope.** It used to be two calls in `viewModelScope` followed by
     * navigation, so going back the instant after tapping save could cancel between them and leave a
     * student with no priced subject — a row this very form refuses to submit, and one nothing can be
     * booked for. Both halves now land together (`EnrollmentRepository.createStudent`) in a scope that
     * outlives the screen, and only the navigation stays here.
     *
     * The form is read into a snapshot **before** the write, for the reason `ValidForm` gives in the
     * booking form: a write that outlives its screen must not read state the screen may still be
     * editing.
     */
    fun save(onSaved: () -> Unit) {
        val s = state.value
        if (!s.canSave) return
        val f = s.form
        val student = Student(
            name = f.name.trim(),
            phone = f.phone.trim(),
            email = f.email.trim(),
            note = f.note.trim(),
            defaultMode = f.mode,
        )
        // isValid guarantees every chosen subject has a parseable price.
        val pricedSubjects = f.subjectPrices.mapNotNull { (subjectId, price) ->
            price.amount?.let { subjectId to it }
        }.toMap()

        viewModelScope.launch {
            val written = appScope
                .async { runCatchingCancellable { enrollment.createStudent(student, pricedSubjects) } }
                .await()
            if (written.isSuccess) onSaved() else events.send(Event.SAVE_FAILED)
        }
    }
}
