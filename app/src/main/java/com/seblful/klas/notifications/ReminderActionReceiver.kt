package com.seblful.klas.notifications

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
 * Where a tap on a reminder's «Проведено» lands. The platform's adapter onto [ReminderActions] — what
 * the tap is allowed to change is decided there, where a JVM test can reach it.
 *
 * A separate receiver from [ReminderReceiver] because they answer to different things: that one is
 * `AlarmManager`'s, and fires when a lesson is *coming*; this one is the notification's, and fires when
 * the tutor has answered one. Same shape, though, and for the same reasons — `goAsync` because the write
 * needs the database and `onReceive` may return before it finishes, and [ApplicationScope] because
 * marking a lesson taught must not be cut in half by a process that has nothing left on screen.
 */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {

    @Inject lateinit var actions: ReminderActions

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COMPLETE) return
        val classId = intent.getLongExtra(EXTRA_CLASS_ID, -1L)
        if (classId < 0) return

        val pending = goAsync()
        appScope.launch {
            try {
                runCatchingCancellable { actions.complete(classId) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.seblful.klas.action.COMPLETE"
        const val EXTRA_CLASS_ID = "classId"
    }
}
