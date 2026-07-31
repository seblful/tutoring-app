package com.seblful.klas.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.seblful.klas.domain.DigestScheduler
import com.seblful.klas.domain.Digests
import com.seblful.klas.domain.ReminderScheduler
import com.seblful.klas.notifications.ReminderAlarms
import com.seblful.klas.domain.Reminders
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

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
 */
@Singleton
class WorkScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarms: ReminderAlarms,
    private val clock: Clock,
) : ReminderScheduler, DigestScheduler {
    private val workManager get() = WorkManager.getInstance(context)

    /** Enqueues the recurring maintenance job. Idempotent — safe to call on every app start. */
    fun ensurePeriodicMaintenance() {
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
     * Replaces every armed reminder with exactly [plans] — as **alarms**, not as deferrable work.
     *
     * These used to be `OneTimeWorkRequest`s with an initial delay, which WorkManager treats as a lower
     * bound and is free to batch or defer under Doze. «Занятие через час» delivered two hours late is
     * not a late reminder, it is a wrong one — and `Reminders.shouldPost` would still post it, because
     * the lesson had not ended yet. Handing this to [ReminderAlarms] is what makes the promised time the
     * actual one.
     */
    override fun schedule(plans: List<Reminders.Plan>) {
        alarms.set(plans)
    }

    /**
     * Arranges the two digests, or cancels one whose instant is null.
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
        arrange<AgendaWorker>(AGENDA_DIGEST, plan.agendaAt, Duration.ofDays(1))
        arrange<DebtDigestWorker>(DEBT_DIGEST, plan.debtsAt, Duration.ofDays(7))
    }

    /**
     * Enqueues [name] to run at [at] and then every [every], or cancels it when [at] is null — a digest
     * switched off has to have what is already enqueued taken away, or it would go on arriving after the
     * switch that governs it was turned off.
     *
     * **Nothing here is a decision.** The instant comes from `Digests.plan`, the delay from
     * [Digests.delayUntil], and whether a pending run is still owed from [Digests.stillOwed] — this reads
     * the clock **once**, through the injected [Clock] every other defaulted `now` in the app reads, and
     * does what it is told. It used to compute the delay against an `Instant.now()` of its own, which was
     * both a second clock read and the last thing in `main/` that no JVM test could reach.
     */
    private suspend inline fun <reified W : androidx.work.ListenableWorker> arrange(
        name: String,
        at: Instant?,
        every: Duration,
    ) {
        if (at == null) {
            workManager.cancelUniqueWork(name)
            return
        }
        val now = clock.instant()
        // A run that is already due and has not happened yet is left alone: applying a new plan replaces
        // what is enqueued, and replacing a *due* digest is how a late one became one that never arrived.
        if (Digests.stillOwed(arranged = nextDue(name), planned = at, now = now)) return
        // Millis and a `TimeUnit`, not the `java.time.Duration` overloads: those are `@RequiresApi(26)` on
        // WorkManager's own builders, and `minSdk` is 24. Core-library desugaring makes `Duration` itself
        // fine to *hold* — it is the library methods taking one that are gated, which desugaring cannot help
        // with. The plan is still decided in instants; only this last hand-off is in milliseconds.
        val request = PeriodicWorkRequestBuilder<W>(every.toMillis(), TimeUnit.MILLISECONDS)
            .setInitialDelay(Digests.delayUntil(at, now).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            name,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
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
            ?.let(Instant::ofEpochMilli)

    companion object {
        const val PERIODIC_MAINTENANCE = "maintenance-periodic"
        const val MAINTENANCE_NOW = "maintenance-now"
        const val AGENDA_DIGEST = "digest-agenda"
        const val DEBT_DIGEST = "digest-debts"
    }
}
