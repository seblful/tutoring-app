package com.seblful.klas.ui.today

import androidx.lifecycle.ViewModel
import com.seblful.klas.data.backup.AutoBackup
import com.seblful.klas.data.billing.BalanceRepository
import com.seblful.klas.data.billing.PaymentRepository
import com.seblful.klas.data.billing.balanceOf
import com.seblful.klas.data.homework.HomeworkRepository
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.data.settings.GuideRepository
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.Computation
import com.seblful.klas.domain.guide.FirstSteps
import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.lesson.LessonPhase
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.people.Captions
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.domain.schedule.DayBounds
import com.seblful.klas.domain.schedule.DaySummary
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.days
import com.seblful.klas.format.minutes
import com.seblful.klas.notifications.NotificationPermission
import com.seblful.klas.notifications.RecentMarks
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * One of today's lessons, captioned, with where the clock stands on it.
 *
 * @param minutesAway how long until it starts — negative once it has.
 */
data class TodayLessonRow(
    val session: ClassSession,
    val who: String,
    val subject: String,
    val phase: LessonPhase,
    val minutesAway: Long = 0,
)

/** A student who owes, for Today's «Owed to you»: what, and when they last paid, if ever. */
data class TodayDebtor(val studentId: Long, val name: String, val owed: Money, val lastPaidAt: Instant?)

/** A lesson marked taught outside the app — the widget, a reminder — still within its Undo's window. */
data class TodayMark(val mark: RecentMarks.Mark, val who: String)

/**
 * The working day at a glance: the lesson now or next, the ones over and still to mark, the rest of the day,
 * what is owed, the homework due at today's lessons, and whether the last backup is getting old.
 */
data class TodayState(
    val lessons: List<TodayLessonRow>,
    val outstanding: Reports.Outstanding,
    val homework: List<HomeworkRow>,
    val debtors: List<TodayDebtor> = emptyList(),
    val recentMarks: List<TodayMark> = emptyList(),
    val lastBackupAt: Instant?,
    val backupStale: Boolean,
    val firstSteps: FirstStepsCard? = null,
) {
    /** Over and unaccounted for — the ones "Mark all taught" settles. */
    val toMark: List<TodayLessonRow> get() = lessons.filter { it.phase.isUnfinished }

    /** What the day comes to — lessons, hours and what is billed so far — for its card's header. */
    val summary: DaySummary get() = DaySummary.of(lessons.map { it.session })

    /** The lesson being taught, else the next one today. */
    val next: TodayLessonRow?
        get() = lessons.firstOrNull { it.phase.isRunning } ?: lessons.firstOrNull { it.phase == LessonPhase.UPCOMING }
}

/**
 * The first-steps card while it is out: which steps are done, and the student a payment or homework step opens
 * — the first one taken on, null if there is none to open.
 */
data class FirstStepsCard(val steps: FirstSteps, val student: Long?)

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
    payments: PaymentRepository,
    private val guide: GuideRepository,
    private val notificationPermission: NotificationPermission,
    private val recentMarks: RecentMarks,
    private val clock: WallClock,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Computation computation: CoroutineDispatcher,
) : ViewModel() {

    sealed interface Event {
        /** Lessons just marked taught, for the snackbar's one Undo. */
        data class Marked(val sessions: List<ClassSession>) : Event

        /** An undo found a lesson changed since it was marked, and left it. */
        data object UndoChanged : Event

        /** The first-steps card was put away, for the snackbar's Undo. */
        data object FirstStepsHidden : Event
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
                TodayLessonRow(
                    session = session,
                    who = names.titleOf(session),
                    subject = names.subjectOf(session),
                    phase = LessonPhase.of(session, now),
                    minutesAway = (session.start - now).inWholeMinutes,
                )
            }
        }

    /** What is owed in all, and by whom — most first, each with when they last paid. */
    private val owed: Flow<Pair<Reports.Outstanding, List<TodayDebtor>>> =
        combine(
            balances.observe(),
            students.observeActive(),
            payments.observeBetween(null, null),
        ) { sums, active, paid ->
            val owing = active.map { sums.balanceOf(it.id) }
            val lastPaid = paid.groupBy { it.studentId }.mapValues { (_, theirs) -> theirs.maxOf { it.date } }
            val names = active.associateBy { it.id }
            val outstanding = Reports.Outstanding.of(owing)
            val debtors = outstanding.debts.mapNotNull { debt ->
                names[debt.student.value]?.let { TodayDebtor(it.id, it.name, debt.amount, lastPaid[it.id]) }
            }
            outstanding to debtors
        }

    /** Marks made outside the app within their window, newest first — see [RecentMarks]. */
    private val recent: Flow<List<TodayMark>> =
        combine(recentMarks.marks, captions, clock.minutes()) { marks, names, now ->
            marks.filter { now - it.at < RecentMarks.WINDOW }
                .sortedByDescending { it.at }
                .map { TodayMark(it, names.titleOf(it.session)) }
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

    /** Whether the phone lets reminders through — asked again on every return to Today (see [refreshPermission]). */
    private val permitted = MutableStateFlow(notificationPermission.isGranted())

    /**
     * The card until it is put away. Not read at all after that, so a tutor long past their first week does not
     * keep a query of every table running for a card nobody sees.
     */
    private val firstSteps: Flow<FirstStepsCard?> = guide.guide.map { it.firstStepsHidden }.distinctUntilChanged()
        .flatMapLatest { hidden ->
            if (hidden) {
                flowOf(null)
            } else {
                combine(guide.progress, settings.observe(), permitted, students.observeActive()) {
                        done,
                        current,
                        allowed,
                        active,
                    ->
                    FirstStepsCard(
                        steps = FirstSteps.of(done, reminded = current.notificationsEnabled && allowed),
                        student = active.minByOrNull { it.id }?.id,
                    )
                }
            }
        }

    private val day: Flow<TodayState> =
        combine(lessons, owed, homeworkDue, backup, recent) {
                rows,
                (total, debtors),
                due,
                (lastBackupAt, stale),
                marks,
            ->
            TodayState(
                lessons = rows,
                outstanding = total,
                homework = due,
                debtors = debtors,
                recentMarks = marks,
                lastBackupAt = lastBackupAt,
                backupStale = stale,
            )
        }

    val state: StateFlow<Loadable<TodayState>> =
        combine(day, firstSteps) { today, steps -> today.copy(firstSteps = steps) }
            .ready()
            .derive(this, computation, Loadable.Loading)

    fun markTaught(row: TodayLessonRow) = mark(listOf(row.session))

    /** Asks the platform again: the tutor may have allowed notifications in system settings meanwhile. */
    fun refreshPermission() {
        permitted.value = notificationPermission.isGranted()
    }

    fun hideFirstSteps() {
        appScope.launch {
            guide.setFirstStepsHidden(true)
            events.send(Event.FirstStepsHidden)
        }
    }

    fun showFirstSteps() {
        appScope.launch { guide.setFirstStepsHidden(false) }
    }

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

    /** Takes back a mark made outside the app, only if nothing about the lesson has changed since. */
    fun undo(recent: TodayMark) {
        recentMarks.forget(recent.mark.session)
        appScope.launch {
            val session = recent.mark.session
            if (!classes.correctCompletion(session, unchanged = session)) events.send(Event.UndoChanged)
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
