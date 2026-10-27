package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A row where one label does not fit its share at full size — «Saturday» in a quarter of a phone — shrinks
 * every label with it, rather than leaving the others larger beside it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A phone's density: at 1 px per dp a label is 14 px tall, and hinting skews its width.
@Config(qualifiers = "w700dp-h800dp-xxhdpi")
class SegmentRowSizeTest {
    @get:Rule
    val compose = createComposeRule()

    private val days = listOf("System", "Monday", "Sunday", "Saturday")

    private fun drawnWidths(): List<Float> = days.map { day ->
        compose.onNodeWithText(day, useUnmergedTree = true).fetchSemanticsNode().size.width.toFloat()
    }

    @Test
    fun `in a row too narrow for the longest day, every day takes its size`() {
        var rowWidth by mutableStateOf(600.dp)
        compose.setContent {
            KlasTheme {
                SegmentedChoice(days, selected = days[0], onSelect = {}, modifier = Modifier.width(rowWidth)) { it }
            }
        }
        val wide = drawnWidths()
        rowWidth = 300.dp
        compose.waitForIdle()
        val narrow = drawnWidths()

        // At one size every label is drawn the same fraction narrower; a lone shrink would leave «Saturday»
        // the only one narrower.
        val ratios = wide.zip(narrow) { full, shrunk -> shrunk / full }
        assertTrue("the narrow row shrinks its labels: $ratios", ratios.first() < 0.97f)
        ratios.forEach { assertEquals("one size for the row: $ratios", ratios.first(), it, 0.03f) }
    }
}
