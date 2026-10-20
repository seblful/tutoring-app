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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class AppTypefaceTest {

    @get:Rule
    val composeRule = createComposeRule()

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

    private fun ComposeContentTestRule.setSampleContent() {
        setContent {
            KlasTheme {
                Column(Modifier.requiredWidth(SampleRoomWidth)) {
                    Weights.forEach { (tag, weight) -> Sample(tag, weight) }
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

    @Composable
    private fun Sample(tag: String, weight: FontWeight) {
        Text(
            SAMPLE,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = weight),
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
