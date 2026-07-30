package com.seblful.klas.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.seblful.klas.domain.DigestScheduler
import com.seblful.klas.domain.Digests
import com.seblful.klas.domain.ReminderScheduler
import com.seblful.klas.notifications.ReminderAlarms
import com.seblful.klas.domain.Reminders
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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
     */
    override fun schedule(plan: Digests.Plan) {
        arrange<AgendaWorker>(AGENDA_DIGEST, plan.agendaAt, Duration.ofDays(1))
        arrange<DebtDigestWorker>(DEBT_DIGEST, plan.debtsAt, Duration.ofDays(7))
    }

    /**
     * Enqueues [name] to run at [at] and then every [every], or cancels it when [at] is null — a digest
     * switched off has to have what is already enqueued taken away, or it would go on arriving after the
     * switch that governs it was turned off.
     *
     * The delay is clamped at zero: an instant already past means "the next one is due now", which is what
     * `Digests.plan` cannot produce (it only ever looks forward) but a caller reading a stale plan could.
     */
    private inline fun <reified W : androidx.work.ListenableWorker> arrange(
        name: String,
        at: Instant?,
        every: Duration,
    ) {
        if (at == null) {
            workManager.cancelUniqueWork(name)
            return
        }
        val delay = Duration.between(Instant.now(), at).coerceAtLeast(Duration.ZERO)
        val request = PeriodicWorkRequestBuilder<W>(every)
            .setInitialDelay(delay)
            .build()
        workManager.enqueueUniquePeriodicWork(
            name,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

    companion object {
        const val PERIODIC_MAINTENANCE = "maintenance-periodic"
        const val MAINTENANCE_NOW = "maintenance-now"
        const val AGENDA_DIGEST = "digest-agenda"
        const val DEBT_DIGEST = "digest-debts"
    }
}
