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
import com.seblful.klas.domain.Digests
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.daySummaryText
import com.seblful.klas.format.formatTime
import com.seblful.klas.format.money
import com.seblful.klas.inAppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Creates the app's channels and posts every notification it sends through the platform. */
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

    /**
     * The context every **word** in this class is read from: the application's, in the language the tutor
     * chose ([com.seblful.klas.inAppLanguage]).
     *
     * [context] itself cannot be it. The application's base context is pinned to whatever language the
     * *phone* asks for, because the settings row cannot be read before a context exists (`KlasApp`), and a
     * notification is the one surface with no composition to be handed a localized one by — so this is where
     * the app's own answer arrives. It matters for the plural rules as much as for the words: a quantity's
     * form comes from the configuration's locale, not from the folder the string was loaded from.
     *
     * Read per use rather than held, so a language changed in Settings applies to the next notification
     * without this singleton having to be told. Everything that is *not* a word — the system service, the
     * `PendingIntent`s, the builders — stays on [context], which is the one the platform expects.
     */
    private val words: Context get() = context.inAppLanguage()

    override fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        // One channel per kind, so each can be tuned or silenced on its own in system settings. The
        // importances are the difference between them stated in the platform's own terms: a reminder
        // interrupts because a lesson is about to start, a digest arrives at an hour the tutor chose, and
        // the upkeep report is news about something already done.
        manager.createNotificationChannel(
            channel(
                CHANNEL_REMINDERS,
                R.string.reminder_channel_name,
                R.string.reminder_channel_desc,
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        manager.createNotificationChannel(
            channel(
                CHANNEL_AGENDA,
                R.string.agenda_channel_name,
                R.string.agenda_channel_desc,
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            channel(
                CHANNEL_UNFINISHED,
                R.string.unfinished_channel_name,
                R.string.unfinished_channel_desc,
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            channel(
                CHANNEL_DEBTS,
                R.string.debts_channel_name,
                R.string.debts_channel_desc,
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            channel(
                CHANNEL_UPKEEP,
                R.string.upkeep_channel_name,
                R.string.upkeep_channel_desc,
                // LOW: no sound, no peek. The tutor is being told what the app did while they were away,
                // which is worth being able to read and never worth interrupting anything for.
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    /**
     * Vibration and sound are left to the channel's importance — **the phone's answer, not this app's.**
     *
     * No `setVibrationPattern` and no `setSound` anywhere here, for the same reason `ui/common/Haptics`
     * refuses to touch `Vibrator`: a duration this app invented would override the device's own haptic
     * strength and whatever the tutor has already chosen for it. `IMPORTANCE_HIGH` and
     * `IMPORTANCE_DEFAULT` vibrate by default; `IMPORTANCE_LOW` does not. That is the whole of the app's
     * opinion on the subject, and it is expressed in the one vocabulary system settings also speaks.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun channel(id: String, nameRes: Int, descRes: Int, importance: Int): NotificationChannel =
        NotificationChannel(id, words.getString(nameRes), importance)
            .apply { description = words.getString(descRes) }

    /** No-ops silently if notification permission is missing. */
    override fun showClassReminder(
        klass: TutoringClass,
        whoName: String,
        subjectName: String,
        homeworkToCheck: String?,
    ) {
        if (!permission.isGranted()) return
        ensureChannels()

        // 12- or 24-hour from the phone, like every other time the tutor is shown. A reminder is read
        // *on* the phone — it is only "not a screen" in the sense that this class draws it, and the
        // first pass at following the system clock drew the line there and missed this. A lesson booked
        // at 9:00 PM was announced as «21:00»: one clock to set it on and another to be reminded of it.
        val time = klass.start.formatTime(hour12 = hour12())
        val headline = bulletJoin(time, subjectName, whoName)
        // In the expanded text only: the collapsed line stays the lesson, which is what the tray is sorted by
        // at a glance, and the homework is what the tutor opens it to read.
        val expanded = homeworkToCheck
            ?.let { "$headline\n${words.getString(R.string.reminder_homework_to_check, it)}" }
            ?: headline

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(words.getString(R.string.reminder_title))
            .setContentText(headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(klass.id.toInt()))
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

    /**
     * The tray entry several reminders collapse under.
     *
     * A group needs one or the members are drawn as ordinary separate notifications on Android 7 through
     * 11 — the grouping is silently ignored without it. It carries no actions and no time of its own,
     * because it is not about a lesson: «Проведено» on a summary of three lessons could only guess which.
     *
     * Re-posted with every reminder rather than tracked, since posting the same id twice updates in place,
     * and it is `setOnlyAlertOnce` so a second reminder arriving does not make the summary buzz again on
     * top of it. It is not cancelled anywhere: the platform removes a summary once its last child is gone.
     */
    private fun remindersSummary() {
        val summary = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(words.getString(R.string.reminder_title))
            .setGroup(GROUP_REMINDERS)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openApp(ID_REMINDERS_SUMMARY))
            .build()
        post(ID_REMINDERS_SUMMARY, summary)
    }

    override fun showAgenda(agenda: Digests.Agenda, currency: Currency) {
        if (!permission.isGranted()) return
        ensureChannels()

        // Which lessons fit and how many are left over is [TrayContent]'s — the clock face and the words
        // are what this class still owns. InboxStyle rather than BigTextStyle: this is a *list*, and the
        // platform draws each line whole and elides the ones that do not fit rather than truncating the
        // middle of a sentence.
        val posting = TrayContent.agenda(agenda)
        val style = NotificationCompat.InboxStyle()
        val hour12 = hour12()
        posting.lines.forEach { entry ->
            style.addLine(bulletJoin(entry.start.formatTime(hour12 = hour12), entry.subject, entry.who))
        }
        if (posting.more > 0) {
            style.setSummaryText(words.getString(R.string.agenda_more, posting.more))
        }

        // The same sentence the schedule prints under the day it names — see [daySummaryText]. Never
        // null here: `Agenda.isWorthPosting` has already refused an empty day, and it is the only way in.
        val summary = daySummaryText(words.resources, agenda.summary, currency).orEmpty()

        val notification = NotificationCompat.Builder(context, CHANNEL_AGENDA)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(words.getString(R.string.agenda_title))
            .setContentText(summary)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(ID_AGENDA))
            .build()

        post(ID_AGENDA, notification)
    }

    override fun showUnfinished(unfinished: Digests.Unfinished) {
        if (!permission.isGranted()) return
        ensureChannels()

        // The same list the morning agenda is drawn as, capped the same way — see [TrayContent].
        val posting = TrayContent.unfinished(unfinished)
        val style = NotificationCompat.InboxStyle()
        val hour12 = hour12()
        posting.lines.forEach { entry ->
            style.addLine(bulletJoin(entry.start.formatTime(hour12 = hour12), entry.subject, entry.who))
        }
        if (posting.more > 0) {
            style.setSummaryText(words.getString(R.string.agenda_more, posting.more))
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_UNFINISHED)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(words.getString(R.string.unfinished_title))
            // The count, not a total: nothing here has billed, so there is no money to print beside it.
            .setContentText(
                words.resources.getQuantityString(
                    R.plurals.unfinished_lessons,
                    unfinished.count,
                    unfinished.count,
                ),
            )
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            // Straight to today's schedule, where each of these is one swipe from being marked. No
            // action buttons: «Проведено» on a list of five lessons could only guess, and this one bills.
            .setContentIntent(openApp(ID_UNFINISHED, TrayNavigation.ACTION_OPEN_TODAY))
            .build()

        post(ID_UNFINISHED, notification)
    }

    override fun showDebts(debts: Digests.Debts, currency: Currency) {
        if (!permission.isGranted()) return
        ensureChannels()

        val body = bulletJoin(
            words.resources.getQuantityString(R.plurals.students_count, debts.students, debts.students),
            debts.total.money(currency),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_DEBTS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(words.getString(R.string.debts_title))
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            // Straight to the report that lists them, where each debt row opens the student — the tray can
            // say how much is owed in total and nothing at all about by whom.
            .setContentIntent(openApp(ID_DEBTS, TrayNavigation.ACTION_OPEN_DEBTS))
            .build()

        post(ID_DEBTS, notification)
    }

    override fun showAutoCompleted(lessons: Int, billed: Money, currency: Currency) {
        if (!permission.isGranted()) return
        ensureChannels()

        val body = bulletJoin(
            words.resources.getQuantityString(R.plurals.lessons_count, lessons, lessons),
            billed.money(currency),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPKEEP)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(words.getString(R.string.upkeep_completed_title))
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openApp(ID_UPKEEP))
            .build()

        post(ID_UPKEEP, notification)
    }

    /**
     * Posts [notification], defending against the permission being revoked between the check at the top of
     * each method and the post at the bottom of it.
     */
    private fun post(id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission was revoked — nothing to show.
        }
    }

    /** The phone's clock, asked here because a notification has no composition to ask it in. */
    private fun hour12(): Boolean = !DateFormat.is24HourFormat(context)

    /**
     * Opening the app from a tray entry.
     *
     * `FLAG_ACTIVITY_SINGLE_TOP` so a notification never stacks a second copy of the one activity on top
     * of the app the tutor already has open. [action] carries where to land when it is somewhere other
     * than wherever they left off — see [TrayNavigation].
     */
    private fun openApp(requestCode: Int, action: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { action?.let { setAction(it) } }
        return PendingIntent.getActivity(context, requestCode, intent, FLAGS)
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
            .putExtra(TrayNavigation.EXTRA_CLASS_ID, classId)
        return action(
            R.string.class_status_completed,
            PendingIntent.getBroadcast(context, classId.toInt(), intent, FLAGS),
        )
    }

    /**
     * «Перенести» — opens the lesson, because a new time cannot be picked from a notification. The
     * tutor lands on the lesson's own screen with its date and time, which is where the app moves a
     * lesson anyway; see [TrayNavigation] for the rule that reads this back.
     */
    private fun rescheduleAction(classId: Long): NotificationCompat.Action {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(TrayNavigation.ACTION_OPEN_LESSON)
            .putExtra(TrayNavigation.EXTRA_CLASS_ID, classId)
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
        NotificationCompat.Action.Builder(0, words.getString(labelRes), intent).build()

    companion object {
        const val CHANNEL_REMINDERS = "class_reminders"
        const val CHANNEL_AGENDA = "daily_agenda"
        const val CHANNEL_UNFINISHED = "unfinished_digest"
        const val CHANNEL_DEBTS = "debt_digest"
        const val CHANNEL_UPKEEP = "upkeep_report"

        /** What several lesson reminders collapse under in the tray. */
        private const val GROUP_REMINDERS = "com.seblful.klas.REMINDERS"

        /**
         * The ids of the notifications there is only ever one of, kept **negative** so they cannot
         * collide with a reminder's — a reminder is identified by its class id, and those are positive
         * `AUTOINCREMENT` row ids. Nothing else needs saying about the numbers themselves; what matters is
         * that the space they come from is disjoint from the one the database allocates.
         */
        private const val ID_REMINDERS_SUMMARY = -1
        private const val ID_AGENDA = -2
        private const val ID_DEBTS = -3
        private const val ID_UPKEEP = -4
        private const val ID_UNFINISHED = -5

        /**
         * `UPDATE_CURRENT` so a re-posted reminder carries the lesson's current id in its extras rather
         * than a stale one: the request code is the class id, but a `PendingIntent`'s identity ignores
         * extras entirely, so this is what keeps the buttons pointed at the right lesson.
         */
        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
