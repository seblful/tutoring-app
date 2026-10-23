package com.seblful.klas.ui.subjects

import androidx.lifecycle.ViewModel
import com.seblful.klas.data.people.EnrollmentRepository
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.people.PeopleSearch
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.common.state.write
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class SubjectsViewModel @Inject constructor(
    private val repository: SubjectRepository,
    enrollment: EnrollmentRepository,
    groups: GroupRepository,
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

    /** Subjects just deleted — each said with Undo ([restore]). */
    val deletions = UiEvents<Subject>()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** [Loadable], so the empty state is only ever shown about subjects that were actually read. */
    val subjects: StateFlow<Loadable<List<SubjectRow>>> =
        combine(repository.observeAll(), enrollment.observeAllPrices(), groups.observeAll(), _query) {
                all,
                prices,
                groupList,
                query,
            ->
            val rates = prices.groupBy { it.subjectId }
            val taught = groupList.groupBy { it.subjectId }
            PeopleSearch.filter(all, query) { it.name }.map { subject ->
                SubjectRow.of(subject, rates[subject.id].orEmpty(), taught[subject.id].orEmpty())
            }
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
                    SubjectRepository.Delete.Ok -> deletions.send(subject)
                    SubjectRepository.Delete.HasClasses -> events.send(Event.DELETE_BLOCKED_HAS_CLASSES)
                    SubjectRepository.Delete.TaughtToGroup -> events.send(Event.DELETE_BLOCKED_TAUGHT_TO_GROUP)
                    SubjectRepository.Delete.TakenByStudents -> events.send(Event.DELETE_BLOCKED_TAKEN_BY_STUDENTS)
                }
            },
        ) { repository.delete(subject) }
    }

    /**
     * Puts a just-deleted [subject] back under its id. Safe without asking first: only a subject nothing
     * referenced could be deleted. Refused as [Event.NAME_TAKEN] if its name was given to another since.
     */
    fun restore(subject: Subject) {
        write(onWritten = { restored -> if (!restored) events.send(Event.NAME_TAKEN) }) {
            repository.saveUnlessTaken(subject)
        }
    }

    private fun <T> write(onWritten: suspend (T) -> Unit = {}, block: suspend () -> T) =
        appScope.write(events, Event.WRITE_FAILED, onWritten, block)
}
