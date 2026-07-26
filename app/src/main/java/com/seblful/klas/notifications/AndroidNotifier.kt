package com.seblful.klas.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
        if (!hasPermission()) return
        ensureChannel()

        val time = klass.start.formatTime()
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

        // Guarded by hasPermission() above; still defend against revocation between check and post.
        try {
            NotificationManagerCompat.from(context).notify(klass.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Notification permission was revoked — nothing to show.
        }
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_REMINDERS = "class_reminders"
    }
}
