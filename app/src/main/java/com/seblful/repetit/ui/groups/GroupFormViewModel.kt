package com.seblful.repetit.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.repository.GroupRepository
import com.seblful.repetit.data.repository.PriceRepository
import com.seblful.repetit.data.repository.StudentRepository
import com.seblful.repetit.data.repository.SubjectRepository
import com.seblful.repetit.domain.GroupRules
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.Group
import com.seblful.repetit.domain.model.Student
import com.seblful.repetit.domain.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Creating or editing a group: its name, note, default lesson mode and roster.
 *
 * The roster is picked **subject first** ([Form.subjectId]): choosing the subject narrows the
 * candidates to the students priced for it (`GroupRules.memberCandidates`), which is exactly the set
 * that keeps the group bookable — a group lesson is billed per member, so a member with no price for
 * the subject could not be billed. The subject is a *picking aid only*: a group stays a plain roster
 * and nothing about it is stored, so it isn't saved.
 *
 * The form also surfaces what the roster *can be booked for* ([UiState.bookableSubjects]) — the
 * safety net for a roster assembled across several subjects, so the tutor doesn't discover the gap
 * later in the booking form, where there is nothing they can do about it.
 */
@HiltViewModel
class GroupFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupRepository,
    students: StudentRepository,
    subjects: SubjectRepository,
    prices: PriceRepository,
) : ViewModel() {

    /** 0 for a new group. */
    val groupId: Long = savedStateHandle["id"] ?: 0L

    val isNew: Boolean = groupId == 0L

    data class Form(
        val name: String = "",
        val note: String = "",
        val mode: ClassMode = ClassMode.IN_PERSON,
        val memberIds: Set<Long> = emptySet(),
        /** The subject the roster is being picked for — a filter, not part of the saved group. */
        val subjectId: Long? = null,
    )

    data class UiState(
        val form: Form = Form(),
        /** Every subject, for the picker that drives [candidates]. */
        val subjects: List<Subject> = emptyList(),
        /**
         * Who may be put in the group: active students priced for [Form.subjectId], plus the current
         * members. Empty until a subject is chosen (for a new group) — see
         * `GroupRules.memberCandidates`.
         */
        val candidates: List<GroupRules.MemberCandidate> = emptyList(),
        /** Subjects every chosen member has a price for; empty until the roster shares one. */
        val bookableSubjects: List<Subject> = emptyList(),
        /**
         * Whether there is anybody to put in a group at all. Kept apart from [candidates] being
         * empty, which usually just means "no subject chosen yet" or "nobody is priced for it" —
         * three situations the form has to word differently.
         */
        val hasStudents: Boolean = false,
        val archived: Boolean = false,
    ) {
        /** A group needs a name and at least one member; with neither there is nothing to book. */
        val isValid: Boolean get() = form.name.isNotBlank() && form.memberIds.isNotEmpty()
    }

    private val form = MutableStateFlow(Form())
    private val archived = MutableStateFlow(false)

    val state: StateFlow<UiState> = combine(
        form,
        students.observeActive(),
        subjects.observeAll(),
        prices.observeRatesByStudent(),
        archived,
    ) { f, activeStudents, allSubjects, ratesByStudent, isArchived ->
        UiState(
            form = f,
            subjects = allSubjects,
            candidates = GroupRules.memberCandidates(
                students = activeStudents,
                ratesByStudent = ratesByStudent,
                subjectId = f.subjectId,
                selectedIds = f.memberIds,
            ),
            bookableSubjects = GroupRules.bookableSubjects(
                allSubjects,
                f.memberIds.map { ratesByStudent[it]?.keys.orEmpty() },
            ),
            hasStudents = activeStudents.isNotEmpty(),
            archived = isArchived,
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
                        mode = group.defaultMode,
                        memberIds = groups.getMemberIds(groupId).toSet(),
                    )
                }
            }
        }
    }

    fun update(transform: (Form) -> Form) {
        form.value = transform(form.value)
    }

    /**
     * Picks the subject the roster is filtered for; passing the subject already chosen clears the
     * filter (tapping a selected chip again). Members chosen so far are **kept**: one who has no
     * price for the new subject stays listed and flagged, rather than dropping out of the roster
     * where the tutor wouldn't see them go.
     */
    fun setSubject(subjectId: Long?) {
        form.value = form.value.copy(subjectId = subjectId.takeIf { it != form.value.subjectId })
    }

    fun toggleMember(studentId: Long, on: Boolean) {
        form.value = form.value.copy(
            memberIds = if (on) form.value.memberIds + studentId else form.value.memberIds - studentId,
        )
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
                    defaultMode = f.mode,
                    archived = archived.value,
                ),
                f.memberIds,
            )
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
     * Deletes the group, reporting whether it happened: a group with lessons is refused (its classes
     * must keep naming it), and the screen then offers archiving instead.
     */
    fun delete(onResult: (Boolean) -> Unit) {
        if (isNew) return
        viewModelScope.launch {
            val group = groups.getById(groupId) ?: return@launch onResult(false)
            onResult(groups.delete(group))
        }
    }
}
