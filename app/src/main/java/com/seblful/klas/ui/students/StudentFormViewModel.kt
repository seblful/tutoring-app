package com.seblful.klas.ui.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.data.people.EnrollmentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.people.Phone
import com.seblful.klas.domain.people.SubjectNames
import com.seblful.klas.format.MoneyField
import com.seblful.klas.ui.common.FormGap
import com.seblful.klas.ui.common.LeavableForm
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.common.state.writeAwaited
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Backs the "add student" screen. A new student must be created with at least one subject *and* a
 * valid price for each chosen subject — a class can only be booked for a priced subject, so a
 * student with none would be unusable. This is enforced here ([UiState.canSave]) and by the screen.
 */
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class StudentFormViewModel @Inject constructor(
    private val subjectsRepo: SubjectRepository,
    private val enrollment: EnrollmentRepository,
    private val settings: SettingsRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
) : ViewModel() {

    data class Form(
        val name: String = "",
        /** Empty until the screen offers the dialling code ([offerDiallingCode]). */
        val phone: String = "",
        val email: String = "",
        val note: String = "",
        val mode: ClassMode = ClassMode.IN_PERSON,
        /** subjectId -> its price field, for the subjects the user has checked. */
        val subjectPrices: Map<Long, MoneyField> = emptyMap(),
    )

    private val form = MutableStateFlow(Form())
    private val pristine = MutableStateFlow(Form())

    data class UiState(
        val form: Form = Form(),
        /**
         * Everything the tutor teaches, to tick a price against — [Loadable], while the rest of this
         * state is not.
         */
        val allSubjects: Loadable<List<Subject>> = Loadable.Loading,
        /** How long a lesson usually is, to say what a typed hourly price comes to for one. */
        val lessonMinutes: Int = AppSettings().defaultLessonMinutes,
        private val baseline: Form = Form(),
    ) : LeavableForm {
        val selectedSubjectIds: Set<Long> get() = form.subjectPrices.keys

        /** What stands between the form and a save, in the order the form asks for it. */
        val missing: List<FormGap>
            get() = listOfNotNull(
                FormGap.NAME.takeIf { form.name.isBlank() },
                FormGap.SUBJECT.takeIf { form.subjectPrices.isEmpty() },
                FormGap.PRICE.takeIf { !form.subjectPrices.values.all { it.isValid } },
            )

        /** Name set, at least one subject chosen, and every chosen subject has a valid price. */
        override val canSave: Boolean get() = missing.isEmpty()

        /** Whether anything has been entered — what leaving without saving would cost. */
        override val isDirty: Boolean get() = form != baseline
    }

    val state: StateFlow<UiState> = combine(
        form,
        subjectsRepo.observeAll(),
        pristine,
        settings.observe().map { it.defaultLessonMinutes },
    ) { f, subjects, baseline, lessonMinutes ->
        val existing = subjects.mapTo(mutableSetOf()) { it.id }
        UiState(
            f.copy(
                subjectPrices = f.subjectPrices.filterKeys {
                    it in existing
                },
            ),
            Loadable.Ready(subjects), lessonMinutes, baseline,
        )
    }
        // The form itself is usable from the first frame, so the state is not wrapped — the one part of it
        // that has to have been read carries its own answer (see [UiState.allSubjects]).
        .derive(this, computation, UiState())

    /**
     * Whether the ticked subjects are still the form's guess ([guessSubject]) — until one is suggested,
     * ticked or added, after which no change to the list second-guesses them.
     */
    private var guessing = true

    init {
        viewModelScope.launch { subjectsRepo.observeAll().collect(::guessSubject) }
    }

    /** Whether nothing has been entered yet, so what the form shows is still its own guess. */
    private val untouched: Boolean get() = form.value == pristine.value

    /**
     * Ticks the only subject there is, until the tutor touches the form: a student needs one, and with a
     * single subject there is nothing to choose. With several, nothing is ticked — and a guess the list
     * has outgrown (setup adds subjects while this form waits) is taken back.
     */
    private fun guessSubject(subjects: List<Subject>) {
        if (!guessing || !untouched) return
        val guess = subjects.singleOrNull()?.let { mapOf(it.id to MoneyField()) }.orEmpty()
        if (form.value.subjectPrices.keys == guess.keys) return
        form.value = form.value.copy(subjectPrices = guess)
        pristine.value = form.value
    }

    fun update(transform: (Form) -> Form) {
        form.value = transform(form.value)
    }

    /**
     * Opens the phone on [prefill], the dialling code for the currency the screen is shown in
     * ([Phone.prefill]). A typed number is kept; either way the code is not the tutor's work to lose.
     */
    fun offerDiallingCode(prefill: String) {
        if (Phone.stored(form.value.phone).isEmpty()) form.value = form.value.copy(phone = prefill)
        pristine.value = pristine.value.copy(phone = prefill)
    }

    /** A setup detour carries the subject, while its parent form stays on the back stack. */
    fun suggestSubject(subjectId: Long) {
        if (!untouched) return
        guessing = false
        form.value = form.value.copy(subjectPrices = mapOf(subjectId to MoneyField()))
        pristine.value = form.value
    }

    fun toggleSubject(subjectId: Long, on: Boolean) {
        guessing = false
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
     */
    fun addSubject(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        guessing = false
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

    /** Creates the student and their priced subjects. */
    fun save(onSaved: () -> Unit) = create { onSaved() }

    fun create(onCreated: (Long) -> Unit) {
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
                onCreated(written.getOrThrow())
            } else {
                saving = false
                events.send(Event.SAVE_FAILED)
            }
        }
    }

    private var saving = false
}
