package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class ScreenHeaderTintTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val page = mutableIntStateOf(0)
    private var surface = Color.Unspecified
    private var scrolled = Color.Unspecified

    @Test
    fun theHeaderIsPageAtRestAndBarOnceTheListScrollsUnderIt() {
        setScreen()
        assertHeaderAndBar(surface, "at rest")

        composeRule.onNodeWithTag(LIST).performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        assertHeaderAndBar(scrolled, "with the list scrolled")
    }

    @Test
    fun aNewPageStartsUntinted() {
        setScreen()
        composeRule.onNodeWithTag(LIST).performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        composeRule.runOnIdle { page.intValue = 1 }
        composeRule.waitForIdle()

        assertHeaderAndBar(surface, "on a new page")
    }

    private fun setScreen() {
        composeRule.setContent {
            KlasTheme {
                surface = MaterialTheme.colorScheme.surface
                scrolled = MaterialTheme.colorScheme.surfaceContainer
                KlasScreen(title = "Люди", page = page.intValue) { modifier ->
                    Column(modifier) {
                        ScreenHeader(Modifier.height(HeaderHeight.dp).testTag(HEADER)) {}
                        key(page.intValue) {
                            LazyColumn(Modifier.fillMaxWidth().testTag(LIST)) {
                                items(Rows) { Text("Row $it", Modifier.height(RowHeight.dp)) }
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertHeaderAndBar(expected: Color, where: String) {
        val header = composeRule.onNodeWithTag(HEADER).captureToImage().toPixelMap()
        val headerColor = header[header.width / 2, header.height / 2]
        val root = composeRule.onRoot().captureToImage().toPixelMap()
        val barColor = root[root.width - Inset, Inset]

        assertTrue("Header is $headerColor, expected $expected $where", headerColor.near(expected))
        assertTrue("Bar is $barColor, expected $expected $where", barColor.near(expected))
    }

    private fun Color.near(other: Color): Boolean =
        abs(red - other.red) < Tolerance && abs(green - other.green) < Tolerance && abs(blue - other.blue) < Tolerance

    private companion object {
        const val HEADER = "header"
        const val LIST = "list"
        const val HeaderHeight = 48
        const val RowHeight = 56
        const val Rows = 60

        /** Pixels in from the bar's corner — past any anti-aliasing, short of the title. */
        const val Inset = 4

        /** Per channel: one 8-bit step either way survives the capture; two tones of the ladder do not. */
        const val Tolerance = 2f / 255f
    }
}
