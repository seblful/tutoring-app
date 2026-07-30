package com.seblful.klas.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.runCatchingCancellable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-derives the reminder plan after a reboot.
 *
 * Alarms do not survive one — the platform clears every pending alarm on shutdown, and unlike
 * WorkManager (which persists its queue and restores it for you) nothing puts them back. So a phone
 * restarted on a Sunday evening would go the whole of Monday without a single reminder, silently, and
 * only the next class change or maintenance tick would arm them again.
 *
 * The plan is re-derived rather than restored, which is the same rule as everywhere else: [Maintenance]
 * recomputes it from the database, so there is no stale copy to be wrong. Auto-completion rides along,
 * which is right after a reboot too — lessons ended while the phone was off. So do the digests, whose
 * enqueued work WorkManager does restore, but whose firing instant was computed before the phone was off.
 *
 * `runUnwatched`, because nobody is: a phone that has just come back up may have missed a whole day of
 * lessons ending, and settling them is what bills them. The tutor is told what it settled rather than
 * finding the number changed.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var maintenance: Maintenance

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        appScope.launch {
            try {
                runCatchingCancellable { maintenance.runUnwatched() }
            } finally {
                pending.finish()
            }
        }
    }
}
