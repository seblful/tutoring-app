package com.seblful.klas.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.seblful.klas.data.billing.RateHistoryRepository
import com.seblful.klas.data.people.EnrollmentRepository
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.RateChange
import com.seblful.klas.domain.model.RateOwner
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.format.MoneyField
import com.seblful.klas.ui.common.LeavableForm
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.common.state.writeAwaited
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** reating or editing a group: its **subject**, name, note, hourly rate, default lesson mode and roster. */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupFormViewModel @AssistedInject constructor(
    @Assisted savedStateHandle: SavedStateHandle,
    private val groups: GroupRepository,
    private val settings: SettingsRepository,
    students: StudentRepository,
    subjects: SubjectRepository,
    rateHistory: RateHistoryRepository,
    private val enrollment: EnrollmentRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
) : ViewModel() {

    /** Hands over the screen's navigation arguments as the [SavedStateHandle]; the rest is injected. */
    @AssistedFactory
    @ViewModelAssistedFactoryKey(GroupFormViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): GroupFormViewModel = create(extras.createSavedStateHandle())

        fun create(@Assisted savedStateHandle: SavedStateHandle): GroupFormViewModel
    }

    /** 0 for a new group. */
    val groupId: Long = savedStateHandle["id"] ?: 0L

    val isNew: Boolean = groupId == 0L

    data class Form(
        val name: String = "",
        val note: String = "",
        /** The group's hourly rate as typed. Zero is allowed, blank is not — see [UiState.price]. */
        val price: MoneyField = MoneyField(kind = MoneyField.Kind.RATE),
        /** What the group is taught. Null until the tutor picks one. */
        val subjectId: Long? = null,
        val mode: ClassMode = ClassMode.IN_PERSON,
        val memberIds: Set<Long> = emptySet(),
    )

    data class UiState(
        val form: Form = Form(),
        /**
         * Who may be put in the group: the active students priced for [Form.subjectId], or every
         * active student while no subject is chosen.
         */
        val candidates: List<Student> = emptyList(),
        /** Active students who need a subject price before they can join. */
        val unpriced: List<Student> = emptyList(),
        /** Everything the tutor could teach this group, for the subject picker. */
        val subjects: List<Subject> = emptyList(),
        /** Every recorded change to this group's hourly rate, newest first. */
        val rateHistory: List<RateChange> = emptyList(),
        /**
         * Everybody priced for [Form.subjectId], archived students included — the set `GroupRepository.save`
         * keeps a roster to — or null while no subject is chosen, when it keeps everyone.
         */
        val pricedIds: Set<Long>? = null,
        /**
         * Whether the form differs from the group as it was loaded (or, for a new one, from the
         * prefilled blank) — what leaving without saving would throw away.
         */
        override val isDirty: Boolean = false,
    ) : LeavableForm {
        /**
         * The rate as [Money], or null while the field doesn't hold a valid amount. Zero is allowed —
         * a group taught for free still needs booking and billing (at 0) — but blank is not: a group
         * prices its own lessons, so it has to say what they cost.
         */
        val price: Money? get() = form.price.amount

        /** Whether there is anybody to put in a group at all — worded differently from "nobody picked". */
        val hasStudents: Boolean get() = candidates.isNotEmpty()

        /**
         * The picked members that survive the subject filter. Switching a group's subject can strand
         * a tick on someone no longer eligible; they are not silently counted as members here, and
         * `GroupRepository.save` drops them for real. Everything downstream reads this, not
         * [Form.memberIds], so what the form validates is what gets written.
         */
        val selectedMemberIds: Set<Long>
            get() = form.memberIds intersect candidates.map { it.id }.toSet()

        /**
         * Members the form is holding but will **not** save: ticked, or loaded with the group, and no
         * longer priced for its subject.
         */
        val strandedMemberIds: Set<Long> get() = form.memberIds - selectedMemberIds

        /**
         * How many of the roster the save would take off it, for want of a price for the subject — asked
         * about **before** the save, not reported after it. The report was a snackbar sent as the form
         * closed, so it went with the screen and the roster shrank in silence after all.
         */
        val leavingOnSave: Int get() = pricedIds?.let { (form.memberIds - it).size } ?: 0

        /** A group needs a name, a rate and at least one member; without those there is nothing to book. */
        override val canSave: Boolean
            get() = form.name.isNotBlank() && price != null && selectedMemberIds.isNotEmpty()
    }

    /** One-shot UI signals. */
    enum class Event {
        /** The write failed — a database error, not anything the form got wrong. The form stays open. */
        SAVE_FAILED,
    }

    val events = UiEvents<Event>()

    private val form = MutableStateFlow(Form())
    private val archived = MutableStateFlow(false)

    private val pristine = MutableStateFlow(Form())

    private val filled = MutableStateFlow(isNew)

    private val pricedForSubject: Flow<Set<Long>?> = form
        .map { it.subjectId }
        .distinctUntilChanged()
        .flatMapLatest { subjectId ->
            if (subjectId == null) flowOf(null)
            else students.observeIdsForSubject(subjectId).map { it.toSet() }
        }

    private val edited: Flow<Pair<Form, Boolean>> =
        combine(form, pristine) { f, p -> f to (f != p) }

    val state: StateFlow<Loadable<UiState>> = combine(
        edited,
        students.observeActive(),
        subjects.observeAll(),
        pricedForSubject,
    ) { (f, dirty), activeStudents, allSubjects, priced ->
        UiState(
            form = f,
            candidates = if (priced == null) activeStudents else activeStudents.filter { it.id in priced },
            unpriced = if (priced == null) emptyList() else activeStudents.filterNot { it.id in priced },
            subjects = allSubjects,
            pricedIds = priced,
            isDirty = dirty,
        )
    }
        // A new group has no history to observe.
        .combine(if (isNew) flowOf(emptyList()) else rateHistory.observeFor(RateOwner.GroupRate(groupId))) {
                state,
                changes,
            ->
            state.copy(rateHistory = changes)
        }
        // Nothing above this line can emit before the students and the subjects have been read — a
        // `combine` waits for every source — so [filled] is the only question left: whether the *group*
        // has arrived in the form. Both halves matter, and the initial value is what says so.
        .combine(filled) { state, isFilled ->
            if (isFilled) Loadable.Ready(state) else Loadable.Loading
        }
        .derive(this, computation, Loadable.Loading)

    init {
        if (!isNew) {
            viewModelScope.launch {
                groups.getById(groupId)?.let { group ->
                    archived.value = group.archived
                    form.value = Form(
                        name = group.name,
                        note = group.note,
                        price = MoneyField.of(group.price),
                        subjectId = group.subjectId,
                        mode = group.defaultMode,
                        memberIds = groups.getMemberIds(groupId).toSet(),
                    )
                    pristine.value = form.value
                }
                // Outside the `let`, so a group that is no longer there still opens: it is an empty form
                // rather than a screen waiting on a skeleton for a row that will never arrive.
                filled.value = true
            }
        } else {
            // Pre-fill the tutor's usual group rate, when they have set one — and only into an
            // untouched field, so an amount already typed is never clobbered by a setting that
            // arrives a moment later (prefilledWith's rule).
            viewModelScope.launch {
                val suggested = settings.get().defaultGroupPrice
                form.value = form.value.copy(price = form.value.price.prefilledWith(suggested))
                // The baseline takes the suggestion too, so a prefilled rate is not "unsaved work".
                // Applied to each separately on purpose: if the tutor typed an amount before this
                // arrived, the form keeps theirs while the baseline takes the default — which is the
                // truth, and is what makes back ask about it.
                pristine.value = pristine.value.copy(price = pristine.value.price.prefilledWith(suggested))
            }
        }
    }

    fun update(transform: (Form) -> Form) {
        form.value = transform(form.value)
    }

    fun toggleMember(studentId: Long, on: Boolean) {
        form.value = form.value.copy(
            memberIds = if (on) form.value.memberIds + studentId else form.value.memberIds - studentId,
        )
    }

    /**
     * Changes what the group is taught. Ticks on students who aren't priced for the new subject are
     * left in [Form.memberIds] rather than pruned here — recovering them is the point.
     */
    fun setSubject(subjectId: Long?) {
        form.value = form.value.copy(subjectId = subjectId)
    }

    /** Repairs eligibility in place, then selects the student without resetting the group draft. */
    fun enrollMember(studentId: Long, subjectId: Long, price: Money) {
        viewModelScope.launch {
            appScope.writeAwaited { enrollment.enroll(studentId, subjectId, price) }
                .onSuccess {
                    if (form.value.subjectId == subjectId) toggleMember(studentId, true)
                }
                .onFailure { events.send(Event.SAVE_FAILED) }
        }
    }

    private var saving = false

    /**
     * Saves the group and its roster in one step (see `GroupRepository.save`) and reports the group's
     * id, so a freshly created group can go straight on to booking its first lesson.
     */
    fun save(onSaved: (Long) -> Unit) {
        if (saving) return
        // A form that has not loaded holds nothing to save — and the screen offers no save button while
        // it is drawing a skeleton, so this is the belt to that braces.
        val s = state.value.valueOrNull() ?: return
        if (!s.canSave) return
        // The rate `canSave` has just proved, taken as a value instead of re-asserted with `!!` — the
        // same check twice is the same check drifting apart.
        val price = s.price ?: return
        val f = s.form
        val group = Group(
            id = groupId,
            name = f.name.trim(),
            note = f.note.trim(),
            price = price,
            subjectId = f.subjectId,
            defaultMode = f.mode,
            archived = archived.value,
        )
        saving = true
        viewModelScope.launch {
            // The whole ticked set goes down, stranded members included: which of them a
            // subject-bound group may keep is the save's own rule.
            appScope.writeAwaited { groups.save(group, f.memberIds) }
                .onSuccess {
                    // Saved is the new baseline, so a save that leaves the screen open (or a slow
                    // navigation) cannot be followed by "you have unsaved changes".
                    pristine.value = f
                    onSaved(it)
                }
                .onFailure {
                    saving = false
                    events.send(Event.SAVE_FAILED)
                }
        }
    }
}
