package com.seblful.klas.ui.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Real text measurement: where the action goes depends on how wide its label is. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BannerTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(width: Dp, action: String) = compose.setContent {
        KlasTheme {
            Box(Modifier.width(width)) {
                Banner(
                    TITLE,
                    BannerKind.Advisory,
                    detail = "Your data is on this phone only.",
                    action = SectionAction(action) {},
                )
            }
        }
    }

    private fun top(text: String) = compose.onNodeWithText(text).getUnclippedBoundsInRoot().top
    private fun bottom(text: String) = compose.onNodeWithText(text).getUnclippedBoundsInRoot().bottom

    @Test
    fun `a short action sits beside the words`() {
        show(width = 360.dp, action = "Allow")

        assertTrue("the action starts below the title", top("Allow") < bottom(TITLE))
    }

    @Test
    fun `an action that would crowd the words goes under them`() {
        show(width = 300.dp, action = "Сохранить копию сейчас")

        assertTrue("the action is beside the title", top("Сохранить копию сейчас") >= bottom(TITLE))
    }

    private companion object {
        const val TITLE = "No backup yet"
    }
}
