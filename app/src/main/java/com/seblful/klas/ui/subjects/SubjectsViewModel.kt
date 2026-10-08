package com.seblful.klas.ui.subjects

import androidx.lifecycle.ViewModel
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.PeopleSearch
import com.seblful.klas.domain.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.UiEvents
import com.seblful.klas.ui.common.derive
import com.seblful.klas.ui.common.ready
import com.seblful.klas.ui.common.write
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
class SubjectsViewModel @Inject constructor(
    private val repository: SubjectRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
) : ViewModel() {

    /** One-shot UI signals: what stopped a deletion, or a write that failed. */
    enum class Event {
        /** Lessons reference the subject, and they are billed history. */
        DELETE_BLOCKED_HAS_CLASSES,

        /**
         * A group is taught it. Told apart from [DELETE_BLOCKED_HAS_CLASSES] because "remove its
         * lessons first" is no help when the blocker is a group that has taught none.
         */
        DELETE_BLOCKED_TAUGHT_TO_GROUP,

        /** A student takes it: deleting it would quietly take it off them. */
        DELETE_BLOCKED_TAKEN_BY_STUDENTS,

        /** The name is already another subject's — two of one name cannot be told apart. */
        NAME_TAKEN,

        /** The write failed — a database error, not anything the tutor got wrong. */
        WRITE_FAILED,
    }

    val events = UiEvents<Event>()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** [Loadable], so the empty state is only ever shown about subjects that were actually read. */
    val subjects: StateFlow<Loadable<List<Subject>>> =
        combine(repository.observeAll(), _query) { all, query ->
            PeopleSearch.filter(all, query) { it.name }
        }
            .ready()
            .derive(this, computation, Loadable.Loading)

    fun setQuery(query: String) {
        _query.value = query
    }

    fun save(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        write(onWritten = { saved -> if (!saved) events.send(Event.NAME_TAKEN) }) {
            repository.saveUnlessTaken(Subject(id = id, name = trimmed))
        }
    }

    fun delete(subject: Subject) {
        write(
            onWritten = { outcome ->
                when (outcome) {
                    SubjectRepository.Delete.Ok -> Unit
                    SubjectRepository.Delete.HasClasses -> events.send(Event.DELETE_BLOCKED_HAS_CLASSES)
                    SubjectRepository.Delete.TaughtToGroup -> events.send(Event.DELETE_BLOCKED_TAUGHT_TO_GROUP)
                    SubjectRepository.Delete.TakenByStudents -> events.send(Event.DELETE_BLOCKED_TAKEN_BY_STUDENTS)
                }
            },
        ) { repository.delete(subject) }
    }

    /**
     * The guarded-write tail (`ui/common/Writes.kt`): in the application scope, so a rename outlives a
     * quick back, and a database error reported as [Event.WRITE_FAILED] rather than thrown out of a bare
     * `launch`, where nothing caught it and the app closed.
     */
    private fun <T> write(onWritten: suspend (T) -> Unit = {}, block: suspend () -> T) =
        appScope.write(events, Event.WRITE_FAILED, onWritten, block)
}
