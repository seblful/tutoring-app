package com.seblful.klas.ui.homework

import androidx.lifecycle.ViewModel
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.HomeworkRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.Captions
import com.seblful.klas.domain.HomeworkRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.format.NameOrder
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.UiEvents
import com.seblful.klas.ui.common.derive
import com.seblful.klas.ui.common.ready
import com.seblful.klas.ui.common.valueOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * What the «Задания» tab shows: its two lists, what it is narrowed to, and what the add dialog is built
 * from.
 *
 * [assignees] and [classes] are held rather than turned into [AssigneeChoice]s here, because a choice
 * says which lesson homework set **now** would go to — and "now" is when the dialog opens, which can be
 * hours after the last emission. See [HomeworkViewModel.choices].
 *
 * @param lists narrowed to [filter] already, so the screen never filters anything itself.
 * @param filter whom the tab is narrowed to, or null for everybody — see [HomeworkRules.narrowedTo].
 * @param assignees everybody homework can be set for, active students then active groups in name order —
 *   what the filter's menu offers too.
 */
data class HomeworkState(
    val lists: HomeworkLists,
    val filter: Assignee?,
    val assignees: List<Assignee>,
    val captions: Captions,
    val classes: List<TutoringClass>,
) {
    /** The filter's name, for the chip that says it is on. */
    val filterName: String? get() = filter?.let(captions::titleOf)
}

@HiltViewModel
class HomeworkViewModel @Inject constructor(
    private val homework: HomeworkRepository,
    students: StudentRepository,
    groups: GroupRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @Computation computation: CoroutineDispatcher,
    private val clock: Clock,
) : ViewModel() {

    private val zone = clock.zone

    private val filter = MutableStateFlow<Assignee?>(null)

    /** Narrows the tab to [who] — a student (with their groups') or a group — or shows everybody with null. */
    fun setFilter(who: Assignee?) {
        filter.value = who
    }

    /** The people homework is for, and who is in which group — what captions, filters and ranks it. */
    private data class People(val students: List<Student>, val groups: List<Group>, val rosters: Map<Long, List<Long>>)

    private val people: Flow<People> =
        combine(students.observeAll(), groups.observeAll(), groups.observeRosters(), ::People)

    /**
     * The clock, **re-read at every local midnight** — what "today" and "overdue" are measured from.
     *
     * The tab stays alive behind the bar, so a list derived once would go on calling yesterday's lesson
     * today's until something else changed. Sleeping until midnight rather than polling, for the reason
     * `ScheduleViewModel.today` gives: the answer changes once a day and it is knowable when.
     *
     * **A test that collects [state] must advance time by a stated amount, never `advanceUntilIdle()`**:
     * a clock has no last emission, so "until idle" never arrives.
     */
    private val days: Flow<Instant> = flow {
        while (true) {
            val now = clock.instant()
            emit(now)
            val midnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            // At least a tick, so a clock already past midnight cannot spin on a zero-length sleep.
            delay(Duration.between(clock.instant(), midnight).toMillis().coerceAtLeast(1))
        }
    }

    /**
     * The tab — [Loadable] because «Текущих заданий нет» is an answer, and the frame before the query
     * returns is not one.
     *
     * Lessons are read from [PICKER_LOOKBACK] back (or from the oldest current homework, if that is
     * older), which covers both what the lists attach homework to and what the dialog ranks by.
     */
    val state: StateFlow<Loadable<HomeworkState>> =
        combine(
            homework.observeWithLessons(since = clock.instant().minus(PICKER_LOOKBACK)),
            people,
            filter,
            days,
        ) { withLessons, people, who, now ->
            val captions = Captions.of(people.students, emptyList(), people.groups)
            val taught = HomeworkRules.taught(withLessons.homework, people.students, people.groups)
            val shown = who?.let { HomeworkRules.narrowedTo(taught, it, people.rosters) } ?: taught
            HomeworkState(
                lists = HomeworkRow.of(
                    HomeworkRules.lists(shown, withLessons.classes),
                    captions,
                    now.atZone(zone).toLocalDate(),
                    zone,
                ),
                filter = who,
                assignees = assigneesOf(people.students, people.groups),
                captions = captions,
                classes = withLessons.classes,
            )
        }
            .ready()
            .derive(this, computation, Loadable.Loading)

    /**
     * Who the add dialog offers, **as of this moment** — whoever's lesson is nearest first, each with the
     * lesson homework set now would be attached to. Empty while there is nobody to set it for, which is the
     * tab's cue to say so instead of opening an empty dialog; **null before the tab has loaded**, which is
     * not the same answer and must not read as "add a student first".
     */
    fun choices(): List<AssigneeChoice>? {
        val loaded = state.value.valueOrNull() ?: return null
        val now = clock.instant()
        return HomeworkRules.byNearestLesson(loaded.assignees, loaded.classes, now).map { assignee ->
            AssigneeChoice(
                assignee = assignee,
                name = loaded.captions.titleOf(assignee),
                nextLessonAt = HomeworkRules.nextLesson(assignee, now, loaded.classes)?.start,
            )
        }
    }

    /**
     * [choices] for correcting [row] — **always including whoever it is for now**, first if they are not
     * among the rest. Otherwise the dialog, which preselects the homework's own assignee from the list,
     * would fall back to somebody else and save the homework onto them.
     */
    fun choicesFor(row: HomeworkRow): List<AssigneeChoice> {
        val all = choices().orEmpty()
        return if (all.any { it.assignee == row.homework.assignee }) all
        else listOf(AssigneeChoice(row.homework.assignee, row.title, row.lessonAt)) + all
    }

    /** One-shot signals from an action that did not go through. */
    enum class Event {
        /** A write failed — a database error rather than a rule, said so the tap is not lost. */
        ACTION_FAILED,
    }

    val events = UiEvents<Event>()

    fun give(assignee: Assignee, text: String) = write { homework.give(assignee, text) }

    fun edit(item: Homework, assignee: Assignee, text: String) = write { homework.edit(item, assignee, text) }

    fun setDone(item: Homework, done: Boolean) = write { homework.setDone(item, done) }

    /** Undoes a swipe on [item], its done state back exactly as it was — see `HomeworkRepository.putBack`. */
    fun putBack(item: Homework) = write { homework.putBack(item) }

    fun delete(item: Homework) = write { homework.delete(item) }

    fun restore(item: Homework) = write { homework.restore(item) }

    /**
     * In [appScope] like every write in the app: a swipe takes the row off the list it was on, and the
     * write must not be cut short because the row it was made on went away.
     */
    private fun write(block: suspend () -> Unit) {
        appScope.launch {
            runCatchingCancellable { block() }.onFailure { events.send(Event.ACTION_FAILED) }
        }
    }

    private companion object {
        /**
         * How far back lessons are read for the dialog's order — a fortnight, so the student taught
         * yesterday still ranks above one not seen since spring.
         */
        val PICKER_LOOKBACK: Duration = Duration.ofDays(14)

        /** Active students, then active groups, each in name order — `byNearestLesson` keeps that for ties. */
        fun assigneesOf(students: List<Student>, groups: List<Group>): List<Assignee> =
            NameOrder.sorted(students.filterNot { it.archived }) { it.name }.map { Assignee.Student(it.id) } +
                NameOrder.sorted(groups.filterNot { it.archived }) { it.name }.map { Assignee.Group(it.id) }
    }
}
