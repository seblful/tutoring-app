package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// Native graphics: legacy mode measures text as nearly no width, so every line would fit.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class FactsLineTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(width: Dp) {
        rule.setContent {
            KlasTheme {
                Box(Modifier.width(width)) { FactsLine(listOf(PHONE, "In person")) }
            }
        }
    }

    @Test
    fun `facts that fit share one line`() {
        show(400.dp)

        rule.onNodeWithText("$PHONE · In person").assertExists()
    }

    @Test
    fun `facts that do not fit each keep a whole line`() {
        show(100.dp)

        rule.onNodeWithText(PHONE).assertExists()
        rule.onNodeWithText("In person").assertExists()
    }

    private companion object {
        const val PHONE = "+375 29 123-45-67"
    }
}
