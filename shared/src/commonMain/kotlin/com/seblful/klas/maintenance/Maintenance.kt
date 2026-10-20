package com.seblful.klas.maintenance

import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.reminders.DigestScheduler
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.domain.reminders.Reminders
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.notifications.Notifier
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Everything the periodic upkeep does, except being periodic: auto-complete the classes whose end
 * time has passed, then re-derive the reminder plan for what is still upcoming.
 */
@SingleIn(AppScope::class)
class Maintenance @Inject constructor(
    private val classes: ClassRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val digests: DigestScheduler,
    private val notifier: Notifier,
    private val clock: WallClock,
) {
    /**
     * How many lessons a pass at [now] **would** auto-complete — the backlog, and deliberately
     * regardless of whether the setting is on: this is what the settings screen puts to the tutor
     * before turning it on, and the sweep only follows if they say yes.
     */
    suspend fun dueForCompletion(now: Instant = clock.now()): Int =
        classes.countDueForCompletion(now)

    /**
     * Runs one upkeep pass, returning **how many lessons it auto-completed** — zero when the setting
     * is off, or when nothing had finished. Counted in lessons, like [dueForCompletion]: a group's
     * hour bills every member but is one lesson settled.
     */
    suspend fun run(now: Instant = clock.now()): Settled {
        val s = settings.get()
        // Auto-completion (and therefore auto-billing) is opt-in; when off, the tutor confirms each
        // lesson by marking it completed.
        val completed = if (s.autoCompleteEnabled) classes.autoCompleteDue(now) else emptyList()
        // The horizon is named once and used twice — to fetch the window and to filter it — because a
        // plan derived over a shorter window than it filters on would quietly drop reminders.
        val horizon = Reminders.DEFAULT_HORIZON
        scheduler.schedule(
            Reminders.plan(
                now = now,
                // Only the classes the plan could keep — the coming week's and the one in progress —
                // not every class ever taught. This runs at
                // every app start, on every periodic tick and behind every class write, and it used to
                // read (and map, and parse the status text of) the tutor's whole history each time to
                // decide about the coming week. `plan` still applies the real filter — the window is
                // deliberately a superset of it.
                classes = classes.notEndedWithin(now, horizon),
                lead = (s.reminderLeadMinutes.toLong()).minutes,
                horizon = horizon,
            ),
        )
        // The three digests are re-planned by the same pass and from the same settings read, for the
        // reason the reminder plan is: this runs at every app start, after a reboot, on every periodic tick and
        // behind every settings change, so there is exactly one place where "what the tutor has asked for"
        // becomes "what is arranged". A digest whose time was changed while the app was closed is put
        // right by the next pass rather than staying wrong until something happened to touch it.
        digests.schedule(
            Digests.plan(
                now = now,
                zone = clock.zone,
                agendaEnabled = s.dailyAgendaEnabled,
                agendaMinuteOfDay = s.agendaMinuteOfDay,
                unfinishedEnabled = s.unfinishedDigestEnabled,
                unfinishedMinuteOfDay = s.unfinishedMinuteOfDay,
                debtsEnabled = s.debtDigestEnabled,
                debtDayOfWeek = s.debtDayOfWeek,
                debtMinuteOfDay = s.debtMinuteOfDay,
            ),
        )
        return Settled(
            lessons = completed.distinctBy { it.lessonId }.size,
            billed = Billing.charged(completed),
            lastDay = completed.maxOfOrNull { it.start }?.toLocalDateTime(clock.zone)?.date,
        )
    }

    /**
     * One upkeep pass **that nobody is watching** — the periodic tick and the pass after a reboot — which
     * reports what it billed to the notification tray instead of to a screen.
     */
    suspend fun runUnwatched(now: Instant = clock.now()): Settled {
        val settled = run(now)
        // A day exactly when something was settled — see [Settled.lastDay].
        val day = settled.lastDay ?: return settled
        notifier.showAutoCompleted(settled.lessons, settled.billed, day, settings.get().currency)
        return settled
    }

    /**
     * What one pass settled: how many **lessons** it auto-completed and what they came to.
     *
     * @param lastDay the day of the latest lesson settled, or null when nothing was — where a tap on the
     * tray's report opens the schedule. The latest rather than the first: a pass after a phone was off
     * for days settles a backlog, and the most recent of it is what the tutor still remembers teaching.
     */
    data class Settled(val lessons: Int, val billed: Money, val lastDay: LocalDate? = null)
}
