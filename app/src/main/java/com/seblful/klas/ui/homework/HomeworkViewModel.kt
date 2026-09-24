package com.seblful.klas.ui.homework

import androidx.lifecycle.ViewModel
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.Captions
import com.seblful.klas.domain.Homework
import com.seblful.klas.domain.model.HomeworkResult
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.ui.common.Loadable
import com.seblful.klas.ui.common.UiEvents
import com.seblful.klas.ui.common.derive
import com.seblful.klas.ui.common.ready
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * What the «Задания» tab shows — [Homework.Worklist], captioned.
 *
 * The tab's two sides are [toCheck] and [handedOut]; each carries the sections it is drawn in, so the
 * screen never decides which list a piece of homework belongs on.
 */
data class HomeworkLists(
    val today: List<HomeworkRow>,
    val overdue: List<HomeworkRow>,
    val waiting: List<HomeworkRow>,
    val unbooked: List<HomeworkRow>,
) {
    /** How many are on «Проверить» — what its segment counts. */
    val toCheck: Int get() = today.size + overdue.size

    /** How many are on «Выдано». */
    val handedOut: Int get() = waiting.size + unbooked.size
}

@HiltViewModel
class HomeworkViewModel @Inject constructor(
    private val classes: ClassRepository,
    students: StudentRepository,
    subjects: SubjectRepository,
    groups: GroupRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation computation: CoroutineDispatcher,
    private val clock: Clock,
) : ViewModel() {

    private val zone = clock.zone

    /**
     * The clock, **re-read at every local midnight** — what "today" and "due today" are measured from.
     *
     * The tab stays alive behind the bar, so a list derived once would go on calling yesterday's lessons
     * today's until something else changed. Sleeping until midnight rather than polling, for the reason
     * `ScheduleViewModel.today` gives: the answer changes once a day and it is knowable when.
     *
     * **A test that collects [lists] must advance time by a stated amount, never `advanceUntilIdle()`**:
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
     * The tab's lists — [Loadable] because «Проверять нечего» is an answer, and the frame before the query
     * returns is not one.
     *
     * The read starts [Homework.FOLLOWED_UP_FOR] back from when the tab was opened and runs open-ended
     * ([ClassRepository.observeForHomework]); the cut itself is re-applied against [days], so a tab kept
     * open for a week drops what went stale in it.
     */
    val lists: StateFlow<Loadable<HomeworkLists>> =
        combine(
            classes.observeForHomework(clock.instant()),
            students.observeAll(),
            subjects.observeAll(),
            groups.observeAll(),
            days,
        ) { classList, studentList, subjectList, groupList, now ->
            val captions = Captions.of(studentList, subjectList, groupList)
            val worklist = Homework.worklist(classList, now, zone, studentList, groupList)
            fun List<Homework.Assignment>.rows() = map { HomeworkRow.of(it, captions) }
            HomeworkLists(
                today = worklist.today.rows(),
                overdue = worklist.overdue.rows(),
                waiting = worklist.waiting.rows(),
                unbooked = worklist.unbooked.rows(),
            )
        }
            .ready()
            .derive(this, computation, Loadable.Loading)

    /** One-shot signals from an action that did not go through. */
    enum class Event {
        /** A verdict failed to save — a database error rather than a rule, said so the tap is not lost. */
        ACTION_FAILED,
    }

    val events = UiEvents<Event>()

    /**
     * Records how the student [klass] belongs to did their homework.
     *
     * In [appScope] like every write in the app: marking the last member of a lesson takes the row off the
     * list, and the write must not be cut short because the row it was tapped on went away.
     */
    fun setResult(klass: TutoringClass, result: HomeworkResult) {
        appScope.launch {
            runCatchingCancellable { classes.setHomeworkResult(klass, result) }
                .onFailure { events.send(Event.ACTION_FAILED) }
        }
    }
}
