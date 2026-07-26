package com.seblful.klas.ui.subjects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.di.Computation
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import com.seblful.klas.ui.common.UiEvents
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubjectsViewModel @Inject constructor(
    private val repository: SubjectRepository,
    @param:Computation private val computation: CoroutineDispatcher,
) : ViewModel() {

    /** One-shot UI signals: what stopped a deletion. */
    enum class Event {
        /** Lessons reference the subject, and they are billed history. */
        DELETE_BLOCKED_HAS_CLASSES,

        /**
         * A group is taught it. Told apart from [DELETE_BLOCKED_HAS_CLASSES] because "remove its
         * lessons first" is no help when the blocker is a group that has taught none.
         */
        DELETE_BLOCKED_TAUGHT_TO_GROUP,
    }

    val events = UiEvents<Event>()

    val subjects: StateFlow<List<Subject>> = repository.observeAll()
        .flowOn(computation)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.upsert(Subject(id = id, name = trimmed)) }
    }

    fun delete(subject: Subject) {
        viewModelScope.launch {
            when (repository.delete(subject)) {
                SubjectRepository.Delete.Ok -> Unit
                SubjectRepository.Delete.HasClasses -> events.send(Event.DELETE_BLOCKED_HAS_CLASSES)
                SubjectRepository.Delete.TaughtToGroup -> events.send(Event.DELETE_BLOCKED_TAUGHT_TO_GROUP)
            }
        }
    }
}
