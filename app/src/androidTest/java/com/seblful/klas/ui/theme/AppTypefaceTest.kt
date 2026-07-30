package com.seblful.klas.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seblful.klas.testutil.textLayout
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * **The bundled face is really the one being drawn.**
 *
 * The failure this exists for is silent by design. A `Font(R.font.golos_text_regular, …)` that cannot be
 * loaded — a file left out of a commit, a resource shrunk away by a release build, a rename that survives
 * because `R.font` is generated from whatever happens to be in the directory — does not throw. Compose
 * falls back to the platform face and the app keeps working, in Roboto, looking exactly like the app it
 * was before any of this was done. Every other test in the suite still passes: the tab labels still fit,
 * the stat tiles still shrink, every layout is still correct. Nothing else measures *which* face they are
 * correct in.
 *
 * So the assertion is comparative. The same Russian string is drawn at one size through the app's
 * typography and again forced onto [FontFamily.Default], and the two must come out **different widths**.
 * Two different text faces agreeing to the pixel across forty Cyrillic glyphs does not happen; a fallback
 * makes them identical by definition. Nothing here fingerprints Golos Text's particular metrics — the
 * property worth keeping is that a real bundled face is in use, not that it is still this one.
 *
 * The sample is a pangram for the same reason it is long: the difference between two text faces is a
 * fraction of a pixel per glyph, and forty of them add up to something a device can measure.
 */
@HiltAndroidTest
class AppTypefaceTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    /**
     * The app's own text is not the platform's text.
     *
     * `bodyLarge` because it is the most-drawn style in the app, and because the body face is the one a
     * fallback hides in best — a wrong headline gets noticed, a wrong list row does not.
     */
    @Test
    fun theAppIsNotDrawnInThePlatformDefaultFace() {
        composeRule.setSampleContent()

        val app = composeRule.onNodeWithTag(REGULAR).textLayout().size.width
        val platform = composeRule.onNodeWithTag(PLATFORM).textLayout().size.width

        assertNotEquals(
            "The sample came out ${app}px wide in the app's typography and ${platform}px in " +
                "FontFamily.Default. Identical widths mean the bundled face did not load and Compose " +
                "fell back to the platform's — check that res/font/golos_text_*.ttf are present and " +
                "that Type.kt still names them.",
            platform,
            app,
        )
    }

    /**
     * All four weights are four different files.
     *
     * The other way this ships wrong, and the reason the weights are static instances at all: upstream
     * Golos Text is a single variable font, Android honours a `wght` axis only from API 26, and `minSdk`
     * here is 24 — so a variable font would have drawn every weight below Bold as Regular on 24 and 25.
     * Four distinct widths prove four distinct faces loaded rather than one file named four times.
     *
     * It cannot fail *for that reason* on a modern test device, where a variable font would have worked
     * anyway. It can fail for the ordinary ones: two `Font(…)` entries resolving to the same resource, or
     * a weight the family does not carry being quietly matched to its nearest neighbour.
     */
    @Test
    fun theFourWeightsAreFourDistinctFaces() {
        composeRule.setSampleContent()

        val widths = Weights.associate { (tag, _) ->
            tag to composeRule.onNodeWithTag(tag).textLayout().size.width
        }

        assertEquals(
            "The sample should be a different width in each of the four weights; got $widths. Two " +
                "weights sharing a width means Type.kt's FontFamily has two entries resolving to the " +
                "same file, or asks for a weight res/font does not carry.",
            Weights.size,
            widths.values.toSet().size,
        )
    }

    /**
     * The samples, in a column **wider than any phone**.
     *
     * [requiredWidth] rather than whatever the device gives, because the measurement only means something
     * while the text is unconstrained: the sample is around 360dp at `bodyLarge`, so on a real screen both
     * renderings would be clipped to the same container width and come out equal — which is the exact
     * reading a fallback produces. Given room, each lays out at its own intrinsic width.
     */
    private fun ComposeContentTestRule.setSampleContent() {
        setContent {
            KlasTheme {
                Column(Modifier.requiredWidth(SampleRoomWidth)) {
                    Weights.forEach { (tag, weight) -> Sample(tag, weight) }
                    // The comparison: same string, same size, the platform's face.
                    Text(
                        SAMPLE,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Default),
                        maxLines = 1,
                        modifier = Modifier.testTag(PLATFORM),
                    )
                }
            }
        }
        waitForIdle()
    }

    /** The sample in one weight of the app's own family, tagged so it can be measured on its own. */
    @Composable
    private fun Sample(tag: String, weight: FontWeight) {
        Text(
            SAMPLE,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = weight),
            // One line, so a wrap can never be what is being compared. If the room above ever stopped
            // being enough, this clips and the tests fail — rather than quietly equalising.
            maxLines = 1,
            modifier = Modifier.testTag(tag),
        )
    }

    private companion object {
        /** A Cyrillic pangram: long enough that per-glyph metric differences accumulate. */
        const val SAMPLE = "Съешь же ещё этих мягких французских булок"

        const val PLATFORM = "platform"
        const val REGULAR = "w400"

        /** Room enough for the sample to lay out at its own width on any device. */
        val SampleRoomWidth = 900.dp

        /** Exactly the weights `Type.kt` names, which are the ones `res/font` has to carry. */
        val Weights = listOf(
            REGULAR to FontWeight.Normal,
            "w500" to FontWeight.Medium,
            "w600" to FontWeight.SemiBold,
            "w700" to FontWeight.Bold,
        )
    }
}
