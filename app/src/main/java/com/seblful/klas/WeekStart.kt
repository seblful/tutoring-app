package com.seblful.klas

import android.content.res.Resources
import java.time.DayOfWeek
import java.util.Locale

/**
 * Where the week starts, as the phone was told — not as this app assumed.
 *
 * Android 14 added it to regional preferences (Settings → System → Languages → Regional preferences →
 * First day of week). The platform hands it to apps as the `fw` Unicode extension on their locale —
 * `ru-BY-u-fw-sun` — and that is the **only** channel: the setting it is stored in is `@hide` and, since
 * Android S, reading it throws `SecurityException` for anything but a system app. A tutor who has moved
 * their week to Sunday there has said what they want their calendars to look like, and the schedule is a
 * calendar.
 *
 * **Read off the *device*, not the app.** [inRussian] replaces the configuration locale with a plain `ru`
 * so plurals resolve by Russian rules, and that replacement drops the extension keywords — the app's own
 * context can no longer see this preference. `Locale.getDefault` is set by the platform at process start
 * and `Resources.getSystem()` is untouched by us, so both are asked and the first answer wins.
 *
 * **Only an explicit preference moves it; otherwise Monday.** The locale's own convention (`WeekFields.of`)
 * is deliberately not consulted: it would answer "Sunday" for a phone whose *language* is `en-US` while the
 * app is Russian and the tutor is in Minsk — inferring a week from a language, which is the same mistake as
 * inferring a clock from one. Monday is the `ru`/`be` convention and stays the answer until someone says
 * otherwise. (`WeekFields` would not read the keyword here anyway — `WeekStartKeywordTest` pins that, and
 * what follows from it for Material's date picker.)
 */
fun deviceFirstDayOfWeek(): DayOfWeek =
    deviceLocales().firstNotNullOfOrNull { firstDayOfWeekOrNull(it) } ?: DayOfWeek.MONDAY

/**
 * The first day of the week [locale] states outright, or null when it states none.
 *
 * The `fw` values are CLDR's three-letter day abbreviations, which are not the day names `DayOfWeek`
 * parses, hence the map. Pure, so the parsing is tested without a device — the device half is only *which*
 * locales to ask. Junk is a missing answer rather than a crash: this reads whatever a phone holds.
 */
fun firstDayOfWeekOrNull(locale: Locale): DayOfWeek? =
    when (locale.getUnicodeLocaleType(FIRST_DAY_OF_WEEK)?.lowercase(Locale.ROOT)) {
        "mon" -> DayOfWeek.MONDAY
        "tue" -> DayOfWeek.TUESDAY
        "wed" -> DayOfWeek.WEDNESDAY
        "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY
        "sat" -> DayOfWeek.SATURDAY
        "sun" -> DayOfWeek.SUNDAY
        else -> null
    }

/**
 * The locales worth asking: the JVM default, the default for *formats* — the category the platform
 * documents these preferences under — and then the device configuration's list, most-preferred first.
 */
private fun deviceLocales(): List<Locale> = buildList {
    add(Locale.getDefault(Locale.Category.FORMAT))
    add(Locale.getDefault())
    val system = Resources.getSystem().configuration.locales
    for (index in 0 until system.size()) add(system[index])
}

private const val FIRST_DAY_OF_WEEK = "fw"
