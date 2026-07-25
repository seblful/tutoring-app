package com.seblful.repetit.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.repository.GroupRepository
import com.seblful.repetit.data.repository.StudentRepository
import com.seblful.repetit.data.repository.SubjectRepository
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

/**
 * A group as the list shows it: the group, the names of the students in it, and what it is taught.
 *
 * [subjectName] is null for a group made before subjects were fixed on the group — the row then says
 * nothing about a subject rather than claiming one.
 */
data class GroupRow(val group: Group, val memberNames: List<String>, val subjectName: String? = null)

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
    subjects: SubjectRepository,
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
                subjects.observeAll(),
            ) { groupList, membershipsByStudent, studentList, subjectList ->
                val namesById = studentList.associate { it.id to it.name }
                val subjectsById = subjectList.associate { it.id to it.name }
                groupList.map { group ->
                    val names = membershipsByStudent
                        .filterValues { group.id in it }
                        .keys
                        .mapNotNull { namesById[it] }
                        .sorted()
                    GroupRow(group, names, group.subjectId?.let { subjectsById[it] })
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleArchived() {
        showArchived.value = !showArchived.value
    }
}
