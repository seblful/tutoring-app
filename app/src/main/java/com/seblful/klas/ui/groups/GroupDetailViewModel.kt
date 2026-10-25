package com.seblful.klas.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.seblful.klas.data.homework.HomeworkRepository
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.minutes
import com.seblful.klas.lessons.ArchiveChange
import com.seblful.klas.lessons.LessonChanges
import com.seblful.klas.queries.groups.GroupDetails
import com.seblful.klas.queries.groups.GroupDetailsQuery
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.common.state.valueOrNull
import com.seblful.klas.ui.common.state.write
import com.seblful.klas.ui.common.state.writeAwaited
import com.seblful.klas.ui.homework.HomeworkActions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.time.Instant

/** A group's card: who is in it, what it has been taught, and **the whole of its lifecycle**. */
class GroupDetailViewModel @AssistedInject constructor(
    @Assisted savedStateHandle: SavedStateHandle,
    private val groups: GroupRepository,
    details: GroupDetailsQuery,
    homework: HomeworkRepository,
    lessonChanges: LessonChanges,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation private val computation: CoroutineDispatcher,
    private val clock: WallClock,
) : ViewModel() {

    /** Hands over the screen's navigation arguments as the [SavedStateHandle]; the rest is injected. */
    @AssistedFactory
    @ViewModelAssistedFactoryKey(GroupDetailViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): GroupDetailViewModel = create(extras.createSavedStateHandle())

        fun create(@Assisted savedStateHandle: SavedStateHandle): GroupDetailViewModel
    }

    /** One-shot signals: a refused deletion, the lessons archiving called off, or a failed write. */
    sealed interface Event {
        data object DeleteBlockedHasLessons : Event

        /**
         * The card was archived, calling off the lessons in [archive] that were still planned. Shown
         * with Undo ([undoArchive]): it changed the schedule, which is another screen. The count is the
         * write's own, not the card's — a lesson marked taught from the tray while the card was open made
         * the card's figure one too many.
         */
        data class Archived(val archive: ArchiveChange) : Event

        /** An archive was taken back, but [count] of its lessons stayed cancelled — changed, or their hour taken. */
        data class ArchiveUndoKept(val count: Int) : Event

        /** A write failed — a database error rather than a rule. */
        data object WriteFailed : Event
    }

    val events = UiEvents<Event>()

    private val changes = lessonChanges.inScope(appScope) { events.send(Event.WriteFailed) }

    private val homeworkActions = HomeworkActions<Event>(homework, appScope, events, Event.WriteFailed)

    val groupId: Long = checkNotNull(savedStateHandle["id"])

    val state: StateFlow<Loadable<GroupDetails>> = details.observe(groupId)
        .ready()
        .derive(this, computation, Loadable.Loading)

    /**
     * The card: [state] with its next lesson and the few listed under it, re-read every minute so a card left
     * open moves on once a lesson ends. Null until loaded. **A test that collects it must advance time by a
     * stated amount, never `advanceUntilIdle()`** — the minutes never run out.
     */
    val overview: StateFlow<GroupOverview?> = combine(state, clock.minutes()) { loaded, now ->
        loaded.valueOrNull()?.let { GroupOverview.of(it, now, clock.zone) }
    }.derive(this, computation, null)

    /**
     * This minute, for the lessons listed here: one over and unmarked reads «Не отмечено», as on the schedule,
     * not «Запланировано». See `lessonBadgeLabel`.
     */
    val minute: StateFlow<Instant> = clock.minutes().derive(this, computation, clock.now())

    /**
     * Retires the group, or brings it back. Writes straight through — there is no form here to leave
     * unsaved, which is half the reason this belongs on the card rather than on one. Archiving is said
     * with an Undo, as a student's is.
     */
    fun setArchived(value: Boolean) {
        if (!value) {
            return appScope.write(events, Event.WriteFailed) {
                groups.getById(groupId)?.let { groups.setArchived(it, archived = false) }
            }
        }
        appScope.write(
            events,
            Event.WriteFailed,
            onWritten = { archive: ArchiveChange? ->
                if (archive != null) events.send(Event.Archived(archive))
            },
        ) { groups.getById(groupId)?.let { changes.archive(it) } }
    }

    /** Takes [archived] back — see [LessonChanges.Writer.undoArchive]. */
    fun undoArchive(archived: Event.Archived) = appScope.write(
        events,
        Event.WriteFailed,
        onWritten = { kept: Int -> if (kept > 0) events.send(Event.ArchiveUndoKept(kept)) },
    ) { changes.undoArchive(archived.archive) }

    /**
     * Deletes the group and leaves, or reports [Event.DeleteBlockedHasLessons] — a group with
     * lessons is refused (its classes must keep naming it) and the card offers archiving instead.
     */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            appScope.writeAwaited { groups.getById(groupId)?.let { groups.delete(it) } ?: false }
                .onSuccess { if (it) onDeleted() else events.send(Event.DeleteBlockedHasLessons) }
                .onFailure { events.send(Event.WriteFailed) }
        }
    }

    /**
     * Who homework set from this card is for — the group, **as of now**, with the lesson it would be
     * attached to. Taken when the dialog opens, as the student card's is. Null before the card has loaded.
     */
    fun homeworkChoice(): AssigneeChoice? {
        val loaded = state.value.valueOrNull() ?: return null
        val group = loaded.group ?: return null
        val classes = loaded.lessons.flatMap { it.session.classes }
        return AssigneeChoice.of(Assignee.Group(groupId), group.name, classes, clock.now())
    }

    /** Sets homework for the whole group — attached to its next lesson from now. */
    fun giveHomework(text: String) = homeworkActions.give(Assignee.Group(groupId), text)

    /** Corrects [item]'s text; it stays the group's. */
    fun editHomework(item: Homework, text: String) = homeworkActions.edit(item, item.assignee, text)

    fun setHomeworkDone(item: Homework, done: Boolean) = homeworkActions.setDone(item, done)

    /** Undoes a swipe, [item]'s done state back as it was — see `HomeworkRepository.putBack`. */
    fun putHomeworkBack(item: Homework) = homeworkActions.putBack(item)

    fun deleteHomework(item: Homework) = homeworkActions.delete(item)

    fun restoreHomework(item: Homework) = homeworkActions.restore(item)
}
