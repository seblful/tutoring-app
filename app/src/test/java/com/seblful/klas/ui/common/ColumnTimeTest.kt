package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColumnTimeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "en-rUS")
    fun `one- and two-digit hours take one width, so the text beside them starts on one edge`() {
        compose.setContent {
            KlasTheme {
                val style = MaterialTheme.typography.titleMedium
                val width = rememberColumnTimeWidth(style)
                Column {
                    ColumnTime("1:00 PM", style, width, Modifier.testTag("short"))
                    ColumnTime("10:00 PM", style, width, Modifier.testTag("widest"))
                }
            }
        }

        assertEquals(width("short"), width("widest"))
        assertFalse("the widest time is cut short", layoutOf("widest").didOverflowWidth)
    }

    private fun width(tag: String): Int = compose.onNodeWithTag(tag).fetchSemanticsNode().size.width

    private fun layoutOf(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }
}
