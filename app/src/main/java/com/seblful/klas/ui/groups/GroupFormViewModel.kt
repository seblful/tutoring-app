package com.seblful.klas.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.RateHistoryRepository
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.RateChange
import com.seblful.klas.domain.model.RateOwner
import com.seblful.klas.domain.model.Student
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
import javax.inject.Inject

/**
 * Creating or editing a group: its **subject**, name, note, hourly rate, default lesson mode and roster.
 *
 * The subject is chosen here rather than per lesson, and it is what bounds the roster: only students
 * priced for it can be members, because a group's lessons are all that subject and a member who isn't
 * priced for it could never be billed for one. Picking a subject therefore narrows the candidate list
 * on the spot, and `GroupRepository.save` drops anyone left over.
 *
 * The subject is **optional**, because a group can also arrive without one — from a backup written
 * before the format carried a group's subject (`BackupData` v3 and older). Such a group keeps the
 * old behaviour — any active student may join and the subject is chosen when booking — until the
 * tutor sets one here.
 *
 * A new group's rate is pre-filled from `AppSettings.defaultGroupPrice` when the tutor has set
 * one, so the common case is one less thing to type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupRepository,
    private val settings: SettingsRepository,
    students: StudentRepository,
    subjects: SubjectRepository,
    rateHistory: RateHistoryRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
) : ViewModel() {

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
        /** Everything the tutor could teach this group, for the subject picker. */
        val subjects: List<Subject> = emptyList(),
        /**
         * Every recorded change to this group's hourly rate, newest first.
         *
         * Here rather than on the card, which is where it used to be: an inline «История цены ⌄»
         * expander under the rate, on a screen that only *reads* the rate. History is context for the
         * number being **typed** — the argument the student's reprice dialog already made — and the
         * number is typed here.
         */
        val rateHistory: List<RateChange> = emptyList(),
        /**
         * Whether the group is retired. Read-only now: archiving and deleting a group moved to its
         * **card**, where a student's already were (see [GroupDetailViewModel]). It is still held here
         * because a save writes the whole `Group` row, and a form that dropped the flag would silently
         * un-retire a group on rename.
         */
        val archived: Boolean = false,
        /**
         * Everybody priced for [Form.subjectId], archived students included — the set `GroupRepository.save`
         * keeps a roster to — or null while no subject is chosen, when it keeps everyone.
         */
        val pricedIds: Set<Long>? = null,
        /**
         * Whether the form differs from the group as it was loaded (or, for a new one, from the
         * prefilled blank) — what leaving without saving would throw away.
         *
         * Archiving is deliberately **not** part of it: that button writes to the database there and
         * then, so it is already saved by the time back is pressed.
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
         *
         * The exclusion above is right, and doing it in silence was not — this is the same fact said out
         * loud (see [leavingOnSave]). The tutor need not have touched the roster
         * to get here: un-enrolling somebody from the subject one-to-one is allowed while the group has
         * taught no lesson, and the next save of the group — a rename is enough — is what drops them.
         * Keeping them in [Form.memberIds] is what lets flipping the subject back restore the roster,
         * so this is a report rather than a reason to prune.
         */
        val strandedMemberIds: Set<Long> get() = form.memberIds - selectedMemberIds

        /**
         * How many of the roster the save would take off it, for want of a price for the subject — asked
         * about **before** the save, not reported after it. The report was a snackbar sent as the form
         * closed, so it went with the screen and the roster shrank in silence after all.
         *
         * Not [strandedMemberIds]: an archived member is out of the candidates but still priced, and the
         * save keeps them.
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

    /**
     * The form as it was last put there by the app rather than by the tutor: the loaded group, or a
     * new group's prefilled rate. Anything the form holds beyond this is unsaved work.
     *
     * A snapshot rather than a "touched" flag because both of those assignments arrive **after** the
     * screen is already on display (they wait on the database), so a flag would have to be un-set
     * again — and a snapshot also forgives a round trip: typing a name and deleting it again leaves
     * nothing to save, and back should not ask about it.
     */
    private val pristine = MutableStateFlow(Form())

    /**
     * Whether the app has finished putting values into the form — what makes [state] [Loadable.Ready].
     *
     * A new group is filled in from the moment it exists: there is no record to read, and the prefilled
     * rate is written into a blank field that is honest without it. An **existing** group is not, and the
     * gap was visible: `init` reads it in a coroutine, so the screen was already up, drawing `UiState()`
     * — a blank name with «Обязательное поле» under it, a rate of nothing, and «У вас нет учеников» in
     * the error red — over a group with six of them. That is the same defect [Loadable] was added for on
     * the list screens, in the one place where the wrong answer is about a record that *does* exist.
     *
     * Separate from [pristine], which asks a different question: this one is "has the app finished", that
     * one is "what did the app put there", and only the second survives the tutor typing over it.
     */
    private val filled = MutableStateFlow(isNew)

    /**
     * Ids priced for the chosen subject, or null while there is no subject (meaning "no restriction").
     * Re-queried whenever the subject changes, so enrolling a student elsewhere shows up here at once.
     */
    private val pricedForSubject: Flow<Set<Long>?> = form
        .map { it.subjectId }
        .distinctUntilChanged()
        .flatMapLatest { subjectId ->
            if (subjectId == null) flowOf(null)
            else students.observeIdsForSubject(subjectId).map { it.toSet() }
        }

    /** The form together with whether it has been edited — one source, since `combine` takes five. */
    private val edited: Flow<Pair<Form, Boolean>> =
        combine(form, pristine) { f, p -> f to (f != p) }

    val state: StateFlow<Loadable<UiState>> = combine(
        edited,
        students.observeActive(),
        subjects.observeAll(),
        pricedForSubject,
        archived,
    ) { (f, dirty), activeStudents, allSubjects, priced, isArchived ->
        UiState(
            form = f,
            candidates = if (priced == null) activeStudents else activeStudents.filter { it.id in priced },
            subjects = allSubjects,
            archived = isArchived,
            pricedIds = priced,
            isDirty = dirty,
        )
    }
        // A sixth source, appended rather than folded in: `combine` takes five, and a new group has no
        // history to observe at all.
        .combine(if (isNew) flowOf(emptyList()) else rateHistory.observeFor(RateOwner.GroupRate(groupId))) {
            state, changes ->
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
     * left in [Form.memberIds] rather than pruned here — recovering them is the point. Flipping back
     * to the previous subject restores the roster you had, while [UiState.selectedMemberIds] keeps
     * them out of anything that counts or saves in the meantime.
     */
    fun setSubject(subjectId: Long?) {
        form.value = form.value.copy(subjectId = subjectId)
    }

    /** Set on the tap and cleared only by a failure — see [save]. */
    private var saving = false

    /**
     * Saves the group and its roster in one step (see `GroupRepository.save`) and reports the group's
     * id, so a freshly created group can go straight on to booking its first lesson.
     *
     * A group with a subject only keeps the members priced for it; the screen asks first when that would
     * leave somebody out ([UiState.leavingOnSave]). The tutor need not have touched the roster at all:
     * un-enrolling somebody from the subject one-to-one is allowed, and the next save of the group — a
     * rename included — is what drops them.
     *
     * **In the application scope, once.** A back press straight after the tap used to cancel the write,
     * a database error closed the app, and a double tap saved a new group twice and popped two screens.
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
                    onSaved(it.id)
                }
                .onFailure {
                    saving = false
                    events.send(Event.SAVE_FAILED)
                }
        }
    }

}
