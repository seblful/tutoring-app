package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SwipeRevealTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the panel behind a row is drawn only while the row is dragged off its rest`() {
        var drawn = false
        compose.setContent {
            KlasTheme {
                SwipeToConfirmBox(
                    onConfirm = {},
                    reveal = { drawn = true },
                    modifier = Modifier.testTag("row").fillMaxWidth().height(72.dp),
                ) { Text("Законы Ньютона: задачи 5.12–5.20") }
            }
        }
        compose.waitForIdle()
        assertFalse("a row at rest, whose background may not reach its edges, shows a panel behind it", drawn)

        compose.onNodeWithTag("row").performTouchInput {
            down(centerLeft + Offset(10f, 0f))
            moveBy(Offset(viewConfiguration.touchSlop + 40f, 0f))
        }
        compose.waitForIdle()
        assertTrue("the drag uncovers the panel", drawn)
    }

    @Test
    fun `a peek shows the panel and comes back - without confirming anything`() {
        var drawn = false
        var confirmed = false
        compose.setContent {
            KlasTheme {
                SwipeToConfirmBox(
                    onConfirm = { confirmed = true },
                    reveal = { drawn = true },
                    modifier = Modifier.testTag("row").fillMaxWidth().height(72.dp),
                    peek = true,
                ) { Text("Алгебра") }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue("on its way out the row uncovers the panel", drawn)

        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        assertFalse("a peek stops short of the mark, and is never a swipe", confirmed)
    }
}
