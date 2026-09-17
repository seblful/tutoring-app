package com.seblful.klas.notifications

import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.PaymentRepository
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.Billing
import com.seblful.klas.domain.ClassSession
import com.seblful.klas.domain.DaySummary
import com.seblful.klas.domain.Digests
import com.seblful.klas.domain.GroupRules
import com.seblful.klas.domain.LessonPhase
import com.seblful.klas.domain.Reminders
import com.seblful.klas.domain.Reports
import com.seblful.klas.domain.model.LessonStatus
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the three digests do when they come due, except the firing: read what they are about, decide
 * whether they are still worth posting, and post them.
 *
 * Outside the worker for the reason [DueReminder] is: a `CoroutineWorker` can only be driven with a
 * `Context` and `WorkerParameters`, so anything inside one is reachable only from a device. Here it is
 * ordinary code over the repositories and the [Notifier] port, so a JVM test drives all of it and
 * `work/DigestWorker` is left as WorkManager's adapter onto it.
 *
 * **Each method re-reads its own switch.** The work is enqueued when the setting is turned on and
 * cancelled when it is turned off, so in principle neither can run while off — but the enqueued work
 * outlives the process, and "the queue is exactly what the settings say" is a claim about two systems
 * agreeing. Checking here makes the switch authoritative at the moment of posting, which is the same
 * argument `Reminders.shouldPost` makes for a reminder whose lesson may have been cancelled since.
 */
@Singleton
class DueDigest @Inject constructor(
    private val classes: ClassRepository,
    private val students: StudentRepository,
    private val subjects: SubjectRepository,
    private val groups: GroupRepository,
    private val payments: PaymentRepository,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
    private val clock: Clock,
) {
    /**
     * Posts the morning agenda for the day [now] falls in, and reports whether it did.
     *
     * **The whole day, not the rest of it.** The digest prints [DaySummary] — the same «4 занятия · 5 ч ·
     * 240 BYN» the schedule puts under that day — and that figure is what the day *holds*. Listing only
     * the lessons still ahead under a whole-day total would put two different day-lengths in one
     * notification, and the tutor would have no way to tell which of them was the one they were reading.
     *
     * **Cancelled lessons are named by the summary and not by the list**, which is the same division the
     * schedule draws: `DaySummary.lessons` already counts only the lessons that are on, so the lines
     * beneath it are exactly those lessons. A called-off lesson appears as «отменено: 1» at the end of the
     * summary line — it gave its hour back, and a row for it among the day's real work would read as one
     * more thing to do.
     */
    suspend fun postAgenda(now: Instant = clock.instant()): Boolean {
        val current = settings.get()
        if (!current.dailyAgendaEnabled) return false

        val zone = clock.zone
        val day = now.atZone(zone).toLocalDate()
        val dayClasses = classes.observeBetween(
            day.atStartOfDay(zone).toInstant(),
            day.plusDays(1).atStartOfDay(zone).toInstant(),
        ).first()

        // One entry per lesson, a group's included — `GroupRules.sessions` collapses its members' classes
        // and sorts by start, so the tray lists the tutor's morning exactly as the schedule would.
        val sessions = GroupRules.sessions(dayClasses)
        val agenda = Digests.Agenda(
            summary = DaySummary.of(sessions),
            lessons = sessions
                .filter { it.status != LessonStatus.CANCELLED }
                .map { session -> entryFor(session) },
        )

        if (!agenda.isWorthPosting) return false
        notifier.showAgenda(agenda, current.currency)
        return true
    }

    /**
     * Posts the evening list of today's **unfinished** lessons, and reports whether it did.
     *
     * Unfinished is [LessonPhase.OVERDUE] — the lesson ended and its status is still `SCHEDULED` — asked
     * of the same rule auto-completion asks (`ClassRules.shouldAutoComplete`), so the two can never
     * disagree about which lessons those are. Read at the instant the digest fires rather than for the
     * whole evening: a lesson that ends at 21:30 is not yet unmarked at 21:00, and saying it is would be
     * asking the tutor about a lesson they are still teaching.
     *
     * **Today's, not every lesson ever left unmarked.** The day is the unit the tutor closes down, and a
     * digest that dragged in last month's forgotten Tuesday would be a backlog report arriving nightly
     * with no way to dismiss it. The backlog has an owner already: turning auto-completion on sweeps it,
     * and says what it settled.
     *
     * Posts nothing when there is nothing unmarked, by the rule every digest here follows — an evening
     * with no notification is the app saying the day is closed.
     */
    suspend fun postUnfinished(now: Instant = clock.instant()): Boolean {
        val current = settings.get()
        if (!current.unfinishedDigestEnabled) return false

        val zone = clock.zone
        val day = now.atZone(zone).toLocalDate()
        val dayClasses = classes.observeBetween(
            day.atStartOfDay(zone).toInstant(),
            day.plusDays(1).atStartOfDay(zone).toInstant(),
        ).first()

        val digest = Digests.Unfinished(
            lessons = GroupRules.sessions(dayClasses)
                .filter { LessonPhase.of(it, now).isUnfinished }
                .map { session -> entryFor(session) },
        )

        if (!digest.isWorthPosting) return false
        notifier.showUnfinished(digest)
        return true
    }

    /**
     * One line about one lesson — when it was, what it was, and who it was with.
     *
     * Shared by the morning and evening digests because it is the same sentence: the agenda listed the
     * day's lessons this way first, and an evening list that named them differently would be the app
     * describing one lesson two ways in one day.
     */
    private suspend fun entryFor(session: ClassSession): Digests.Agenda.Entry = Digests.Agenda.Entry(
        start = session.start,
        subject = subjects.getById(session.first.subjectId)?.name.orEmpty(),
        // The same rule a reminder is announced by: the group's name when there is one, since a lesson
        // taught to four is not one student's.
        who = Reminders.announcedBy(
            groupName = session.first.groupId?.let { groups.getById(it)?.name },
            studentName = students.getById(session.first.studentId)?.name,
        ),
    )

    /**
     * Posts the weekly summary of who owes money, and reports whether it did.
     *
     * **Active students only**, by the same rule reports follow: archiving a student is how a tutor says
     * they have stopped teaching them, and a weekly reminder about a retired student's balance would make
     * that a decision with a cost. It is also why the app warns before archiving somebody in debt — the
     * debt does not vanish, it simply stops being chased here.
     *
     * A balance spans a student's **whole history** and is deliberately not clipped to a period, which is
     * the one exception `Reports` documents: charges from this year against payments from every year would
     * invent credit.
     */
    suspend fun postDebts(): Boolean {
        val current = settings.get()
        if (!current.debtDigestEnabled) return false

        val balances = Billing.balancesFor(
            students = students.observeActive().first(),
            classes = classes.getAll(),
            payments = payments.observeAll().first(),
        )
        // `Reports.Outstanding` rather than the two aggregations behind it: `ReportsSurfaceTest` keeps
        // callers from reaching past `Reports.of` for pieces of a report and reassembling them, and a
        // summary of what is owed is a thing the reports module should name rather than something a
        // notification works out for itself. It also does not net an overpaid student off against
        // somebody else's debt, which a hand-rolled sum here would be free to get wrong.
        val owed = Reports.Outstanding.of(balances)
        val digest = Digests.Debts(students = owed.students, total = owed.total)

        if (!digest.isWorthPosting) return false
        notifier.showDebts(digest, current.currency)
        return true
    }
}
