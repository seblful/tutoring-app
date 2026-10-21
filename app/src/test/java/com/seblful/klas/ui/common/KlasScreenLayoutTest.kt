package com.seblful.klas.ui.common

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class KlasScreenLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private lateinit var view: View

    @Test
    @Config(qualifiers = "w800dp-h360dp")
    fun `a camera cutout in landscape keeps the back arrow and the body clear of it`() {
        setScreen(onBack = {})
        compose.runOnUiThread {
            val density = view.resources.displayMetrics.density
            ViewCompat.dispatchApplyWindowInsets(
                view,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(Cutout.px(density), 0, 0, 0))
                    .build(),
            )
        }
        compose.waitForIdle()

        assertTrue(compose.onNodeWithContentDescription("Back").getBoundsInRoot().left >= Cutout)
        assertTrue(compose.onNodeWithTag(BODY).getBoundsInRoot().left >= Cutout)
    }

    @Test
    @Config(qualifiers = "w610dp-h900dp")
    fun `a medium window keeps Material's 24 dp margins`() {
        setScreen()

        // The body brings the other 16 dp as its gutter.
        assertEquals(8.dp, compose.onNodeWithTag(BODY).getBoundsInRoot().left)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `on a wide window the bar's title and the add button line up with the capped column`() {
        setScreen(fab = true)
        val body = compose.onNodeWithTag(BODY).getBoundsInRoot()
        val title = compose.onNodeWithText(TITLE).getBoundsInRoot()
        val fab = compose.onNodeWithTag(FAB).getBoundsInRoot()

        assertEquals(MaxContentWidth, body.right - body.left)
        assertEquals(body.left + Spacing.gutter, title.left)
        assertEquals(body.right - Spacing.gutter, fab.right)
    }

    @Test
    fun `beside a rail the add button is the rail's, and is taken back when the screen goes`() {
        val rail = RailAction()
        var shown by mutableStateOf(true)
        compose.setContent {
            KlasTheme {
                CompositionLocalProvider(LocalRailAction provides rail) {
                    if (shown) KlasScreen(title = TITLE, floatingActionButton = { Fab() }) { _, _ -> }
                }
            }
        }

        compose.onNodeWithTag(FAB).assertDoesNotExist()
        assertNotNull(rail.content)

        shown = false
        compose.waitForIdle()
        assertNull(rail.content)
    }

    private fun setScreen(onBack: (() -> Unit)? = null, fab: Boolean = false) {
        compose.setContent {
            view = LocalView.current
            KlasTheme {
                KlasScreen(
                    title = TITLE,
                    onBack = onBack,
                    floatingActionButton = if (fab) ({ Fab() }) else null,
                ) { modifier, _ ->
                    // Full width: `constrainedContent` wraps width, and a zero-width node reports empty bounds.
                    Box(modifier.fillMaxWidth().testTag(BODY)) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Fab() {
        FloatingActionButton(onClick = {}, Modifier.testTag(FAB)) { Text("+") }
    }

    private fun Dp.px(density: Float) = (value * density).toInt()

    private companion object {
        const val TITLE = "Screen"
        const val BODY = "body"
        const val FAB = "fab"
        val Cutout = 32.dp
    }
}
