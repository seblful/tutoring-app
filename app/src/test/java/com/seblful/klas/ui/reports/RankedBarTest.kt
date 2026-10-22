package com.seblful.klas.ui.reports

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/** A report bar given another period's share: it grows to it rather than jumping. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RankedBarTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var fraction by mutableFloatStateOf(0.2f)
    private var fill = Color.Unspecified

    @Test
    fun `a new share is grown to over several frames`() {
        compose.setContent {
            MaterialTheme {
                fill = MaterialTheme.colorScheme.primary
                Surface {
                    Box(Modifier.width(200.dp).testTag(TAG)) { RankedBar(fraction, Modifier.fillMaxWidth()) }
                }
            }
        }
        compose.waitForIdle()
        assertNotEquals("track, past a fifth", fill.toArgb(), pixelAt(0.75f))

        compose.mainClock.autoAdvance = false
        fraction = 0.9f
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(64)
        assertNotEquals("still on its way", fill.toArgb(), pixelAt(0.75f))
        assertEquals("already growing", fill.toArgb(), pixelAt(0.22f))

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(fill.toArgb(), pixelAt(0.75f))
    }

    private fun pixelAt(along: Float): Int {
        val bar = compose.onNodeWithTag(TAG).fetchSemanticsNode().boundsInWindow
        return compose.activity.window.decorView.drawToBitmap()
            .getPixel((bar.left + bar.width * along).roundToInt(), bar.center.y.roundToInt())
    }

    private companion object {
        const val TAG = "bar"
    }
}
