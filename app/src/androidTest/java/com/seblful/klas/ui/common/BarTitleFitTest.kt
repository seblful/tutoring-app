package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seblful.klas.testutil.textLayoutOf
import com.seblful.klas.ui.theme.KlasTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A form's title is read whole beside its «Сохранить», on a common phone.
 *
 * «Изменить занят…» was the lesson form's title on a 360 dp phone at the default font size — the noun,
 * the half that says which form this is, lost to an ellipsis. The bar title now shrinks a few steps
 * first. Font scale is set by the test rather than read from the device, which would otherwise decide
 * whether it holds; it covers the default and 1.3×, where the shrink's floor being in sp cut it again.
 */
@HiltAndroidTest
class BarTitleFitTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun everyFormTitleFitsBesideSave() {
        val complaints = mutableListOf<String>()
        val shown = mutableStateOf(FormTitles.first())
        val width = mutableStateOf(Widths.first())
        val fontScale = mutableStateOf(FontScales.first())
        composeRule.setForm(shown, width, fontScale)
        FontScales.forEach { scale ->
            FormTitles.forEach { title ->
                Widths.forEach { candidate ->
                    composeRule.runOnIdle {
                        fontScale.value = scale
                        shown.value = title
                        width.value = candidate
                    }
                    composeRule.waitForIdle()
                    val layout = composeRule.textLayoutOf(title)
                    val where = "$candidate, font scale $scale"
                    if (layout.lineCount != 1) complaints += "«$title» takes ${layout.lineCount} lines at $where"
                    if (layout.isLineEllipsized(0)) complaints += "«$title» is cut short at $where"
                }
            }
        }
        assertTrue(complaints.joinToString(prefix = "\n", separator = "\n"), complaints.isEmpty())
    }

    /** Shrinking is a floor for long titles, not an enlargement of short ones. */
    @Test
    fun aShortTitleKeepsTheBarsSize() {
        val width = mutableStateOf(360.dp)
        composeRule.setForm(mutableStateOf(SHORT_TITLE), width)
        val narrow = composeRule.textLayoutOf(SHORT_TITLE).size.width

        composeRule.runOnIdle { width.value = 411.dp }
        composeRule.waitForIdle()

        assertEquals(narrow, composeRule.textLayoutOf(SHORT_TITLE).size.width)
    }

    private fun ComposeContentTestRule.setForm(
        title: MutableState<String>,
        width: MutableState<Dp>,
        fontScale: MutableState<Float> = mutableStateOf(1f),
    ) {
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale.value)) {
                KlasTheme {
                    Box(Modifier.width(width.value)) {
                        KlasScreen(
                            title = title.value,
                            onBack = {},
                            actions = { SaveAction(enabled = true, onSave = {}) },
                        ) {}
                    }
                }
            }
        }
        waitForIdle()
    }

    private companion object {
        /** 360 dp is the common small phone; 411 dp the common large one. */
        val Widths = listOf(360.dp, 411.dp)

        /** The default, and the larger-text step many people use — where the floor's unit showed. */
        val FontScales = listOf(1f, 1.3f)

        /** The longest form titles the app has, in both languages it speaks. */
        val FormTitles = listOf(
            "Изменить занятие",
            "Изменить ученика",
            "Изменить предмет",
            "Изменить группу",
            "Edit lesson",
            "Edit student",
        )

        const val SHORT_TITLE = "Ещё"
    }
}
