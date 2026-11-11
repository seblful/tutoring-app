package com.seblful.klas.notifications

import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.DigestScheduler
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.domain.reminders.Reminders
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.maintenance.Maintenance
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Instant

/*
 * iOS notifications. Android posts each one when an alarm or a worker fires and decides then what it says;
 * an iOS app is not woken to do that, so it decides ahead: the same shared decisions ([DueReminder],
 * [DueDigest]) run when the plans are made, *as of* the moment each will show, and [IosNotifier] hands
 * the result to the system with that moment as its trigger. A plan made again (after any change, and each
 * time the app comes to the front) replaces what was pending, so what shows is at most as old as the last
 * change.
 */

/** The system's answer, read when the app comes to the front; iOS gives it only asynchronously. */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosNotificationPermission : NotificationPermission {

    @Volatile
    private var status = UNAuthorizationStatusNotDetermined

    init {
        refresh()
    }

    override fun isGranted(): Boolean = status == UNAuthorizationStatusAuthorized ||
        status == UNAuthorizationStatusProvisional ||
        status == UNAuthorizationStatusEphemeral

    /** iOS asks once; after that only the Settings app changes the answer. */
    override val canAsk: Boolean get() = status == UNAuthorizationStatusNotDetermined

    fun refresh() {
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            settings?.let { status = it.authorizationStatus }
        }
    }
}

/** iOS needs no permission to show a notification at the minute it was set for. */
@ContributesBinding(AppScope::class)
@Inject
class IosExactAlarmPermission : ExactAlarmPermission {
    override fun isGranted(): Boolean = true
}

/**
 * [Notifier] over `UNUserNotificationCenter`. A notification is shown at [deliverAt] when a scheduler has
 * set it — see the file's note — and at once otherwise. A redacted lock screen shows the collapsed line
 * only; iOS's own "Show Previews" setting hides even that.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosNotifier(
    private val displayLocale: DisplayLocale,
    private val settings: SettingsRepository,
    private val clock: WallClock,
) : Notifier {

    /** When what is posted now is to show; set by [IosScheduler] around each decision it runs ahead. */
    @Volatile
    internal var deliverAt: Instant? = null

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    private fun text() = TrayText(displayLocale.texts, displayLocale.tag, phoneHour12())

    override fun ensureChannels() = Unit

    override fun showClassReminder(
        klass: TutoringClass,
        whoName: String,
        subjectName: String,
        homeworkToCheck: String?,
    ) {
        val entry = text().reminder(klass, whoName, subjectName, homeworkToCheck)
        post(reminderId(klass.id), entry, TrayNavigation.Destination.LessonOnSchedule(klass.id), thread = REMINDERS)
    }

    override fun cancelClassReminder(classId: Long) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(reminderId(classId)))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(reminderId(classId)))
    }

    override fun showAgenda(agenda: Digests.Agenda, currency: Currency) =
        post(AGENDA, text().agenda(agenda, currency), TrayNavigation.Destination.Day(agenda.day))

    override fun showUnfinished(unfinished: Digests.Unfinished) =
        post(UNFINISHED, text().unfinished(unfinished), TrayNavigation.Destination.Day(unfinished.day))

    override fun showDebts(debts: Digests.Debts, currency: Currency) =
        post(DEBTS, text().debts(debts, currency), TrayNavigation.Destination.Debts)

    override fun showAutoCompleted(lessons: Int, billed: Money, day: LocalDate, currency: Currency) =
        post(UPKEEP, text().autoCompleted(lessons, billed, currency), TrayNavigation.Destination.Day(day))

    private fun post(
        id: String,
        entry: TrayText.Entry,
        tap: TrayNavigation.Destination,
        thread: String? = null,
    ) {
        // No permission check: iOS drops a request the app may not show, and one set while the answer is
        // still being read at launch shows once it is yes.
        val details = settings.privacy.value?.lockScreenDetails ?: true
        val content = UNMutableNotificationContent().apply {
            setTitle(entry.title)
            setBody(
                when {
                    !details -> entry.text
                    entry.lines.isEmpty() -> entry.expanded
                    else -> (listOf(entry.text) + entry.lines + listOfNotNull(entry.more)).joinToString("\n")
                },
            )
            setSound(UNNotificationSound.defaultSound)
            thread?.let { setThreadIdentifier(it) }
            setUserInfo(mapOf<Any?, Any?>(KEY_LINK to TrayNavigation.linkFor(tap)))
        }
        val at = deliverAt?.takeIf { it > clock.now() }
        val trigger = at?.let {
            UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components(it), repeats = false)
        }
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(id, content, trigger), null)
    }

    private fun components(at: Instant): NSDateComponents {
        val local = at.toLocalDateTime(TimeZone.currentSystemDefault())
        return NSDateComponents().apply {
            year = local.year.toLong()
            month = local.month.ordinal.toLong() + 1
            day = local.day.toLong()
            hour = local.hour.toLong()
            minute = local.minute.toLong()
            second = local.second.toLong()
        }
    }

    internal companion object {
        const val REMINDERS = "reminders"
        const val AGENDA = "digest-agenda"
        const val UNFINISHED = "digest-unfinished"
        const val DEBTS = "digest-debts"
        const val UPKEEP = "upkeep"
        /** Where a tap on it goes, as its `klas://` link. */
        const val KEY_LINK = "link"

        const val REMINDER_PREFIX = "reminder-"

        fun reminderId(classId: Long) = "$REMINDER_PREFIX$classId"

        /** The phone's 12/24-hour switch, as its own clock reads it. */
        fun phoneHour12(): Boolean =
            NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale)?.contains('a') == true
    }
}

