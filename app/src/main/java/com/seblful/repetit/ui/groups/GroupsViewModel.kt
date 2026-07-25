package com.seblful.repetit.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.repository.GroupRepository
import com.seblful.repetit.data.repository.StudentRepository
import com.seblful.repetit.domain.model.Group
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A group as the list shows it: the group plus the names of the students in it. */
data class GroupRow(val group: Group, val memberNames: List<String>)

/**
 * The groups list. Read-only on purpose — editing, archiving and deleting a group all live in the
 * group form, so there is one place where a roster changes (mirroring students: a read-only list, a
 * detail screen that mutates).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupsViewModel @Inject constructor(
    groups: GroupRepository,
    students: StudentRepository,
) : ViewModel() {

    val showArchived = MutableStateFlow(false)

    val rows: StateFlow<List<GroupRow>> =
        showArchived.flatMapLatest { archived ->
            val source = if (archived) groups.observeAll() else groups.observeActive()
            combine(
                source,
                groups.observeMembershipsByStudent(),
                // All students, not just active ones: an archived member still explains a roster.
                students.observeAll(),
            ) { groupList, membershipsByStudent, studentList ->
                val namesById = studentList.associate { it.id to it.name }
                groupList.map { group ->
                    val names = membershipsByStudent
                        .filterValues { group.id in it }
                        .keys
                        .mapNotNull { namesById[it] }
                        .sorted()
                    GroupRow(group, names)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleArchived() {
        showArchived.value = !showArchived.value
    }
}
