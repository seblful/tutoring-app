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
 * Where a due reminder arrives. `AlarmManager`'s adapter onto [DueReminder] — whether the reminder is
 * still owed, and what it says, are decided there, where a JVM test can reach them.
 *
 * `goAsync` because posting needs the database: `onReceive` runs on the main thread and the process may
 * be killed the moment it returns, so the receiver holds the wake-up open until the work finishes. The
 * finishing is the whole reason to hold it — an alarm that woke the device and then lost its own
 * notification to a dead process is worse than no alarm.
 *
 * The work itself goes to [ApplicationScope], which is where anything that must complete lives, and its
 * handler is what keeps a failed read out of the tutor's face — there is no screen here to report to.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var due: DueReminder

    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val classId = intent.getLongExtra(EXTRA_CLASS_ID, -1L)
        if (classId < 0) return

        val pending = goAsync()
        appScope.launch {
            try {
                runCatchingCancellable { due.post(classId) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.seblful.klas.action.REMIND"
        const val EXTRA_CLASS_ID = "classId"
    }
}