/**
 * [ReminderScheduler] and [DigestScheduler] for iOS: each plan's notification is decided now, as of when it
 * will show, and left with the system. Upkeep runs when the app comes to the front (`RootViewModel`), as
 * iOS gives an app no periodic background time it can count on.
 */
@ContributesBinding(AppScope::class, binding = binding<ReminderScheduler>())
@ContributesBinding(AppScope::class, binding = binding<DigestScheduler>())
@SingleIn(AppScope::class)
@Inject
class IosScheduler(
    private val notifier: IosNotifier,
    private val reminders: () -> DueReminder,
    private val digests: () -> DueDigest,
    private val maintenance: () -> Maintenance,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ReminderScheduler, DigestScheduler {

    /** One decision at a time, so each runs with its own [IosNotifier.deliverAt]. */
    private val deciding = Mutex()

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override fun refreshNow() {
        appScope.launch { maintenance().runUnwatched() }
    }

    override fun ensurePeriodicMaintenance() = Unit

    override suspend fun scheduleNextPass(at: Instant?) = Unit

    /**
     * Android's rule ([Reminders.Plan.due]): a due reminder is left as it is — set already, or shown — and
     * every future one is decided again. iOS keeps at most 64 pending requests, so the soonest
     * [MAX_REMINDERS] are set and the rest wait for a later plan.
     */
    override fun schedule(plans: List<Reminders.Plan>) {
        appScope.launch {
            deciding.withLock {
                val planned = plans.map { IosNotifier.reminderId(it.classId) }.toSet()
                pendingIds().filter { it.startsWith(IosNotifier.REMINDER_PREFIX) && it !in planned }
                    .takeIf { it.isNotEmpty() }
                    ?.let { center.removePendingNotificationRequestsWithIdentifiers(it) }
                plans.filterNot { it.due }.sortedBy { it.fireAt }.forEachIndexed { index, plan ->
                    val id = IosNotifier.reminderId(plan.classId)
                    center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
                    if (index < MAX_REMINDERS) deliveringAt(plan.fireAt) { reminders().post(plan.classId, now = it) }
                }
            }
        }
    }

    override suspend fun schedule(plan: Digests.Plan) = deciding.withLock {
        arrange(IosNotifier.AGENDA, plan.agendaAt) { digests().postAgenda(now = it) }
        arrange(IosNotifier.UNFINISHED, plan.unfinishedAt) { digests().postUnfinished(now = it) }
        arrange(IosNotifier.DEBTS, plan.debtsAt) { digests().postDebts() }
    }

    private suspend fun arrange(id: String, at: Instant?, post: suspend (Instant) -> Boolean) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
        if (at != null) deliveringAt(at) { post(at) }
    }

    private suspend fun deliveringAt(at: Instant, decide: suspend (Instant) -> Boolean) {
        notifier.deliverAt = at
        try {
            decide(at)
        } finally {
            notifier.deliverAt = null
        }
    }

    private suspend fun pendingIds(): List<String> = suspendCoroutine { done ->
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            done.resume(requests.orEmpty().filterIsInstance<UNNotificationRequest>().map { it.identifier })
        }
    }

    private companion object {
        /** iOS's 64, less the three digests and one to spare. */
        const val MAX_REMINDERS = 60
    }
}
