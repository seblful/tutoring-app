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
 * clock is this phone on". There are two moments: the mark a drag crosses ([rememberArmedFeedback]) and the
 * action landing ([rememberConfirmFeedback]).
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
    fun `both buzzes exist and are used`() {
        val haptics = ProjectFiles.sourceFile("ui/common/Haptics.kt").readText()
        val elsewhere = ProjectFiles.sources().filter { it.name != "Haptics.kt" }.map { it.readText() }

        assertTrue("`Haptics.kt` performs no haptic feedback", "performHapticFeedback" in haptics)
        assertTrue(
            "Nothing asks for the confirm buzz — the swipe used to, on the lift",
            elsewhere.any { "rememberConfirmFeedback()" in it },
        )
        assertTrue(
            "Nothing asks for the armed tick — the swipe used to, at its mark",
            elsewhere.any { "rememberArmedFeedback()" in it },
        )
    }

    private companion object {
        /** Vibrating the phone without asking it: the API, its effects, and the permission they need. */
        val RawVibration = listOf("android.os.Vibrator", "VibrationEffect", "HapticFeedbackConstants")
    }
}
