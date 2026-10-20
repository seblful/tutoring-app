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

/** he app's time-driven scheduling, split by what each half actually needs. */
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

    /** pplies [plans] through [ReminderAlarms], preserving due alarms and rearming future entries. */
    override fun schedule(plans: List<Reminders.Plan>) {
        alarms.set(plans)
    }

    /** rranges the three digests, or cancels one whose instant is null. */
    override suspend fun schedule(plan: Digests.Plan) {
        arrange<AgendaWorker>(AGENDA_DIGEST, plan.agendaAt, 1.days)
        arrange<UnfinishedDigestWorker>(UNFINISHED_DIGEST, plan.unfinishedAt, 1.days)
        arrange<DebtDigestWorker>(DEBT_DIGEST, plan.debtsAt, 7.days)
    }

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
