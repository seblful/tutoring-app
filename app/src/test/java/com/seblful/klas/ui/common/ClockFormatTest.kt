package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.testutil.SourceText.argumentLists
import com.seblful.klas.ui.common.state.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **12- or 24-hour is the phone's answer, everywhere the tutor reads a time off this phone** — the clock a
 * lesson is set on, the text it is listed as, and the reminder that announces it.
 *
 * Three ways the app had of deciding it for itself:
 *
 * - [TimePickerModal] passed `is24Hour = true` to `rememberTimePickerState`, which follows the system
 *   setting when left alone — so a phone set to a 12-hour clock was handed a 24-hour dial to set a
 *   lesson's time on.
 * - A screen calling `formatTime()`/`formatDateTime()` directly got `format/Formatters`' 24-hour face,
 *   which was how a lesson could be *picked* at 9:00 PM and *listed* as 21:00. The UI goes through
 *   [timeText]/[dateTimeText] instead, which ask the phone.
 * - The reminder for that lesson did the same, because the first fix drew the line at "screens" — and a
 *   notification is not a screen but is read on the phone all the same.
 *
 * `hour12` has no default now, so nothing can pick a face by saying nothing; these tests cover what a
 * compiler cannot, which is *which* face each surface asks for. Only text read away from this phone — a
 * report, an export, a backup — pins `HH:mm`.
 *
 * None of the three looks wrong on a 24-hour phone, which is the one this was written on.
 */
class ClockFormatTest {

    private fun uiSources() =
        ProjectFiles.sources().filter { "ui" in it.path.split(File.separator) }

    @Test
    fun `no clock is pinned to 12 or 24 hours`() {
        val offenders = ProjectFiles.sources()
            .filter { file ->
                argumentLists(file.readText(), "TimePickerState(").any { "is24Hour" in it }
            }
            .map { it.name }

        assertEquals(
            "`is24Hour` is passed to a time picker state, overriding the phone's own clock format. " +
                "Leave it out — the default follows the system setting: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `no screen formats a time itself`() {
        val offenders = uiSources()
            .filter { it.name != "TimeText.kt" }
            .filter { file -> Formatters.any { it in file.readText() } }
            .map { it.name }

        assertEquals(
            "A screen reaches past `timeText()`/`dateTimeText()` to the formatter, so its text can " +
                "disagree with the picker that set it: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * The one surface outside `ui/` that a tutor reads off this phone. It cannot use [timeText] — there is
     * no composition around a posted notification — so it asks `DateFormat` the same question directly.
     */
    @Test
    fun `the reminder asks the phone which clock it is on`() {
        val notifier = ProjectFiles.sourceFile("notifications/AndroidNotifier.kt").readText()

        assertTrue(
            "The reminder prints no time any more — has this moved somewhere that has to be checked instead?",
            Formatters.any { it in notifier },
        )
        assertTrue(
            "`AndroidNotifier` prints a lesson's time without asking `DateFormat.is24HourFormat`, so a " +
                "phone on a 12-hour clock is reminded of «21:00» about a lesson it booked for 9:00 PM.",
            "is24HourFormat" in notifier,
        )
    }

    /** So the two scans above cannot pass by there being nothing left to get it wrong. */
    @Test
    fun `the pickers and the times are there to be scanned`() {
        val pickers = ProjectFiles.sources()
            .sumOf { argumentLists(it.readText(), "TimePickerState(").size }
        val times = uiSources().count { file ->
            listOf(".timeText()", ".dateTimeText()").any { it in file.readText() }
        }

        assertTrue("Found no `TimePickerState(` argument lists — has the scan stopped matching?", pickers >= 1)
        assertTrue("Found no screen printing a time — has the scan stopped matching?", times >= 3)
    }

    private companion object {
        /** The formatter calls that choose a clock face, as any caller has to spell them. */
        val Formatters = listOf("formatTime(", "formatDateTime(")
    }
}
