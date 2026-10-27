package com.seblful.klas.ui.designsystem.component

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HeaderedCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a bare count sits beside the title, as a profile's «Lessons 36» does, and a summary under it`() {
        compose.setContent {
            KlasTheme {
                HeaderedCard(title = "Homework", count = 3) {}
                HeaderedCard(title = "Today's lessons", summary = "1 lesson · 1.5 h") {}
            }
        }

        val title = compose.onNodeWithText("Homework").getUnclippedBoundsInRoot()
        val count = compose.onNodeWithText("3").getUnclippedBoundsInRoot()
        assertTrue("the count follows the title on its line", count.left >= title.right && count.top < title.bottom)

        val heading = compose.onNodeWithText("Today's lessons").getUnclippedBoundsInRoot()
        val summary = compose.onNodeWithText("1 lesson · 1.5 h").getUnclippedBoundsInRoot()
        assertTrue("a worded summary goes under the title", summary.top >= heading.bottom)
    }
}
