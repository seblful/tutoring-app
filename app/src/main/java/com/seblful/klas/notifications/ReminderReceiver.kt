package com.seblful.klas.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seblful.klas.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
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
        // Which ids are real is `TrayNavigation`'s, not this receiver's — see [TrayNavigation.lessonIdOrNull].
        // This used to read a `-1L` default and refuse `< 0`, so lesson **zero** passed the guard here and
        // was refused one layer up: two rules for one question, in the place a notification arrives.
        val classId = TrayNavigation.lessonIdOrNull(intent.getLongExtra(TrayNavigation.EXTRA_CLASS_ID, 0L))
            ?: return

        launchAsync(appScope) { due.post(classId) }
    }

    companion object {
        const val ACTION_REMIND = "com.seblful.klas.action.REMIND"
    }
}
