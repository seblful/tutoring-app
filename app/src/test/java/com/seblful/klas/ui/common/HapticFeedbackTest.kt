package com.seblful.klas.ui.common

import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **A buzz is the phone's to give.** `LocalHapticFeedback` ends at `View.performHapticFeedback`, which
 * honours the system's touch-feedback setting and the device's haptic strength; `Vibrator` and
 * `VibrationEffect` do not — they vibrate a phone whose owner turned vibration off, for a duration this
 * app would have had to invent. The `VIBRATE` permission is the tell that it happened, so the manifest is
 * checked too.
 *
 * And it is performed in one file (`ui/common/Haptics.kt`) rather than at each gesture, so *which* effect a
 * moment gets is a decision with one home — the same argument as [timeText] having the only copy of "which
 * clock is this phone on". There are five moments, and the list is deliberately short: the mark a drag
 * crosses ([rememberArmedFeedback]), an action landing ([rememberConfirmFeedback]), a refusal
 * ([rememberRejectFeedback]), a stored fact toggling ([rememberToggleFeedback]) and moving between the
 * segments of a fixed choice ([rememberSegmentTickFeedback]).
 *
 * **What is not on the list is the point of the list.** An ordinary button, a list row, a tab, a navigation —
 * none of them buzz. The platform reserves haptics for state changes, gesture thresholds and confirmations
 * and leaves a plain tap to its ripple; a phone that buzzes at everything is one where no buzz means
 * anything.
 */
class HapticFeedbackTest {

    /**
     * Read off the *code* rather than the whole file, because the file that documents this rule names the
     * APIs it forbids — and a scan that cannot tell a call from a KDoc link would be answered by deleting
     * the explanation.
     */
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

    /** So the scans above cannot pass by the app having stopped buzzing at all. */
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

    /**
     * The **confirm and the refusal are a pair**, and the refusal is the one that was missing: a lesson
     * marked taught buzzed twice while an hour refused because it was already booked — the app's one hard
     * "no" — said nothing at all. If the app can say yes without words it has to be able to say no.
     */
    @Test
    fun `a refusal is felt wherever the schedule refuses an hour`() {
        val schedule = ProjectFiles.sourceFile("ui/schedule/ScheduleScreen.kt").readText()

        assertTrue(
            "The schedule draws the time-conflict refusal without feeling like one",
            "rememberRejectFeedback()" in schedule,
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
