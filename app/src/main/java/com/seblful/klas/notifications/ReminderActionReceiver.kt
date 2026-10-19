package com.seblful.klas.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.di.appGraph
import com.seblful.klas.launchAsync
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * Where a tap on a reminder's «Проведено» lands. The platform's adapter onto [ReminderActions] — what
 * the tap is allowed to change is decided there, where a JVM test can reach it.
 *
 * A separate receiver from [ReminderReceiver] because they answer to different things: that one is
 * `AlarmManager`'s, and fires when a lesson is *coming*; this one is the notification's, and fires when
 * the tutor has answered one. Same shape, though, and for the same reasons — `goAsync` because the write
 * needs the database and `onReceive` may return before it finishes, and [ApplicationScope] because
 * marking a lesson taught must not be cut in half by a process that has nothing left on screen.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    @Inject lateinit var actions: ReminderActions

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        context.appGraph.inject(this)
        if (intent.action != ACTION_COMPLETE) return
        // One definition of a real class id, shared with the tray's other two readers.
        val classId = TrayNavigation.lessonIdOrNull(intent.getLongExtra(TrayNavigation.EXTRA_CLASS_ID, 0L))
            ?: return

        launchAsync(appScope) { actions.complete(classId) }
    }

    companion object {
        const val ACTION_COMPLETE = "com.seblful.klas.action.COMPLETE"
    }
}
