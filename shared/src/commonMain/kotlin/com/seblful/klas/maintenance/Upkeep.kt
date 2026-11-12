package com.seblful.klas.maintenance

import com.seblful.klas.data.backup.AutoBackup
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.notifications.Notifier
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant

/**
 * What the app does to stay current, for both hosts: they say what happened ([on]) and upkeep decides the
 * rest — a [Maintenance] pass, whether its billing goes to the tray, and whether the weekly copy is due.
 */
@SingleIn(AppScope::class)
class Upkeep @Inject constructor(
    private val maintenance: Maintenance,
    private val autoBackup: AutoBackup,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
    private val clock: WallClock,
) {
    /** What made upkeep run. */
    enum class Trigger {
        /** The app's screens came up, or returned to the front: the tutor is watching, so the tray stays quiet. */
        OPENED,

        /** The periodic tick, or the pass arranged for a lesson's end. */
        PERIODIC,

        /** The phone restarted, losing its alarms. */
        BOOT,

        /** The clock or the time zone changed. */
        CLOCK_CHANGED,

        /** A lesson or a setting changed, so the plans must follow. */
        DATA_CHANGED,
    }

    /**
     * Runs one pass for [trigger]. One nobody is watching reports what it billed to the tray instead of to a
     * screen. The weekly copy is looked at when the app opens, after a reboot and on the periodic tick —
     * never behind each write, as a copy reads the whole database.
     */
    suspend fun on(trigger: Trigger, now: Instant = clock.now()): Maintenance.Settled {
        val settled = maintenance.run(now)
        // A day exactly when something was settled — see [Maintenance.Settled.lastDay].
        val day = settled.lastDay
        if (trigger != Trigger.OPENED && day != null) {
            notifier.showAutoCompleted(settled.lessons, settled.billed, day, settings.get().currency)
        }
        if (trigger in LOOKS_AT_WEEKLY_COPY) autoBackup.runIfDue()
        return settled
    }

    private companion object {
        val LOOKS_AT_WEEKLY_COPY = setOf(Trigger.OPENED, Trigger.BOOT, Trigger.PERIODIC)
    }
}
