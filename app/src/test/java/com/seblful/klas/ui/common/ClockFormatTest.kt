package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.testutil.SourceText.argumentLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **12- or 24-hour is the phone's answer, in both directions: the clock a time is set on and the text it
 * is read back as.**
 *
 * Two ways the app had of deciding it for itself:
 *
 * - [TimePickerModal] passed `is24Hour = true` to `rememberTimePickerState`, which follows the system
 *   setting when left alone — so a phone set to a 12-hour clock was handed a 24-hour dial to set a
 *   lesson's time on.
 * - A screen calling `formatTime()`/`formatDateTime()` directly gets `format/Formatters`' 24-hour default,
 *   which was how a lesson could be *picked* at 9:00 PM and *listed* as 21:00. The UI goes through
 *   [timeText]/[dateTimeText] instead, which ask the phone. Everything that is not a screen — a
 *   notification, a report, an export — keeps calling the formatter directly, on purpose.
 *
 * Neither looks wrong on a 24-hour phone, which is the one this was written on.
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
            .filter { file -> RawFormatters.any { it in file.readText() } }
            .map { it.name }

        assertEquals(
            "A screen calls the 24-hour formatter directly, so its text can disagree with the picker " +
                "that set it. Use `timeText()`/`dateTimeText()`: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    /** So neither test above can pass by there being nothing left to get it wrong. */
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
        /** The 24-hour faces, as a screen would spell them: no argument, so no phone was asked. */
        val RawFormatters = listOf(".formatTime()", ".formatDateTime()")
    }
}
