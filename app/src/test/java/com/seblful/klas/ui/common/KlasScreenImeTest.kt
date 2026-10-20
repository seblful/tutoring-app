package com.seblful.klas.ui.common

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
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
class KlasScreenImeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `an ime-aware form ends at the keyboard, not a navigation bar above it`() {
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            KlasTheme {
                // Full width: `constrainedContent` wraps width, and a zero-width node reports empty bounds.
                KlasScreen(title = "Form", imeAware = true) { modifier ->
                    Box(modifier.fillMaxWidth().testTag("body"))
                }
            }
        }

        // A pushed screen: the navigation suite is hidden, so nothing above consumes the bar's inset.
        compose.runOnUiThread {
            val density = view.resources.displayMetrics.density
            ViewCompat.dispatchApplyWindowInsets(
                view,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NavBar.px(density)))
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, Keyboard.px(density)))
                    .setVisible(WindowInsetsCompat.Type.ime(), true)
                    .build(),
            )
        }
        compose.waitForIdle()

        val window = compose.onRoot().getBoundsInRoot().bottom
        val body = compose.onNodeWithTag("body").getBoundsInRoot().bottom
        assertEquals(window - Keyboard, body)
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

    private fun Dp.px(density: Float) = (value * density).toInt()

    private companion object {
        val NavBar = 48.dp
        val Keyboard = 280.dp
    }
}
