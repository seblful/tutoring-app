package com.seblful.klas.domain.model

import kotlinx.datetime.DayOfWeek

/**
 * Where the schedule's weeks start, chosen in Settings. [PHONE] follows the phone's own first-day-of-week
 * preference (`deviceFirstDayOfWeek`), which is Monday where the phone states none — so a tutor in the US
 * on an older Android picks [SUNDAY] here rather than having the app guess it from a language.
 *
 * Constant names are persisted in `settings.weekStart`; renaming one requires a migration.
 */
enum class WeekStartChoice(private val day: DayOfWeek?) {
    PHONE(null),
    MONDAY(DayOfWeek.MONDAY),
    SUNDAY(DayOfWeek.SUNDAY),
    SATURDAY(DayOfWeek.SATURDAY),
    ;

    /** The first day of the week this choice means on a phone whose own answer is [phone]. */
    fun resolve(phone: DayOfWeek): DayOfWeek = day ?: phone

    companion object {
        /** The stored choice, or [PHONE] when the value is missing or unrecognized. */
        fun parse(stored: String?): WeekStartChoice = entries.firstOrNull { it.name == stored } ?: PHONE
    }
}
