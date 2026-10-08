package com.seblful.klas.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.di.Computation
import com.seblful.klas.data.repository.EnrollmentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.domain.Phone
import com.seblful.klas.domain.SubjectNames
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.format.MoneyField
import com.seblful.klas.ui.common.LeavableForm
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.UiEvents
import com.seblful.klas.ui.common.derive
import com.seblful.klas.ui.common.valueOrNull
import com.seblful.klas.ui.common.writeAwaited
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
    private val subjectsRepo: SubjectRepository,
    private val enrollment: EnrollmentRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
) : ViewModel() {

    data class Form(
        val name: String = "",
        val phone: String = Phone.PREFILL,
        val email: String = "",
        val note: String = "",
        val mode: ClassMode = ClassMode.IN_PERSON,
        /** subjectId -> its price field, for the subjects the user has checked. */
        val subjectPrices: Map<Long, MoneyField> = emptyMap(),
    )

    private val form = MutableStateFlow(Form())

    data class UiState(
        val form: Form = Form(),
        /**
         * Everything the tutor teaches, to tick a price against — [Loadable], while the rest of this
         * state is not.
         *
         * The split is the point. A blank name and an empty phone box are what "add a student" *is*, so
         * the form is honest before anything has been read and must not be withheld; the subject list is
         * a read of the database, and empty is a **refusal** on this screen — «Сначала добавьте
         * предметы», in the error red, which is what a tutor with nine subjects saw for the frame or two
         * before they arrived. So only the list waits. See [Loadable].
         */
        val allSubjects: Loadable<List<Subject>> = Loadable.Loading,
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
    ) { f, subjects -> UiState(f, Loadable.Ready(subjects)) }
        // The form itself is usable from the first frame, so the state is not wrapped — the one part of it
        // that has to have been read carries its own answer (see [UiState.allSubjects]).
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

    /**
     * Makes a subject from inside the form and ticks it, ready for its price — the way out of «Сначала
     * добавьте предметы», and of a new student taking a subject nobody has been taught yet. The form used
     * to offer no road to a subject but back out of it, so a fresh install's «Добавить ученика» on the
     * schedule led to a refusal.
     *
     * Written in the application scope, like [save]; only the tick waits here, and a failure is the same
     * [Event.SAVE_FAILED] a failed save reports.
     */
    fun addSubject(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        // A subject already called that is the one meant: ticked, not made a second time.
        state.value.allSubjects.valueOrNull()
            ?.let { SubjectNames.sameAs(trimmed, it) }
            ?.let { existing ->
                if (existing.id !in form.value.subjectPrices) toggleSubject(existing.id, on = true)
                return
            }
        viewModelScope.launch {
            appScope.writeAwaited { subjectsRepo.upsert(Subject(name = trimmed)) }
                .onSuccess { id -> toggleSubject(id, on = true) }
                .onFailure { events.send(Event.SAVE_FAILED) }
        }
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
        // Once: a double tap on «Сохранить» created the student twice and popped two screens.
        if (!s.canSave || saving) return
        val f = s.form
        val student = typedStudent(f.name, f.phone, f.email, f.note, f.mode) ?: return
        saving = true
        // isValid guarantees every chosen subject has a parseable price.
        val pricedSubjects = f.subjectPrices.mapNotNull { (subjectId, price) ->
            price.amount?.let { subjectId to it }
        }.toMap()

        viewModelScope.launch {
            val written = appScope.writeAwaited { enrollment.createStudent(student, pricedSubjects) }
            if (written.isSuccess) {
                onSaved()
            } else {
                saving = false
                events.send(Event.SAVE_FAILED)
            }
        }
    }

    /** Set on the tap and cleared only by a failure — see [save]. */
    private var saving = false
}
