package com.seblful.klas.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.seblful.klas.di.ApplicationContext
import com.seblful.klas.domain.reminders.DigestScheduler
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.domain.reminders.Reminders
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.notifications.ReminderAlarms
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The app's time-driven scheduling, split by what each half actually needs.
 *
 * [refreshNow] — "something changed, work the plan out again" — is deferrable by nature: it is bookkeeping,
 * nobody is waiting on it, and running a minute late costs nothing. That is WorkManager's job, and it
 * stays here. The periodic [MaintenanceWorker] is the same argument on a timer: the safety net that
 * auto-completes due lessons and re-derives reminders even when the app is never opened.
 *
 * [schedule] is **not** deferrable, and used to be treated as though it were. It hands over the reminders
 * themselves, and a reminder is worthless late — so it goes to [ReminderAlarms] and the platform's alarm
 * clock instead of a batched worker. See that class for the whole argument.
 *
 * **One object behind both ports**, deliberately: this is where the app's time-driven work is arranged, and
 * the two interfaces are how the callers stay ignorant of which mechanism — an alarm or a worker — each kind
 * of thing rides on.
 */
@ContributesBinding(AppScope::class, binding = binding<ReminderScheduler>())
@ContributesBinding(AppScope::class, binding = binding<DigestScheduler>())
@SingleIn(AppScope::class)
class WorkScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarms: ReminderAlarms,
    private val clock: WallClock,
) : ReminderScheduler, DigestScheduler {
    private val workManager get() = WorkManager.getInstance(context)

    /** Enqueues the recurring maintenance job. Idempotent — safe to call on every app start. */
    override fun ensurePeriodicMaintenance() {
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(30, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_MAINTENANCE,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Runs maintenance now — after a class changes or when the app opens. */
    override fun refreshNow() {
        val request = OneTimeWorkRequestBuilder<MaintenanceWorker>().build()
        workManager.enqueueUniqueWork(MAINTENANCE_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Applies [plans] through [ReminderAlarms], preserving due alarms and rearming future entries.
     *
     * These used to be `OneTimeWorkRequest`s with an initial delay, which WorkManager treats as a lower
     * bound and is free to batch or defer under Doze. «Занятие через час» delivered two hours late is
     * not a late reminder, it is a wrong one — and `Reminders.shouldPost` would still post it, because
     * the lesson had not ended yet. [ReminderAlarms] uses the platform's alarm scheduling and preserves
     * reminders already owed, including inexact alarms that may still be waiting to fire.
     */
    override fun schedule(plans: List<Reminders.Plan>) {
        alarms.set(plans)
    }

    /**
     * Arranges the three digests, or cancels one whose instant is null.
     *
     * **Periodic work with an initial delay**, rather than the alarms the reminders ride: a digest is a
     * summary, and «сегодня 4 занятия» is still true and still useful twenty minutes after nine. Spending
     * the app's exact-alarm budget on it would put the one feature that genuinely needs punctuality — the
     * reminder before a lesson — in a queue behind two that do not.
     *
     * The initial delay is what puts the first run at the minute the tutor chose; after that WorkManager
     * repeats on its own interval, measured from each completion, so over months it can drift and a clock
     * change shifts it by an hour. That is corrected rather than prevented: [Maintenance] re-derives this
     * plan from `now` at every app start, after a reboot and on every periodic tick, so the delay is
     * recomputed against the wall clock over and over. `CANCEL_AND_REENQUEUE` is therefore the right
     * policy — `KEEP` would make the first plan permanent and `UPDATE` keeps the original period's
     * schedule, which is the drift we are correcting.
     *
     * **With one exception, and it is the whole reason this is `suspend`.** Re-planning that often against
     * a policy that cancels what is enqueued turns a *late* digest into one that never arrives: a 09:00
     * agenda WorkManager had not run yet at 09:12 was re-planned for tomorrow, taking the pending run with
     * it. So a run that is already due is left alone — see [Digests.stillOwed], which is where that rule
     * is stated and tested, and [nextDue], which is how this asks what is arranged.
     */
    override suspend fun schedule(plan: Digests.Plan) {
        arrange<AgendaWorker>(AGENDA_DIGEST, plan.agendaAt, 1.days)
        arrange<UnfinishedDigestWorker>(UNFINISHED_DIGEST, plan.unfinishedAt, 1.days)
        arrange<DebtDigestWorker>(DEBT_DIGEST, plan.debtsAt, 7.days)
    }

    /**
     * Enqueues [name] to run at [at] and then every [every], or cancels it when [at] is null — a digest
     * switched off has to have what is already enqueued taken away, or it would go on arriving after the
     * switch that governs it was turned off.
     *
     * **Nothing here is a decision**, and that is now true rather than asserted. Which of the three things
     * should happen to the queue is [Digests.arrangementFor] — cancel, leave alone, or re-enqueue — and
     * this turns each answer into the WorkManager call for it. The three used to be `if`s in this body,
     * under this same claim, where no JVM test could reach them; two of them are the branches that had
     * already produced a defect.
     *
     * The clock is read **once**, through the injected [WallClock] every other defaulted `now` in the app
     * reads, and handed to the decision.
     */
    private suspend inline fun <reified W : androidx.work.ListenableWorker> arrange(
        name: String,
        at: Instant?,
        every: Duration,
    ) {
        // Only the leave-alone rule consults what is already arranged, and that rule cannot apply without a
        // plan to compare against — so a digest that is off is taken down without a round trip to
        // WorkManager. `arrangementFor` answers `Cancel` either way; this only decides when to ask.
        val arranged = if (at == null) null else nextDue(name)
        val arrangement = Digests.arrangementFor(
            planned = at,
            arranged = arranged,
            now = clock.now(),
            every = every,
        )
        when (arrangement) {
            Digests.Arrangement.Cancel -> workManager.cancelUniqueWork(name)
            Digests.Arrangement.LeaveAlone -> Unit
            is Digests.Arrangement.Enqueue -> {
                // Millis and a `TimeUnit`, not the `java.time.Duration` overloads: those are
                // `@RequiresApi(26)` on WorkManager's own builders, and `minSdk` is 24. Core-library
                // desugaring makes `Duration` itself fine to *hold* — it is the library methods taking one
                // that are gated, which desugaring cannot help with. The plan is still decided in instants;
                // only this last hand-off is in milliseconds.
                val request = PeriodicWorkRequestBuilder<W>(
                    arrangement.every.inWholeMilliseconds,
                    TimeUnit.MILLISECONDS,
                )
                    .setInitialDelay(arrangement.delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    name,
                    ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                    request,
                )
            }
        }
    }

    /**
     * When the work already arranged under [name] is next due, or null when nothing is waiting to run.
     *
     * WorkManager is asked rather than a note being kept beside it: it is the thing that actually holds the
     * arrangement, and a private copy could disagree with it after a reboot, a force-stop or an app update.
     * Only [WorkInfo.State.ENQUEUED] counts — work that is running needs no protecting, and work that has
     * finished is not owed.
     */
    private suspend fun nextDue(name: String): Instant? =
        workManager.getWorkInfosForUniqueWorkFlow(name).first()
            .firstOrNull { it.state == WorkInfo.State.ENQUEUED }
            ?.nextScheduleTimeMillis
            // What WorkManager reports for work with nothing scheduled.
            ?.takeIf { it != Long.MAX_VALUE }
            ?.let { Instant.fromEpochMilliseconds(it) }

    companion object {
        const val PERIODIC_MAINTENANCE = "maintenance-periodic"
        const val MAINTENANCE_NOW = "maintenance-now"
        const val AGENDA_DIGEST = "digest-agenda"
        const val UNFINISHED_DIGEST = "digest-unfinished"
        const val DEBT_DIGEST = "digest-debts"
    }
}
