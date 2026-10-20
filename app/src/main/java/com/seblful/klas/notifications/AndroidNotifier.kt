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
import com.seblful.klas.di.ApplicationContext
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.daySummaryText
import com.seblful.klas.format.formatTime
import com.seblful.klas.format.inAppLanguage
import com.seblful.klas.format.money
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
) : Notifier {

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
        if (!mayPost()) return

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

        val notification = builder(CHANNEL_REMINDERS, R.string.reminder_title)
            .setContentText(headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // The lesson on the schedule, with its sheet open — what tapping its card does in the app.
            .setContentIntent(
                openApp(klass.id.toInt(), TrayNavigation.ACTION_SHOW_LESSON) {
                    putExtra(TrayNavigation.EXTRA_CLASS_ID, klass.id)
                },
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
        val summary = builder(CHANNEL_REMINDERS, R.string.reminder_title)
            .setGroup(GROUP_REMINDERS)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            // Several lessons, all of them within the next hour or so: today's schedule, where they all are.
            .setContentIntent(openApp(ID_REMINDERS_SUMMARY, TrayNavigation.ACTION_OPEN_TODAY))
            .build()
        post(ID_REMINDERS_SUMMARY, summary)
    }

    override fun showAgenda(agenda: Digests.Agenda, currency: Currency) {
        if (!mayPost()) return

        // Which lessons fit and how many are left over is [TrayContent]'s — the clock face and the words
        // are what this class still owns. InboxStyle rather than BigTextStyle: this is a *list*, and the
        // platform draws each line whole and elides the ones that do not fit rather than truncating the
        // middle of a sentence.
        val style = lessonList(TrayContent.agenda(agenda))

        // The same sentence the schedule prints under the day it names — see [daySummaryText]. Never
        // null here: `Agenda.isWorthPosting` has already refused an empty day, and it is the only way in.
        val summary = daySummaryText(words.resources, agenda.summary, currency).orEmpty()

        val notification = builder(CHANNEL_AGENDA, R.string.agenda_title)
            .setContentText(summary)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openDay(ID_AGENDA, agenda.day))
            .build()

        post(ID_AGENDA, notification)
    }

    override fun showUnfinished(unfinished: Digests.Unfinished) {
        if (!mayPost()) return

        // The same list the morning agenda is drawn as, capped the same way — see [TrayContent].
        val style = lessonList(TrayContent.unfinished(unfinished))

        val notification = builder(CHANNEL_UNFINISHED, R.string.unfinished_title)
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
            // Straight to the day's schedule, where each of these is one swipe from being marked. No
            // action buttons: «Проведено» on a list of five lessons could only guess, and this one bills.
            .setContentIntent(openDay(ID_UNFINISHED, unfinished.day))
            .build()

        post(ID_UNFINISHED, notification)
    }

    override fun showDebts(debts: Digests.Debts, currency: Currency) {
        if (!mayPost()) return

        val body = bulletJoin(
            words.resources.getQuantityString(R.plurals.students_count, debts.students, debts.students),
            debts.total.money(currency),
        )
        val notification = builder(CHANNEL_DEBTS, R.string.debts_title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            // Straight to the report that lists them, where each debt row opens the student — the tray can
            // say how much is owed in total and nothing at all about by whom.
            .setContentIntent(openApp(ID_DEBTS, TrayNavigation.ACTION_OPEN_DEBTS))
            .build()

        post(ID_DEBTS, notification)
    }

    override fun showAutoCompleted(lessons: Int, billed: Money, day: LocalDate, currency: Currency) {
        if (!mayPost()) return

        val body = bulletJoin(
            words.resources.getQuantityString(R.plurals.lessons_count, lessons, lessons),
            billed.money(currency),
        )
        val notification = builder(CHANNEL_UPKEEP, R.string.upkeep_completed_title)
            .setContentText(body)
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

    private fun builder(channel: String, titleRes: Int): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(words.getString(titleRes))
            .setAutoCancel(true)

    private fun lessonList(posting: TrayContent.AgendaPosting): NotificationCompat.InboxStyle {
        val style = NotificationCompat.InboxStyle()
        val hour12 = hour12()
        posting.lines.forEach { entry ->
            style.addLine(bulletJoin(entry.start.formatTime(hour12 = hour12), entry.subject, entry.who))
        }
        if (posting.more > 0) {
            style.setSummaryText(words.getString(R.string.agenda_more, posting.more))
        }
        return style
    }

    private fun post(id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission was revoked — nothing to show.
        }
    }

    private fun hour12(): Boolean = !DateFormat.is24HourFormat(context)

    private fun openApp(requestCode: Int, action: String, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setAction(action)
            .apply(extras)
        return PendingIntent.getActivity(context, requestCode, intent, FLAGS)
    }

    private fun openDay(requestCode: Int, day: LocalDate): PendingIntent =
        openApp(requestCode, TrayNavigation.ACTION_OPEN_DAY) {
            putExtra(TrayNavigation.EXTRA_DAY, day.toEpochDays())
        }

    private fun completeAction(classId: Long): NotificationCompat.Action {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(ReminderActionReceiver.ACTION_COMPLETE)
            .putExtra(TrayNavigation.EXTRA_CLASS_ID, classId)
        return action(
            R.string.class_status_completed,
            PendingIntent.getBroadcast(context, classId.toInt(), intent, FLAGS),
        )
    }

    private fun rescheduleAction(classId: Long): NotificationCompat.Action =
        action(
            R.string.class_move,
            openApp(classId.toInt(), TrayNavigation.ACTION_OPEN_LESSON) {
                putExtra(TrayNavigation.EXTRA_CLASS_ID, classId)
            },
        )

    private fun action(labelRes: Int, intent: PendingIntent): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, words.getString(labelRes), intent).build()

    companion object {
        const val CHANNEL_REMINDERS = "class_reminders"
        const val CHANNEL_AGENDA = "daily_agenda"
        const val CHANNEL_UNFINISHED = "unfinished_digest"
        const val CHANNEL_DEBTS = "debt_digest"
        const val CHANNEL_UPKEEP = "upkeep_report"

        private const val GROUP_REMINDERS = "com.seblful.klas.REMINDERS"

        private const val ID_REMINDERS_SUMMARY = -1
        private const val ID_AGENDA = -2
        private const val ID_DEBTS = -3
        private const val ID_UPKEEP = -4
        private const val ID_UNFINISHED = -5

        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
