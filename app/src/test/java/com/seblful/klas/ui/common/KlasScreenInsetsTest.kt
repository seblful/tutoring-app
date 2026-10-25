package com.seblful.klas.ui.common

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.seblful.klas.testutil.ProjectFiles
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KlasScreenInsetsTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var view: View

    @Test
    fun `an ime-aware form ends at the keyboard, not a navigation bar above it`() {
        setScreen(imeAware = true) { modifier, bottomInset ->
            // Full width: `constrainedContent` wraps width, and a zero-width node reports empty bounds.
            Box(modifier.fillMaxWidth().testTag("body")) {
                Box(Modifier.fillMaxSize().padding(bottomInset).testTag("content"))
            }
        }
        dispatchInsets(keyboard = true)

        assertEquals(windowBottom() - Keyboard, bottomOf("body"))
        // The bar's inset is under the keyboard, so it leaves no gap above it either.
        assertEquals(bottomOf("body"), bottomOf("content"))
    }

    @Test
    fun `a list runs under the navigation bar and its last row clears it`() {
        setScreen { modifier, bottomInset ->
            LazyColumn(modifier.fillMaxWidth().testTag("list"), contentPadding = bottomInset) {
                items(Rows) { Box(Modifier.fillMaxWidth().height(RowHeight).testTag("row $it")) }
            }
        }
        dispatchInsets(keyboard = false)
        compose.onNodeWithTag("list").performScrollToIndex(Rows - 1)
        compose.waitForIdle()

        assertEquals(windowBottom(), bottomOf("list"))
        assertEquals(windowBottom() - NavBar, bottomOf("row ${Rows - 1}"))
    }

    @Test
    fun `on a tab under the floating bar the last row and the add button clear the bar`() {
        setScreen(barClearance = BarClearance, fab = true) { modifier, bottomInset ->
            LazyColumn(modifier.fillMaxWidth().testTag("list"), contentPadding = bottomInset) {
                items(Rows) { Box(Modifier.fillMaxWidth().height(RowHeight).testTag("row $it")) }
            }
        }
        dispatchInsets(keyboard = false)
        compose.onNodeWithTag("list").performScrollToIndex(Rows - 1)
        compose.waitForIdle()

        val barTop = windowBottom() - NavBar - BarClearance
        assertEquals(windowBottom(), bottomOf("list"))
        assertEquals(barTop, bottomOf("row ${Rows - 1}"))
        assertTrue("the add button sits under the bar", bottomOf("fab") <= barTop)
    }

    @Test
    fun `the activity resizes for the keyboard`() {
        val manifest = ProjectFiles.moduleFile("src/main/AndroidManifest.xml").readText()

        assertTrue(
            "`MainActivity` lost `windowSoftInputMode=\"adjustResize\"`; below API 30 the IME inset never " +
                "reaches Compose without it, and `imePadding()` does nothing.",
            "android:windowSoftInputMode=\"adjustResize\"" in manifest,
        )
    }

    private fun setScreen(
        imeAware: Boolean = false,
        barClearance: Dp = 0.dp,
        fab: Boolean = false,
        content: @Composable (Modifier, PaddingValues) -> Unit,
    ) {
        compose.setContent {
            view = LocalView.current
            KlasTheme {
                CompositionLocalProvider(LocalTabBarClearance provides barClearance) {
                    KlasScreen(
                        title = "Screen",
                        imeAware = imeAware,
                        floatingActionButton = if (fab) {
                            { Box(Modifier.size(FabSize).testTag("fab")) }
                        } else {
                            null
                        },
                        content = content,
                    )
                }
            }
        }
    }

    /** A pushed screen: the navigation suite is hidden, so nothing above consumes the bar's inset. */
    private fun dispatchInsets(keyboard: Boolean) {
        compose.runOnUiThread {
            val density = view.resources.displayMetrics.density
            ViewCompat.dispatchApplyWindowInsets(
                view,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NavBar.px(density)))
                    .setInsets(
                        WindowInsetsCompat.Type.ime(),
                        Insets.of(0, 0, 0, if (keyboard) Keyboard.px(density) else 0),
                    )
                    .setVisible(WindowInsetsCompat.Type.ime(), keyboard)
                    .build(),
            )
        }
        compose.waitForIdle()
    }

    private fun windowBottom() = compose.onRoot().getBoundsInRoot().bottom

    private fun bottomOf(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().bottom

    private fun Dp.px(density: Float) = (value * density).toInt()

    private companion object {
        val NavBar = 48.dp
        val Keyboard = 280.dp
        val BarClearance = 72.dp
        val FabSize = 56.dp
        val RowHeight = 100.dp
        const val Rows = 30
    }
}
