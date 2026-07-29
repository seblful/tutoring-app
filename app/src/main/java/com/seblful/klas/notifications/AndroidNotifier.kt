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
            .build()

        // Guarded above; still defend against revocation between check and post.
        try {
            NotificationManagerCompat.from(context).notify(klass.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Notification permission was revoked — nothing to show.
        }
    }

    companion object {
        const val CHANNEL_REMINDERS = "class_reminders"
    }
}
