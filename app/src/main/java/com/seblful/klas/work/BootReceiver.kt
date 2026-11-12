package com.seblful.klas.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.appGraph
import com.seblful.klas.launchAsync
import com.seblful.klas.maintenance.Upkeep
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope

/** Re-derives the reminder plan after a reboot. */
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var upkeep: Upkeep

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        context.appGraph.inject(this)
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        launchAsync(appScope) { upkeep.on(Upkeep.Trigger.BOOT) }
    }
}
