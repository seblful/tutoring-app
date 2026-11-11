package com.seblful.klas.calendar

import com.seblful.klas.di.Io
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.format.labelRes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import platform.EventKit.EKAuthorizationStatusFullAccess
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKSpan
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneWithName
import kotlin.time.DurationUnit
import kotlin.time.Instant

/** Full access to events: the mirror finds the event it wrote earlier as well as writing it. */
@ContributesBinding(AppScope::class)
@Inject
class IosCalendarPermission : CalendarPermission {
    override fun isGranted(): Boolean =
        EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent) == EKAuthorizationStatusFullAccess
}

/**
 * [CalendarSync] over EventKit, into the phone's default calendar — which iOS syncs onward to whatever
 * account the tutor made it.
 *
 * Two things Android's mirror does are left out, both because EventKit cannot do them: guests (an app may
 * read an event's attendees but not invite anyone) and the calendar's email reminder. An alarm on the event
 * would be a second alert beside the app's own reminder, so the event carries none.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class IosCalendarSync(
    private val permission: CalendarPermission,
    private val displayLocale: DisplayLocale,
    private val clock: WallClock,
    @param:Io private val io: CoroutineDispatcher,
) : CalendarSync {

    private val store by lazy { EKEventStore() }

    override suspend fun upsert(
        klass: TutoringClass,
        title: String,
        guest: Guest?,
        reminderLeadMinutes: Int,
        description: String,
    ): String? = withContext(io) {
        if (!permission.isGranted()) return@withContext null
        val event = klass.calendarEventId?.let(store::eventWithIdentifier)
            ?: store.defaultCalendarForNewEvents?.let { calendar ->
                EKEvent.eventWithEventStore(store).apply { setCalendar(calendar) }
            }
            ?: return@withContext null
        event.fill(klass, title, description)
        if (save(event)) event.eventIdentifier else null
    }

    override suspend fun refresh(klass: TutoringClass, title: String, description: String) = withContext(io) {
        if (!permission.isGranted()) return@withContext
        val event = klass.calendarEventId?.let(store::eventWithIdentifier) ?: return@withContext
        event.fill(klass, title, description)
        save(event)
        Unit
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun delete(eventId: String) = withContext(io) {
        if (!permission.isGranted()) return@withContext
        val event = store.eventWithIdentifier(eventId) ?: return@withContext
        store.removeEvent(event, span = EKSpan.EKSpanThisEvent, error = null)
        Unit
    }

    private fun EKEvent.fill(klass: TutoringClass, title: String, description: String) {
        setTitle(title)
        setStartDate(klass.start.toNSDate())
        setEndDate(klass.end.toNSDate())
        setTimeZone(NSTimeZone.timeZoneWithName(clock.zone.id))
        // Even an empty one, to clear homework that was removed or marked done.
        setNotes(description)
        // The one word written, in the tutor's chosen language — see Android's `DeviceCalendarSync`.
        setLocation(displayLocale.texts.string(klass.mode.labelRes()))
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun save(event: EKEvent): Boolean = store.saveEvent(event, span = EKSpan.EKSpanThisEvent, error = null)

    private fun Instant.toNSDate(): NSDate =
        NSDate.dateWithTimeIntervalSince1970((this - Instant.fromEpochSeconds(0)).toDouble(DurationUnit.SECONDS))
}
