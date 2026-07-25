package com.seblful.repetit.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.repository.GroupRepository
import com.seblful.repetit.data.repository.SettingsRepository
import com.seblful.repetit.data.repository.StudentRepository
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.Group
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.Student
import com.seblful.repetit.format.MoneyInput
import com.seblful.repetit.format.toAmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Creating or editing a group: its name, note, **hourly rate**, default lesson mode and roster.
 *
 * The rate is the group's own — every member of one of its lessons is billed it — so the roster is
 * just people: any active student may join, with no price prerequisite to satisfy first. (Picking the
 * subject up front used to be what made a roster bookable, back when a group lesson billed each member
 * their individual price; a group that prices itself has nothing left to check.)
 *
 * A new group's rate is pre-filled from `AppSettings.defaultGroupPriceMinor` when the tutor has set
 * one, so the common case is one less thing to type.
 */
@HiltViewModel
class GroupFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groups: GroupRepository,
    private val settings: SettingsRepository,
    students: StudentRepository,
) : ViewModel() {

    /** 0 for a new group. */
    val groupId: Long = savedStateHandle["id"] ?: 0L

    val isNew: Boolean = groupId == 0L

    data class Form(
        val name: String = "",
        val note: String = "",
        /** Raw rate text; see [UiState.price]. */
        val priceText: String = "",
        val mode: ClassMode = ClassMode.IN_PERSON,
        val memberIds: Set<Long> = emptySet(),
    )

    data class UiState(
        val form: Form = Form(),
        /** Who may be put in the group: every active student, in display order. */
        val candidates: List<Student> = emptyList(),
        val archived: Boolean = false,
    ) {
        /**
         * The rate as [Money], or null while the field doesn't hold a valid amount. Zero is allowed —
         * a group taught for free still needs booking and billing (at 0) — but blank is not: a group
         * prices its own lessons, so it has to say what they cost.
         */
        val price: Money? get() = MoneyInput.rate(form.priceText)

        /** Whether there is anybody to put in a group at all — worded differently from "nobody picked". */
        val hasStudents: Boolean get() = candidates.isNotEmpty()

        /** A group needs a name, a rate and at least one member; without those there is nothing to book. */
        val isValid: Boolean
            get() = form.name.isNotBlank() && price != null && form.memberIds.isNotEmpty()
    }

    private val form = MutableStateFlow(Form())
    private val archived = MutableStateFlow(false)

    val state: StateFlow<UiState> = combine(
        form,
        students.observeActive(),
        archived,
    ) { f, activeStudents, isArchived ->
        UiState(form = f, candidates = activeStudents, archived = isArchived)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        if (!isNew) {
            viewModelScope.launch {
                groups.getById(groupId)?.let { group ->
                    archived.value = group.archived
                    form.value = Form(
                        name = group.name,
                        note = group.note,
                        priceText = group.price.toAmountInput(),
                        mode = group.defaultMode,
                        memberIds = groups.getMemberIds(groupId).toSet(),
                    )
                }
            }
        } else {
            // Pre-fill the tutor's usual group rate. Only when they have set one (0 means "not set")
            // and only into an untouched field, so an amount already typed is never clobbered by a
            // setting that arrives a moment later.
            viewModelScope.launch {
                val default = settings.get().defaultGroupPriceMinor
                if (default != 0L && form.value.priceText.isBlank()) {
                    form.value = form.value.copy(priceText = Money(default).toAmountInput())
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
                    price = s.price!!,
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
