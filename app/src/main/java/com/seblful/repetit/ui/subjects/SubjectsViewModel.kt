package com.seblful.repetit.ui.subjects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.repetit.data.repository.SubjectRepository
import com.seblful.repetit.domain.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import com.seblful.repetit.ui.common.UiEvents
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
) : ViewModel() {

    /** One-shot UI signals, e.g. a deletion that was blocked because the subject has classes. */
    enum class Event { DELETE_BLOCKED_HAS_CLASSES }

    val events = UiEvents<Event>()

    val subjects: StateFlow<List<Subject>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.upsert(Subject(id = id, name = trimmed)) }
    }

    fun delete(subject: Subject) {
        viewModelScope.launch {
            if (!repository.delete(subject)) events.send(Event.DELETE_BLOCKED_HAS_CLASSES)
        }
    }
}
