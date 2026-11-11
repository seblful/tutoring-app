package com.seblful.klas.notifications

import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.di.ApplicationScope
import com.seblful.klas.domain.reminders.DigestScheduler
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.domain.reminders.Reminders
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.maintenance.Maintenance
import com.seblful.klas.ui.common.phoneHour12
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionAuthenticationRequired
import platform.UserNotifications.UNNotificationActionOptionForeground
import platform.UserNotifications.UNNotificationCategory
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
 * [DueDigest]) run when the plans are made, *as of* the moment each will show, posting to
 * [IosNotifier.at] that moment, which hands the notice to the system with it as the trigger. A plan made
 * again (after any change, and each time the app comes to the front) replaces what was pending, so what
 * shows is at most as old as the last change.
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
 * [Notifier] over `UNUserNotificationCenter`: shows each [TrayNotice] at once, or at the moment given to [at].
 * A redacted lock screen shows the collapsed line only; iOS's own "Show Previews" setting hides even that.
 * A notice's buttons are its category's actions, answered in `KlasIos`.
 */
@ContributesBinding(AppScope::class, binding = binding<Notifier>())
@SingleIn(AppScope::class)
@Inject
class IosNotifier(
    private val displayLocale: DisplayLocale,
    private val settings: SettingsRepository,
    private val clock: WallClock,
) : TrayNotifier(displayLocale) {

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    /** The button titles last registered with the system, which keeps them per category, not per notification. */
    private var registered: List<String>? = null

    override fun hour12(): Boolean = phoneHour12()

    override fun ensureChannels() = Unit

    override fun post(notice: TrayNotice) = deliver(notice, at = null)

    override fun cancelClassReminder(classId: Long) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(reminderId(classId)))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(reminderId(classId)))
    }

    /** This notifier showing what it is given at [at] rather than now — for the decisions made ahead. */
    fun at(at: Instant): Notifier = object : TrayNotifier(displayLocale) {
        override fun hour12(): Boolean = phoneHour12()

        override fun ensureChannels() = Unit

        override fun post(notice: TrayNotice) = deliver(notice, at)

        override fun cancelClassReminder(classId: Long) = this@IosNotifier.cancelClassReminder(classId)
    }

    private fun deliver(notice: TrayNotice, at: Instant?) {
        // No permission check: iOS drops a request the app may not show, and one set while the answer is
        // still being read at launch shows once it is yes.
        val details = settings.privacy.value?.lockScreenDetails ?: true
        val entry = notice.entry
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
            setThreadIdentifier(notice.kind.name.lowercase())
            if (notice.buttons.isNotEmpty()) setCategoryIdentifier(category(notice.buttons))
            setUserInfo(
                buildMap<Any?, Any?> {
                    put(KEY_LINK, TrayNavigation.linkFor(notice.tap))
                    notice.buttons.forEachIndexed { index, button ->
                        when (button) {
                            is TrayButton.Complete -> put(KEY_CLASS_ID, button.classId)
                            is TrayButton.Open -> put(openAction(index), TrayNavigation.linkFor(button.destination))
                        }
                    }
                },
            )
        }
        val trigger = at?.takeIf { it > clock.now() }?.let {
            UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components(it), repeats = false)
        }
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(id(notice), content, trigger), null)
    }

    /**
     * The category holding [buttons] as actions, registered again when their words change. Only reminders have
     * buttons, so one category serves.
     */
    private fun category(buttons: List<TrayButton>): String {
        val texts = displayLocale.texts
        val titles = buttons.map { texts.string(it.label) }
        if (titles != registered) {
            val actions = buttons.mapIndexed { index, button ->
                when (button) {
                    // It bills, so a locked phone asks for the unlock first, as Android's does.
                    is TrayButton.Complete -> UNNotificationAction.actionWithIdentifier(
                        ACTION_COMPLETE,
                        titles[index],
                        UNNotificationActionOptionAuthenticationRequired,
                    )
                    is TrayButton.Open -> UNNotificationAction.actionWithIdentifier(
                        openAction(index),
                        titles[index],
                        UNNotificationActionOptionForeground,
                    )
                }
            }
            center.setNotificationCategories(
                setOf(UNNotificationCategory.categoryWithIdentifier(CATEGORY, actions, emptyList<Any>(), 0u)),
            )
            registered = titles
        }
        return CATEGORY
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
        /** Where a tap on it goes, as its `klas://` link. */
        const val KEY_LINK = "link"

        /** The lesson its «Проведено» marks. */
        const val KEY_CLASS_ID = "classId"

        /** The action marking the lesson taught. An Open button's action is [openAction], keyed to its link. */
        const val ACTION_COMPLETE = "complete"

        private const val CATEGORY = "reminder"
        private const val REMINDER_PREFIX = "reminder-"

        fun openAction(index: Int) = "open.$index"

        fun reminderId(classId: Long) = "$REMINDER_PREFIX$classId"

        fun isReminder(id: String) = id.startsWith(REMINDER_PREFIX)

        /** One request per lesson's reminder, and one per digest kind, which a newer one replaces. */
        fun id(notice: TrayNotice): String = notice.classId?.let(::reminderId) ?: idFor(notice.kind)

        /** The ids iOS builds before this one used, kept so a plan replaces what they left pending. */
        fun idFor(kind: TrayKind): String = when (kind) {
            TrayKind.UPKEEP -> "upkeep"
            else -> "digest-${kind.name.lowercase()}"
        }
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

    /** One plan at a time, so two plans' removals and additions do not interleave. */
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
                pendingIds().filter { IosNotifier.isReminder(it) && it !in planned }
                    .takeIf { it.isNotEmpty() }
                    ?.let { center.removePendingNotificationRequestsWithIdentifiers(it) }
                plans.filterNot { it.due }.sortedBy { it.fireAt }.forEachIndexed { index, plan ->
                    val id = IosNotifier.reminderId(plan.classId)
                    center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
                    if (index < MAX_REMINDERS) reminders().post(plan.classId, plan.fireAt, notifier.at(plan.fireAt))
                }
            }
        }
    }

    override suspend fun schedule(plan: Digests.Plan) = deciding.withLock {
        arrange(TrayKind.AGENDA, plan.agendaAt) { digests().postAgenda(now = it, to = notifier.at(it)) }
        arrange(TrayKind.UNFINISHED, plan.unfinishedAt) { digests().postUnfinished(now = it, to = notifier.at(it)) }
        arrange(TrayKind.DEBTS, plan.debtsAt) { digests().postDebts(to = notifier.at(it)) }
    }

    private suspend fun arrange(kind: TrayKind, at: Instant?, post: suspend (Instant) -> Boolean) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(IosNotifier.idFor(kind)))
        if (at != null) post(at)
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
