package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.testutil.SourceText
import com.seblful.klas.ui.common.state.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HapticFeedbackTest {

    private fun code(file: File): String = file.readLines()
        .filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")
        }
        .joinToString("\n")

    @Test
    fun `nothing vibrates the phone directly`() {
        val offenders = ProjectFiles.sources()
            .filter { file -> RawVibration.any { it in code(file) } }
            .map { it.name }

        assertEquals(
            "A raw vibration ignores the system's touch-feedback setting. Go through " +
                "`LocalHapticFeedback` (see `ui/common/Haptics.kt`): $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the manifest asks for no vibration permission`() {
        val manifest = ProjectFiles.moduleFile("src/main/AndroidManifest.xml").readText()

        assertTrue(
            "`VIBRATE` is in the manifest, which no `LocalHapticFeedback` call needs — something is " +
                "driving the vibrator itself.",
            "VIBRATE" !in manifest,
        )
    }

    @Test
    fun `haptics are performed in one place`() {
        val offenders = ProjectFiles.sources()
            .filter { it.name != "Haptics.kt" }
            .filter { "performHapticFeedback" in code(it) }
            .map { it.name }

        assertEquals(
            "A screen picks a haptic effect itself; `ui/common/Haptics.kt` is where an action's effect " +
                "is chosen: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `every buzz exists and is used`() {
        val haptics = ProjectFiles.sourceFile("ui/common/Haptics.kt").readText()
        val elsewhere = ProjectFiles.sources().filter { it.name != "Haptics.kt" }.map { it.readText() }

        assertTrue("`Haptics.kt` performs no haptic feedback", "performHapticFeedback" in haptics)

        val unused = Moments.filterNot { (_, factory) -> elsewhere.any { "$factory()" in it } }
        assertEquals(
            "A named haptic moment nothing asks for. Either the moment it was written for lost its " +
                "buzz, or the function should go: $unused",
            emptyList<Pair<String, String>>(),
            unused,
        )
    }

    @Test
    fun `a refusal is felt wherever the schedule refuses an hour`() {
        val schedule = ProjectFiles.sourceFile("ui/schedule/ScheduleScreen.kt").readText()

        assertTrue(
            "The schedule draws the time-conflict refusal without feeling like one",
            "rememberRejectFeedback()" in schedule,
        )
    }

    @Test
    fun `a refusal is felt wherever the booking form refuses an hour`() {
        val form = SourceText.code(ProjectFiles.sourceFile("ui/schedule/booking/ClassEditScreen.kt").readText())
        val refusal = SourceText.argumentLists(form, "ShowMessagesFor(").joinToString()

        assertTrue(
            "The booking form shows «Это время уже занято» without feeling like a refusal",
            "refused =" in refusal && "SAVE_REFUSED_TIME_TAKEN" in refusal,
        )
    }

    private companion object {
        /** Vibrating the phone without asking it: the API, its effects, and the permission they need. */
        val RawVibration = listOf("android.os.Vibrator", "VibrationEffect", "HapticFeedbackConstants")

        /** Each named moment, and what it is for — so a message can say which one went unused. */
        val Moments = listOf(
            "the mark a drag crosses" to "rememberArmedFeedback",
            "an action landing" to "rememberConfirmFeedback",
            "a refusal" to "rememberRejectFeedback",
            "a stored fact toggling" to "rememberToggleFeedback",
            "moving between segments" to "rememberSegmentTickFeedback",
        )
    }
}
