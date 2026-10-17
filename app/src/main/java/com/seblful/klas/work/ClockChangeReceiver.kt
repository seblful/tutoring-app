package com.seblful.klas.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.launchAsync
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * Re-plans after the device's timezone or wall clock is changed — travel, or a manual correction.
 *
 * The screens follow a zone change on their own (`DeviceClock.zones`), but nothing armed did: the
 * digests are planned as instants from a wall-clock time **in the zone of the pass that planned them**
 * (`Digests.plan`), so after landing three hours east the 08:00 agenda went on arriving at 05:00 until
 * the next upkeep pass happened to run. Reminder alarms are absolute instants and stay right, but the
 * same pass re-derives them anyway, and a clock set forward may have finished lessons to settle.
 *
 * `runUnwatched` for [BootReceiver]'s reason: the app is usually closed when this arrives.
 */
@AndroidEntryPoint
class ClockChangeReceiver : BroadcastReceiver() {

    @Inject lateinit var maintenance: Maintenance

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (!handles(intent.action)) return
        launchAsync(appScope) { maintenance.runUnwatched() }
    }

    companion object {
        /** The broadcasts this answers — both exempt from the implicit-broadcast limits, so the manifest can ask. */
        val ACTIONS: Set<String> = setOf(Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)

        fun handles(action: String?): Boolean = action in ACTIONS
    }
}
