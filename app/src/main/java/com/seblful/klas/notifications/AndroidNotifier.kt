package com.seblful.klas.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.seblful.klas.R
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationContext
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** Creates the app's channels and posts every notification it sends through the platform. */
@ContributesBinding(AppScope::class, binding = binding<Notifier>())
@SingleIn(AppScope::class)
class AndroidNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,

    private val permission: NotificationPermission,
    private val displayLocale: DisplayLocale,
    private val settings: SettingsRepository,
) : TrayNotifier(displayLocale) {

    private val words: Texts get() = displayLocale.texts

    /**
     * Creates the channel set in force and removes the other — see [Kind]. The importances are the
     * difference between the kinds stated in the platform's own terms: a reminder interrupts because a
     * lesson is about to start, a digest arrives at an hour the tutor chose, and the upkeep report is news
     * about something already done.
     */
    override fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        // One localized context for all of them: this runs on every cold start and before every
        // notification, re-labelling the channels after a language change.
        val words = words
        val details = details()
        manager.createNotificationChannels(
            Kind.entries.map { kind ->
                words.channel(kind, details).apply {
                    // What the tutor tuned in system settings survives the swap: the channel this one
                    // replaces is read before it is deleted.
                    kind.replaced(details).firstNotNullOfOrNull { manager.getNotificationChannel(it) }
                        ?.let { carry(it) }
                }
            },
        )
        Kind.entries.flatMap { it.replaced(details) }
            .filter { manager.getNotificationChannel(it) != null }
            .forEach { manager.deleteNotificationChannel(it) }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun Texts.channel(kind: Kind, details: Boolean): NotificationChannel =
        NotificationChannel(kind.id(details), string(kind.nameRes), kind.importance).apply {
            description = string(kind.descRes)
            // Redacted on a secure lock screen whatever the phone's own setting says — set here because a
            // channel's lock-screen visibility can only be given as it is created.
            if (!details) lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

    /**
     * Whether the shown set is in force: the tutor's choice once the settings row has been read, and
     * until then whichever set exists — so a cold start never swaps the channels back and forth.
     */
    private fun details(): Boolean = settings.privacy.value?.lockScreenDetails
        ?: (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                context.getSystemService(NotificationManager::class.java)
                    .getNotificationChannel(Kind.REMINDERS.id(details = true)) != null
            )

    override fun hour12(): Boolean = !DateFormat.is24HourFormat(context)

    /**
     * Draws [notice] in the platform's terms: its channel, a style that suits its words, and its tap and
     * buttons as intents. No-ops silently if notification permission is missing.
     */
    override fun post(notice: TrayNotice) {
        if (!permission.isGranted()) return
        ensureChannels()
        val kind = notice.kind.channelKind
        val id = notice.classId?.toInt() ?: kind.fixedId
        val entry = notice.entry
        val builder = builder(kind, entry.title)
            .setContentText(entry.text)
            .setContentIntent(openApp(id, notice.tap))
        when (notice.kind) {
            TrayKind.REMINDER ->
                builder
                    .setStyle(NotificationCompat.BigTextStyle().bigText(entry.expanded))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    // Reminders stack: a tutor with back-to-back lessons can have two or three in the tray at
                    // once, and Android needs a *summary* to collapse them under — [remindersSummary].
                    .setGroup(GROUP_REMINDERS)
            // InboxStyle rather than BigTextStyle: a digest is a *list*, and the platform draws each line
            // whole and elides the ones that do not fit rather than truncating a sentence.
            TrayKind.AGENDA, TrayKind.UNFINISHED ->
                builder
                    .setStyle(
                        NotificationCompat.InboxStyle().also { style ->
                            entry.lines.forEach(style::addLine)
                            entry.more?.let(style::setSummaryText)
                        },
                    )
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
            TrayKind.DEBTS, TrayKind.UPKEEP -> builder.setCategory(NotificationCompat.CATEGORY_STATUS)
        }
        notice.buttons.forEach { builder.addAction(action(it, id)) }
        post(id, builder.build())
        if (notice.kind == TrayKind.REMINDER) remindersSummary()
    }

    override fun cancelClassReminder(classId: Long) {
        NotificationManagerCompat.from(context).cancel(classId.toInt())
    }

    private fun remindersSummary() {
        val summary = builder(Kind.REMINDERS, words.string(Strings.reminder_title))
            .setGroup(GROUP_REMINDERS)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            // Several lessons, all of them within the next hour or so: today's schedule, where they all are.
            .setContentIntent(openApp(ID_REMINDERS_SUMMARY, TrayNavigation.Destination.Today))
            .build()
        post(ID_REMINDERS_SUMMARY, summary)
    }

    private fun builder(kind: Kind, title: String): NotificationCompat.Builder {
        val channel = kind.id(details())
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_klas)
            .setContentTitle(title)
            .setAutoCancel(true)
            // What a lock screen that hides sensitive content shows instead: the kind of news, never whose
            // lesson or how much. Every title is already that neutral; the details wait for the unlock.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_stat_klas)
                    .setContentTitle(title)
                    .setContentText(words.string(Strings.notification_locked_text))
                    .build(),
            )
    }

    private fun post(id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission was revoked — nothing to show.
        }
    }

    private fun openApp(requestCode: Int, destination: TrayNavigation.Destination): PendingIntent {
        val intent = context.openAppIntent(destination).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, requestCode, intent, FLAGS)
    }

    /** [button] as the platform's action; [requestCode] is the notification's, which its intents share. */
    private fun action(button: TrayButton, requestCode: Int): NotificationCompat.Action = when (button) {
        is TrayButton.Complete -> {
            val intent = Intent(context, ReminderActionReceiver::class.java)
                .setAction(ReminderActionReceiver.ACTION_COMPLETE)
                .putExtra(TrayNavigation.EXTRA_CLASS_ID, button.classId)
            NotificationCompat.Action.Builder(
                0,
                words.string(button.label),
                PendingIntent.getBroadcast(context, requestCode, intent, FLAGS),
            )
                // It bills the lesson, so from a locked phone it asks for the unlock first (Android 12+).
                .setAuthenticationRequired(true)
                .build()
        }
        is TrayButton.Open ->
            NotificationCompat.Action.Builder(0, words.string(button.label), openApp(requestCode, button.destination))
                .build()
    }

    /**
     * The five kinds of notification, one channel each so each can be tuned or silenced on its own. Every
     * kind has two channels: a private one that redacts on a secure lock screen, and a shown one that
     * leaves that to the phone ([com.seblful.klas.data.settings.Privacy.lockScreenDetails]). Only one of the
     * two exists at a time; [legacyId] is the channel from before the split, replaced once.
     */
    private enum class Kind(
        val fixedId: Int,
        val legacyId: String,
        val nameRes: StringResource,
        val descRes: StringResource,
        val importance: Int,
    ) {
        REMINDERS(
            ID_REMINDERS_SUMMARY, "class_reminders", Strings.reminder_channel_name, Strings.reminder_channel_desc,
            NotificationManager.IMPORTANCE_HIGH,
        ),
        AGENDA(
            ID_AGENDA, "daily_agenda", Strings.agenda_channel_name, Strings.agenda_channel_desc,
            NotificationManager.IMPORTANCE_DEFAULT,
        ),
        UNFINISHED(
            ID_UNFINISHED, "unfinished_digest", Strings.unfinished_channel_name, Strings.unfinished_channel_desc,
            NotificationManager.IMPORTANCE_DEFAULT,
        ),
        DEBTS(
            ID_DEBTS, "debt_digest", Strings.debts_channel_name, Strings.debts_channel_desc,
            NotificationManager.IMPORTANCE_DEFAULT,
        ),

        // LOW: no sound, no peek. The tutor is being told what the app did while they were away, which is
        // worth being able to read and never worth interrupting anything for.
        UPKEEP(
            ID_UPKEEP, "upkeep_report", Strings.upkeep_channel_name, Strings.upkeep_channel_desc,
            NotificationManager.IMPORTANCE_LOW,
        ),
        ;

        fun id(details: Boolean): String = legacyId + if (details) SHOWN else PRIVATE

        /** The channels the set in force replaces: the other set, and the one from before the split. */
        fun replaced(details: Boolean): List<String> = listOf(id(!details), legacyId)
    }

    private val TrayKind.channelKind: Kind
        get() = when (this) {
            TrayKind.REMINDER -> Kind.REMINDERS
            TrayKind.AGENDA -> Kind.AGENDA
            TrayKind.UNFINISHED -> Kind.UNFINISHED
            TrayKind.DEBTS -> Kind.DEBTS
            TrayKind.UPKEEP -> Kind.UPKEEP
        }

    companion object {
        private const val PRIVATE = ".private"
        private const val SHOWN = ".shown"

        private const val GROUP_REMINDERS = "com.seblful.klas.REMINDERS"

        private const val ID_REMINDERS_SUMMARY = -1
        private const val ID_AGENDA = -2
        private const val ID_DEBTS = -3
        private const val ID_UPKEEP = -4
        private const val ID_UNFINISHED = -5

        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}

/** What the tutor tuned on [from], carried onto its replacement. */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
private fun NotificationChannel.carry(from: NotificationChannel) {
    importance = from.importance
    setSound(from.sound, from.audioAttributes)
    enableVibration(from.shouldVibrate())
    vibrationPattern = from.vibrationPattern
    setShowBadge(from.canShowBadge())
}
