package com.seblful.klas.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.seblful.klas.MainActivity
import com.seblful.klas.R
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.format.formatTime
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Creates the reminder channel and posts class-reminder notifications through the platform. */
@Singleton
class AndroidNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    /**
     * "May a reminder be shown" has one definition, and it is the port's — this class used to keep a
     * private copy that checked the runtime permission only, so with notifications switched off for
     * the whole app in system settings it went on building and posting notifications that could never
     * appear, while the settings row correctly said they were blocked.
     */
    private val permission: NotificationPermission,
) : Notifier {

    override fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.reminder_channel_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** No-ops silently if notification permission is missing. */
    override fun showClassReminder(klass: TutoringClass, whoName: String, subjectName: String) {
        if (!permission.isGranted()) return
        ensureChannel()

        // 12- or 24-hour from the phone, like every other time the tutor is shown. A reminder is read
        // *on* the phone — it is only "not a screen" in the sense that this class draws it, and the
        // first pass at following the system clock drew the line there and missed this. A lesson booked
        // at 9:00 PM was announced as «21:00»: one clock to set it on and another to be reminded of it.
        val time = klass.start.formatTime(hour12 = !DateFormat.is24HourFormat(context))
        val title = context.getString(R.string.reminder_title)
        val headline = listOf(time, subjectName, whoName)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openApp = PendingIntent.getActivity(
            context,
            klass.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(headline))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            // The two things a tutor does about the lesson they have just been reminded of, in the order
            // the reminder makes them likely: it happened, or it has to move. Same words as the schedule's
            // own actions — «Проведено» and «Перенести» are one idea each, so they are one string each.
            .addAction(completeAction(klass.id))
            .addAction(rescheduleAction(klass.id))
            .build()

        // Guarded above; still defend against revocation between check and post.
        try {
            NotificationManagerCompat.from(context).notify(klass.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Notification permission was revoked — nothing to show.
        }
    }

    override fun cancelClassReminder(classId: Long) {
        NotificationManagerCompat.from(context).cancel(classId.toInt())
    }

    /**
     * «Проведено» — marks the lesson taught without opening anything, which is the whole point of
     * having it here: the tutor is walking out of the lesson, not sitting down with the app.
     *
     * A broadcast rather than an activity, so nothing is drawn; [ReminderActionReceiver] hands it to
     * [ReminderActions], which decides whether the change is still legal.
     */
    private fun completeAction(classId: Long): NotificationCompat.Action {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(ReminderActionReceiver.ACTION_COMPLETE)
            .putExtra(ReminderActionReceiver.EXTRA_CLASS_ID, classId)
        return action(
            R.string.class_status_completed,
            PendingIntent.getBroadcast(context, classId.toInt(), intent, FLAGS),
        )
    }

    /**
     * «Перенести» — opens the lesson, because a new time cannot be picked from a notification. The
     * tutor lands on the lesson's own screen with its date and time, which is where the app moves a
     * lesson anyway; see [ReminderNavigation] for the rule that reads this back.
     *
     * `FLAG_ACTIVITY_SINGLE_TOP` for the reason it is on the tap-to-open intent too — a reminder must
     * not stack a second copy of the one activity on top of the app the tutor already has open.
     */
    private fun rescheduleAction(classId: Long): NotificationCompat.Action {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ReminderNavigation.ACTION_OPEN_LESSON)
            .putExtra(ReminderNavigation.EXTRA_CLASS_ID, classId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return action(
            R.string.class_move,
            PendingIntent.getActivity(context, classId.toInt(), intent, FLAGS),
        )
    }

    /**
     * Icon 0 on purpose: since Android N the platform draws action *labels* only, and every device this
     * app runs on is N or later (minSdk 24). A drawable here would be one nobody ever sees.
     */
    private fun action(labelRes: Int, intent: PendingIntent): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, context.getString(labelRes), intent).build()

    companion object {
        const val CHANNEL_REMINDERS = "class_reminders"

        /**
         * `UPDATE_CURRENT` so a re-posted reminder carries the lesson's current id in its extras rather
         * than a stale one: the request code is the class id, but a `PendingIntent`'s identity ignores
         * extras entirely, so this is what keeps the buttons pointed at the right lesson.
         */
        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
