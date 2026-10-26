package com.seblful.klas.data.backup

import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.domain.guide.PhoneSetup
import com.seblful.klas.domain.guide.PhoneStep
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.time.MINUTES_PER_DAY
import kotlinx.serialization.Serializable

/**
 * The settings that travel with the data — **v11 and later**. Two kinds, for two reasons:
 *
 * - What the rows mean: the currency every amount is captioned in, and what bookings and groups are pre-filled
 *   with and whether lessons are marked taught by themselves. Without them a restored «489» reads in another
 *   currency. A restore applies them.
 * - What the phone the file came from had on that no file can carry — reminders, a calendar mirror, a weekly
 *   copy — so the phone it lands on asks for exactly those again ([PhoneSetup]). A restore only reads them.
 *
 * The rest of the settings row stays on its phone: its look, its digests, its lock, its guide.
 */
@Serializable
data class BackupPreferences(
    val currency: String,
    val defaultLessonMinutes: Int,
    val defaultGroupPriceMinor: Long? = null,
    val autoCompleteEnabled: Boolean,
    val remindersOn: Boolean,
    val calendarSyncOn: Boolean,
    val weeklyCopyOn: Boolean,
)

internal fun AppSettings.toBackupPreferences() = BackupPreferences(
    currency = currency.name,
    defaultLessonMinutes = defaultLessonMinutes,
    defaultGroupPriceMinor = defaultGroupPrice?.minor,
    autoCompleteEnabled = autoCompleteEnabled,
    remindersOn = notificationsEnabled,
    calendarSyncOn = calendarSyncEnabled,
    weeklyCopyOn = autoBackupFolder != null,
)

/**
 * [this] with what [preferences] brings to the data. A currency this build does not know, or a length or rate no
 * build would write, leaves this phone's own rather than refusing the whole file over a caption.
 */
internal fun AppSettings.applying(preferences: BackupPreferences): AppSettings = copy(
    currency = Currency.forCode(preferences.currency) ?: currency,
    defaultLessonMinutes = preferences.defaultLessonMinutes.takeIf { it in 1..MINUTES_PER_DAY } ?: defaultLessonMinutes,
    defaultGroupPrice = when (val minor = preferences.defaultGroupPriceMinor) {
        null -> null
        in 1..Money.MAX.minor -> Money(minor)
        else -> defaultGroupPrice
    },
    autoCompleteEnabled = preferences.autoCompleteEnabled,
)

/** What the phone a file is restored on is to be given: what its own phone had on, or — not said — [PhoneSetup.UNKNOWN]. */
internal fun BackupPreferences?.phoneSetup(): Set<PhoneStep> = this?.let {
    buildSet {
        if (remindersOn) add(PhoneStep.REMINDERS)
        if (calendarSyncOn) add(PhoneStep.CALENDAR)
        if (weeklyCopyOn) add(PhoneStep.WEEKLY_COPY)
    }
} ?: PhoneSetup.UNKNOWN
