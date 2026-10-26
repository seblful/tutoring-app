package com.seblful.klas.ui.designsystem.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The label is hidden from TalkBack, which reads the button's name instead, so it is told apart by width. */
@RunWith(RobolectricTestRunner::class)
class KlasFabTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a collapsed rail's button is its icon alone, still named for TalkBack`() {
        val width = widthOf(iconOnly = true)
        assertTrue("$width is wider than the icon alone", width <= ICON_ONLY_MAX)
    }

    @Test
    fun `elsewhere the button says what it adds`() {
        val width = widthOf(iconOnly = false)
        assertTrue("$width is no wider than the icon alone", width > ICON_ONLY_MAX)
    }

    private fun widthOf(iconOnly: Boolean): Dp {
        compose.setContent {
            KlasTheme {
                CompositionLocalProvider(LocalFabIconOnly provides iconOnly) {
                    KlasFab(label = "New student", onClick = {})
                }
            }
        }
        val bounds = compose.onNodeWithContentDescription("New student").getBoundsInRoot()
        return bounds.right - bounds.left
    }

    private companion object {
        /** A FAB with only its icon, 56 dp, with room for the theme's rounding. */
        val ICON_ONLY_MAX = 64.dp
    }
}
