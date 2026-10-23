package com.seblful.klas.ui.schedule

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.ui.theme.KlasTheme
import com.seblful.klas.ui.theme.klasSemantics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS")
class BatchActionsTest {
    @get:Rule(order = 0)
    val env = TestEnv()

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    @get:Rule(order = 2)
    val compose = createComposeRule()

    @get:Rule(order = 3)
    val language = AppLocaleRule.english()

    @Test
    fun `the bar's cancel is drawn red, and marking taught is not`() {
        var red = Color.Unspecified
        compose.setContent {
            KlasTheme {
                red = klasSemantics.destructive
                Row { BatchActions(LessonBatch(env.classRepo, mainRule.appScope), allCancelled = false) }
            }
        }

        assertTrue("cancel is not red", draws(red, "Cancel lessons"))
        assertFalse("marking taught is red", draws(red, "Mark taught"))
    }

    @Test
    fun `with every lesson picked cancelled, the bar offers to return them to the plan, and only that`() {
        compose.setContent {
            KlasTheme { Row { BatchActions(LessonBatch(env.classRepo, mainRule.appScope), allCancelled = true) } }
        }

        compose.onNodeWithContentDescription("Return to planned").assertExists()
        compose.onNodeWithContentDescription("Cancel lessons").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mark taught").assertDoesNotExist()
    }

    private fun draws(color: Color, description: String): Boolean {
        val pixels = compose.onNodeWithContentDescription(description).captureToImage().toPixelMap()
        return (0 until pixels.width).any { x -> (0 until pixels.height).any { y -> pixels[x, y].near(color) } }
    }

    private fun Color.near(other: Color) =
        abs(red - other.red) < CLOSE && abs(green - other.green) < CLOSE && abs(blue - other.blue) < CLOSE

    private companion object {
        const val CLOSE = 0.08f
    }
}
