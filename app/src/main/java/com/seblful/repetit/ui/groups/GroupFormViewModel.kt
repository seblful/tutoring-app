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
 * The form also surfaces what the roster *can be booked for* ([UiState.bookableSubjects]): a group
 * lesson is billed per member, so only a subject every member has a price for can be booked. Showing
 * that while the roster is being built is what keeps the tutor from discovering the gap later, in the
 * booking form, where there is nothing they can do about it.
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
    )

    data class UiState(
        val form: Form = Form(),
        /** Students that may be put in a group — archived ones are left out. */
        val candidates: List<Student> = emptyList(),
        /** Subjects every chosen member has a price for; empty until the roster shares one. */
        val bookableSubjects: List<Subject> = emptyList(),
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
        prices.observePricedSubjectsByStudent(),
        archived,
    ) { f, activeStudents, allSubjects, pricedByStudent, isArchived ->
        UiState(
            form = f,
            candidates = activeStudents,
            bookableSubjects = GroupRules.bookableSubjects(
                allSubjects,
                f.memberIds.map { pricedByStudent[it].orEmpty() },
            ),
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
