package com.seblful.klas

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * **Why the app reads the phone's first-day-of-week preference itself** ([firstDayOfWeekOrNull]) instead of
 * handing the locale to `WeekFields` and letting `java.time` answer.
 *
 * Android 14 surfaces the preference as the `fw` Unicode extension on the device locale. This device — with
 * `java.time` arriving through core-library desugaring — **ignores that keyword**: `ru-BY-u-fw-sun` gets the
 * same Monday as plain `ru-BY`, while an `en-US` locale does get Sunday, so it is the keyword that is
 * unread and not the locale data that is missing. Asserted on a device because that is where the answer
 * lives; reasoning about it from the desugar library's source is how you get this wrong.
 *
 * Two things follow:
 * - `WeekStart.kt` maps `fw` itself. It would otherwise be a preference the app could read on some Androids
 *   and not others, which is the worst of both.
 * - **Material's `DatePicker` cannot be made to follow it.** It takes no `firstDayOfWeek` and reads
 *   `WeekFields.of(composition locale)`, so pinning the keyword onto the locale in [inRussian] would change
 *   nothing: the picker's month keeps starting on Monday even where the schedule starts on Sunday. A
 *   date-picker dialog is a different surface from the schedule and both are internally consistent, so this
 *   is left alone rather than worked around.
 *
 * If this test ever fails, the platform has started honouring `fw` — at which point the keyword can be
 * passed through [inRussian] to make both calendars agree, and this file has done its job.
 */
@HiltAndroidTest
class WeekStartKeywordTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    private fun weekStartOf(tag: String): DayOfWeek =
        WeekFields.of(Locale.forLanguageTag(tag)).firstDayOfWeek

    @Test
    fun weekFieldsIgnoresTheFirstDayOfWeekKeyword() {
        assertEquals(
            "`fw` moved the week — this platform used to ignore it, see the KDoc",
            weekStartOf("ru-BY"),
            weekStartOf("ru-BY-u-fw-sun"),
        )
        assertEquals(DayOfWeek.MONDAY, weekStartOf("ru-BY-u-fw-sun"))
    }

    /** So the above is the keyword being unread, not `WeekFields` answering Monday to everything. */
    @Test
    fun weekFieldsStillReadsTheRegionsOwnConvention() {
        assertEquals(DayOfWeek.MONDAY, weekStartOf("ru-BY"))
        assertEquals(DayOfWeek.SUNDAY, weekStartOf("en-US"))
    }
}
