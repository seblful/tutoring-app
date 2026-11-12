package com.seblful.klas.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.appGraph
import com.seblful.klas.launchAsync
import com.seblful.klas.maintenance.Upkeep
import com.seblful.klas.widget.WidgetUpdates
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * Re-plans after the device's timezone or wall clock is changed — travel, or a manual correction — and redraws
 * the widgets, whose times and day move with the clock. Toggling 24-hour time sends the same broadcast.
 */
class ClockChangeReceiver : BroadcastReceiver() {

    @Inject lateinit var upkeep: Upkeep

    @Inject lateinit var widgets: WidgetUpdates

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        context.appGraph.inject(this)
        if (!handles(intent.action)) return
        launchAsync(appScope) {
            widgets.refresh()
            upkeep.on(Upkeep.Trigger.CLOCK_CHANGED)
        }
    }

    companion object {
        /** The broadcasts this answers — both exempt from the implicit-broadcast limits, so the manifest can ask. */
        val ACTIONS: Set<String> = setOf(Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)

        fun handles(action: String?): Boolean = action in ACTIONS
    }
}
