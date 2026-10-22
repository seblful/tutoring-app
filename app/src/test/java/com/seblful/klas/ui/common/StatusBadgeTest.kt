package com.seblful.klas.ui.common

import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatusBadgeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the pill pads its label by the inline and tight spacing`() {
        compose.setContent { KlasTheme { StatusBadge("Проведено", Color.Green, Modifier.testTag(Badge)) } }

        val label = compose.onNodeWithText("Проведено").fetchSemanticsNode().size
        with(compose.density) {
            assertEquals(
                IntSize(label.width + 2 * Spacing.inline.roundToPx(), label.height + 2 * Spacing.tight.roundToPx()),
                compose.onNodeWithTag(Badge).fetchSemanticsNode().size,
            )
        }
    }

    @Test
    fun `a caller's style overrides the default`() {
        compose.setContent {
            KlasTheme {
                StatusBadge("Проведено", Color.Green, Modifier.testTag(Badge), style = Style { contentPadding(0.dp) })
            }
        }

        assertEquals(
            compose.onNodeWithText("Проведено").fetchSemanticsNode().size,
            compose.onNodeWithTag(Badge).fetchSemanticsNode().size,
        )
    }

    private companion object {
        const val Badge = "badge"
    }
}
