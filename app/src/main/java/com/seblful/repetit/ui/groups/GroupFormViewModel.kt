package com.seblful.repetit.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.repository.GroupRepository
import com.seblful.repetit.data.repository.SettingsRepository
import com.seblful.repetit.data.repository.StudentRepository
import com.seblful.repetit.data.repository.SubjectRepository
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.Group
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.Student
import com.seblful.repetit.domain.model.Subject
import com.seblful.repetit.format.MoneyField
import com.seblful.repetit.ui.common.UiEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import javax.inject.Inject

/**
 * Creating or editing a group: its **subject**, name, note, hourly rate, default lesson mode and roster.
 *
 * The subject is chosen here rather than per lesson, and it is what bounds the roster: only students
 * priced for it can be members, because a group's lessons are all that subject and a member who isn't
 * priced for it could never be billed for one. Picking a subject therefore narrows the candidate list
 * on the spot, and `GroupRepository.save` drops anyone left over.
 *
 * The subject is **optional**, for one reason only: groups made before v6 have none, and there was no
 * honest way to invent one for them (see `Migrations.MIGRATION_5_6`). Such a group keeps the old
 * behaviour — any active student may join and the subject is chosen when booking — until the tutor
 * sets one here.
 *
 * A new group's rate is pre-filled from `AppSettings.defaultGroupPriceMinor` when the tutor has set
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
) : ViewModel() {

    /** 0 for a new group. */
    val groupId: Long = savedStateHandle["id"] ?: 0L

    val isNew: Boolean = groupId == 0L

    data class Form(
        val name: String = "",
        val note: String = "",
        /** The group's hourly rate as typed. Zero is allowed, blank is not — see [UiState.price]. */
        val price: MoneyField = MoneyField(kind = MoneyField.Kind.RATE),
        /** What the group is taught. Null on a pre-v6 group, and until the tutor picks one. */
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
        val archived: Boolean = false,
        /**
         * Whether the form differs from the group as it was loaded (or, for a new one, from the
         * prefilled blank) — what leaving without saving would throw away.
         *
         * Archiving is deliberately **not** part of it: that button writes to the database there and
         * then, so it is already saved by the time back is pressed.
         */
        val isDirty: Boolean = false,
    ) {
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

        /** A group needs a name, a rate and at least one member; without those there is nothing to book. */
        val isValid: Boolean
            get() = form.name.isNotBlank() && price != null && selectedMemberIds.isNotEmpty()
    }

    /** One-shot UI signals, e.g. a deletion blocked because the group already has lessons. */
    enum class Event { DELETE_BLOCKED_HAS_LESSONS }

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

    val state: StateFlow<UiState> = combine(
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
            isDirty = dirty,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

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
            }
        } else {
            // Pre-fill the tutor's usual group rate. Only when they have set one (0 means "not set")
            // and only into an untouched field, so an amount already typed is never clobbered by a
            // setting that arrives a moment later.
            viewModelScope.launch {
                val default = settings.get().defaultGroupPriceMinor
                // 0 means "not set"; prefilledWith leaves an amount already typed alone.
                val suggested = Money(default).takeIf { default != 0L }
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

    /**
     * Saves the group and its roster in one step (see `GroupRepository.save`) and reports the group's
     * id, so a freshly created group can go straight on to booking its first lesson.
     */
    fun save(onSaved: (Long) -> Unit) {
        val s = state.value
        if (!s.isValid) return
        val f = s.form
        viewModelScope.launch {
            val id = groups.save(
                Group(
                    id = groupId,
                    name = f.name.trim(),
                    note = f.note.trim(),
                    price = s.price!!,
                    subjectId = f.subjectId,
                    defaultMode = f.mode,
                    archived = archived.value,
                ),
                s.selectedMemberIds,
            )
            // Saved is the new baseline, so a save that leaves the screen open (or a slow navigation)
            // cannot be followed by "you have unsaved changes".
            pristine.value = f
            onSaved(id)
        }
    }

    fun setArchived(value: Boolean) {
        if (isNew) return
        viewModelScope.launch {
            groups.getById(groupId)?.let {
                groups.setArchived(it, value)
                archived.value = value
            }
        }
    }

    /**
     * Deletes the group and leaves, or reports [Event.DELETE_BLOCKED_HAS_LESSONS] — a group with
     * lessons is refused (its classes must keep naming it) and the screen offers archiving instead.
     *
     * Reported through [events] rather than a `(Boolean) -> Unit`, so a refusal here reaches the
     * tutor the same way every other refusal in the app does.
     */
    fun delete(onDeleted: () -> Unit) {
        if (isNew) return
        viewModelScope.launch {
            val group = groups.getById(groupId)
            if (group != null && groups.delete(group)) onDeleted()
            else events.send(Event.DELETE_BLOCKED_HAS_LESSONS)
        }
    }
}
