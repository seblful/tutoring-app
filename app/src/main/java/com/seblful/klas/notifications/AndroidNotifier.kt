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
import com.seblful.klas.MainActivity
import com.seblful.klas.R
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationContext
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.datetime.LocalDate

/** Creates the app's channels and posts every notification it sends through the platform. */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,

    private val permission: NotificationPermission,
    private val displayLocale: DisplayLocale,
    private val settings: SettingsRepository,
) : Notifier {

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

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun NotificationChannel.carry(from: NotificationChannel) {
        importance = from.importance
        setSound(from.sound, from.audioAttributes)
        enableVibration(from.shouldVibrate())
        vibrationPattern = from.vibrationPattern
        setShowBadge(from.canShowBadge())
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

    /** No-ops silently if notification permission is missing. */
    override fun showClassReminder(
        klass: TutoringClass,
        whoName: String,
        subjectName: String,
        homeworkToCheck: String?,
    ) {
        if (!mayPost()) return

        // 12- or 24-hour from the phone, like every other time the tutor is shown. A reminder is read
        // *on* the phone, so a lesson booked at 9:00 PM must not be announced as «21:00».
        val entry = text().reminder(klass, whoName, subjectName, homeworkToCheck)

        val notification = builder(Kind.REMINDERS, entry.title)
            .setContentText(entry.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(entry.expanded))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // The lesson on the schedule, with its sheet open — what tapping its card does in the app.
            .setContentIntent(
                openApp(klass.id.toInt(), TrayNavigation.Destination.LessonOnSchedule(klass.id)),
            )
            // Reminders stack: a tutor with back-to-back lessons can have two or three in the tray at
            // once, and un-grouped they sit among everything else the phone has to say. Grouping is only
            // half of it — Android needs a *summary* to collapse them under, which is why [remindersSummary]
            // is posted straight after this one.
            .setGroup(GROUP_REMINDERS)
            // The two things a tutor does about the lesson they have just been reminded of, in the order
            // the reminder makes them likely: it happened, or it has to move. Same words as the schedule's
            // own actions — «Проведено» and «Перенести» are one idea each, so they are one string each.
            .addAction(completeAction(klass.id))
            .addAction(rescheduleAction(klass.id))
            .build()

        post(klass.id.toInt(), notification)
        remindersSummary()
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

    override fun showAgenda(agenda: Digests.Agenda, currency: Currency) {
        if (!mayPost()) return

        // InboxStyle rather than BigTextStyle: this is a *list*, and the platform draws each line whole and
        // elides the ones that do not fit rather than truncating the middle of a sentence.
        val notification = listed(Kind.AGENDA, text().agenda(agenda, currency))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openDay(ID_AGENDA, agenda.day))
            .build()

        post(ID_AGENDA, notification)
    }

    override fun showUnfinished(unfinished: Digests.Unfinished) {
        if (!mayPost()) return

        val notification = listed(Kind.UNFINISHED, text().unfinished(unfinished))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Straight to the day's schedule, where each of these is one swipe from being marked. No
            // action buttons: «Проведено» on a list of five lessons could only guess, and this one bills.
            .setContentIntent(openDay(ID_UNFINISHED, unfinished.day))
            .build()

        post(ID_UNFINISHED, notification)
    }

    override fun showDebts(debts: Digests.Debts, currency: Currency) {
        if (!mayPost()) return

        val entry = text().debts(debts, currency)
        val notification = builder(Kind.DEBTS, entry.title)
            .setContentText(entry.text)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            // Straight to the report that lists them, where each debt row opens the student — the tray can
            // say how much is owed in total and nothing at all about by whom.
            .setContentIntent(openApp(ID_DEBTS, TrayNavigation.Destination.Debts))
            .build()

        post(ID_DEBTS, notification)
    }

    override fun showAutoCompleted(lessons: Int, billed: Money, day: LocalDate, currency: Currency) {
        if (!mayPost()) return

        val entry = text().autoCompleted(lessons, billed, currency)
        val notification = builder(Kind.UPKEEP, entry.title)
            .setContentText(entry.text)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openDay(ID_UPKEEP, day))
            .build()

        post(ID_UPKEEP, notification)
    }

    private fun mayPost(): Boolean {
        if (!permission.isGranted()) return false
        ensureChannels()
        return true
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

    /** A digest that lists lessons: its sentence collapsed, its lines expanded. */
    private fun listed(kind: Kind, entry: TrayText.Entry): NotificationCompat.Builder {
        val style = NotificationCompat.InboxStyle()
        entry.lines.forEach(style::addLine)
        entry.more?.let(style::setSummaryText)
        return builder(kind, entry.title).setContentText(entry.text).setStyle(style)
    }

    private fun post(id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission was revoked — nothing to show.
        }
    }

    /** The words, in the app's language and on the phone's clock — the same as iOS's tray says. */
    private fun text() = TrayText(words, displayLocale.tag, hour12 = !DateFormat.is24HourFormat(context))

    private fun openApp(requestCode: Int, destination: TrayNavigation.Destination): PendingIntent {
        val intent = context.openAppIntent(destination).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, requestCode, intent, FLAGS)
    }

    private fun openDay(requestCode: Int, day: LocalDate): PendingIntent =
        openApp(requestCode, TrayNavigation.Destination.Day(day))

    private fun completeAction(classId: Long): NotificationCompat.Action {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(ReminderActionReceiver.ACTION_COMPLETE)
            .putExtra(TrayNavigation.EXTRA_CLASS_ID, classId)
        // It bills the lesson, so from a locked phone it asks for the unlock first (Android 12+).
        return action(
            Strings.class_status_completed,
            PendingIntent.getBroadcast(context, classId.toInt(), intent, FLAGS),
            authenticated = true,
        )
    }

    private fun rescheduleAction(classId: Long): NotificationCompat.Action =
        action(
            Strings.class_move,
            openApp(classId.toInt(), TrayNavigation.Destination.Lesson(classId)),
        )

    private fun action(
        labelRes: StringResource,
        intent: PendingIntent,
        authenticated: Boolean = false,
    ): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, words.string(labelRes), intent)
            .setAuthenticationRequired(authenticated)
            .build()

    /**
     * The five kinds of notification, one channel each so each can be tuned or silenced on its own. Every
     * kind has two channels: a private one that redacts on a secure lock screen, and a shown one that
     * leaves that to the phone ([com.seblful.klas.data.settings.Privacy.lockScreenDetails]). Only one of the
     * two exists at a time; [legacyId] is the channel from before the split, replaced once.
     */
    private enum class Kind(
        val legacyId: String,
        val nameRes: StringResource,
        val descRes: StringResource,
        val importance: Int,
    ) {
        REMINDERS(
            "class_reminders", Strings.reminder_channel_name, Strings.reminder_channel_desc,
            NotificationManager.IMPORTANCE_HIGH,
        ),
        AGENDA(
            "daily_agenda", Strings.agenda_channel_name, Strings.agenda_channel_desc,
            NotificationManager.IMPORTANCE_DEFAULT,
        ),
        UNFINISHED(
            "unfinished_digest", Strings.unfinished_channel_name, Strings.unfinished_channel_desc,
            NotificationManager.IMPORTANCE_DEFAULT,
        ),
        DEBTS(
            "debt_digest", Strings.debts_channel_name, Strings.debts_channel_desc,
            NotificationManager.IMPORTANCE_DEFAULT,
        ),

        // LOW: no sound, no peek. The tutor is being told what the app did while they were away, which is
        // worth being able to read and never worth interrupting anything for.
        UPKEEP(
            "upkeep_report", Strings.upkeep_channel_name, Strings.upkeep_channel_desc,
            NotificationManager.IMPORTANCE_LOW,
        ),
        ;

        fun id(details: Boolean): String = legacyId + if (details) SHOWN else PRIVATE

        /** The channels the set in force replaces: the other set, and the one from before the split. */
        fun replaced(details: Boolean): List<String> = listOf(id(!details), legacyId)
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
