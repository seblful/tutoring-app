package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SurfaceRowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a list's ends round its surface and the rows between stay square`() {
        assertEquals(listOf(SlicePosition.ONLY), List(1) { SlicePosition.of(it, 1) })
        assertEquals(
            listOf(SlicePosition.FIRST, SlicePosition.MIDDLE, SlicePosition.MIDDLE, SlicePosition.LAST),
            List(4) { SlicePosition.of(it, 4) },
        )
    }

    @Test
    fun `a row is one target, and the one open beside the list says so`() {
        var taps = 0
        compose.setContent {
            KlasTheme {
                Column {
                    SurfaceRow(SlicePosition.FIRST, onClick = { taps++ }, selected = true) { Text("Аня") }
                    SurfaceRow(SlicePosition.LAST, onClick = {}) { Text("Борис") }
                }
            }
        }

        compose.onNodeWithText("Аня", useUnmergedTree = false).assertIsSelected().performClick()
        compose.onNodeWithText("Борис").assert(isSelected().not())

        assertEquals(1, taps)
    }
}
