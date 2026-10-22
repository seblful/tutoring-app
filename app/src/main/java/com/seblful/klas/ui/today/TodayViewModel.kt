package com.seblful.klas.ui.today

import androidx.lifecycle.ViewModel
import com.seblful.klas.data.backup.AutoBackup
import com.seblful.klas.data.billing.BalanceRepository
import com.seblful.klas.data.billing.balanceOf
import com.seblful.klas.data.homework.HomeworkRepository
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.people.Captions
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.domain.schedule.DayBounds
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.days
import com.seblful.klas.format.minutes
import com.seblful.klas.queries.homework.HomeworkLists
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.derive
import com.seblful.klas.ui.common.state.ready
import com.seblful.klas.ui.common.state.valueOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** One of today's lessons, captioned, with where the clock stands on it. */
data class TodayLessonRow(val session: ClassSession, val who: String, val subject: String, val phase: LessonPhase)

/**
 * The working day at a glance: the lesson now or next, the ones over and still to mark, the rest of the day,
 * what is owed, the homework due at today's lessons, and whether the last backup is getting old.
 */
data class TodayState(
    val lessons: List<TodayLessonRow>,
    val outstanding: Reports.Outstanding,
    val homework: List<HomeworkRow>,
    val lastBackupAt: Instant?,
    val backupStale: Boolean,
) {
    /** Over and unaccounted for — the ones "Mark all taught" settles. */
    val toMark: List<TodayLessonRow> get() = lessons.filter { it.phase.isUnfinished }

    /** The lesson being taught, else the next one today. */
    val next: TodayLessonRow?
        get() = lessons.firstOrNull { it.phase.isRunning } ?: lessons.firstOrNull { it.phase == LessonPhase.UPCOMING }
}

@OptIn(ExperimentalCoroutinesApi::class)
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class TodayViewModel @Inject constructor(
    private val classes: ClassRepository,
    students: StudentRepository,
    subjects: SubjectRepository,
    groups: GroupRepository,
    balances: BalanceRepository,
    homework: HomeworkRepository,
    settings: SettingsRepository,
    private val clock: WallClock,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation computation: CoroutineDispatcher,
) : ViewModel() {

    sealed interface Event {
        /** Lessons just marked taught, for the snackbar's one Undo. */
        data class Marked(val sessions: List<ClassSession>) : Event

        /** An undo found a lesson changed since it was marked, and left it. */
        data object UndoChanged : Event
    }

    val events = UiEvents<Event>()

    private val captions: Flow<Captions> =
        combine(students.observeAll(), subjects.observeAll(), groups.observeAll()) { s, sub, g ->
            Captions.of(s, sub, g)
        }

    private val todaysSessions: Flow<List<ClassSession>> = clock.days().flatMapLatest { day ->
        val bounds = DayBounds.of(day, clock.zone)
        classes.observeBetween(bounds.start, bounds.end).map { dayClasses ->
            SessionRules.sessions(dayClasses).filter { it.status != LessonStatus.CANCELLED }
        }
    }

    private val lessons: Flow<List<TodayLessonRow>> =
        combine(todaysSessions, captions, clock.minutes()) { sessions, names, now ->
            sessions.sortedBy { it.start }.map { session ->
                TodayLessonRow(session, names.titleOf(session), names.subjectOf(session), LessonPhase.of(session, now))
            }
        }

    private val outstanding: Flow<Reports.Outstanding> =
        combine(balances.observe(), students.observeActive()) { sums, active ->
            Reports.Outstanding.of(active.map { sums.balanceOf(it.id) })
        }

    /** Current homework whose lesson is today, or has passed with it still open. */
    private val homeworkDue: Flow<List<HomeworkRow>> =
        combine(
            homework.observeWithLessons(since = clock.now() - HOMEWORK_LOOKBACK),
            students.observeAll(),
            groups.observeAll(),
            clock.days(),
        ) { withLessons, studentList, groupList, today ->
            val names = Captions.of(studentList, emptyList(), groupList)
            val taught = HomeworkRules.taught(withLessons.homework, studentList, groupList)
            HomeworkLists.of(taught, withLessons.classes, names, today, clock.zone).current
                .filter { it.due == HomeworkRules.Due.TODAY || it.due == HomeworkRules.Due.OVERDUE }
        }

    /**
     * When the last backup was, and whether it is old enough to say so. Never said before there is anyone to
     * lose: a tutor who has just installed the app has nothing to back up.
     */
    private val backup: Flow<Pair<Instant?, Boolean>> =
        combine(settings.observe(), clock.minutes(), students.observeActive()) { s, now, active ->
            s.lastExportAt to (active.isNotEmpty() && AutoBackup.isStale(s.lastExportAt, now))
        }

    val state: StateFlow<Loadable<TodayState>> =
        combine(lessons, outstanding, homeworkDue, backup) { rows, owed, due, (lastBackupAt, stale) ->
            TodayState(
                lessons = rows,
                outstanding = owed,
                homework = due,
                lastBackupAt = lastBackupAt,
                backupStale = stale,
            )
        }
            .ready()
            .derive(this, computation, Loadable.Loading)

    fun markTaught(row: TodayLessonRow) = mark(listOf(row.session))

    /** Every lesson over and still unmarked, taught in one go — and taken back in one go. */
    fun markAllTaught() = mark(state.value.valueOrNull()?.toMark.orEmpty().map { it.session })

    private fun mark(sessions: List<ClassSession>) {
        if (sessions.isEmpty()) return
        appScope.launch {
            val marked = sessions.mapNotNull { session ->
                val written = classes.setStatus(session, LessonStatus.COMPLETED)
                (written as? LessonWrite.Written)?.value?.takeIf { it.isNotEmpty() }
                    ?.let { ClassSession(session.groupId, it) }
            }
            if (marked.isNotEmpty()) events.send(Event.Marked(marked))
        }
    }

    /** Takes back [marked], each only if nothing about it has changed since. */
    fun undo(marked: Event.Marked) {
        appScope.launch {
            val kept = marked.sessions.count { !classes.correctCompletion(it, unchanged = it) }
            if (kept > 0) events.send(Event.UndoChanged)
        }
    }

    private companion object {
        /** Far enough back that homework from a lesson missed last week still shows as overdue. */
        val HOMEWORK_LOOKBACK = 14.days
    }
}
