package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PickerFieldTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a picker takes the room a text field does, floated label included`() {
        compose.setContent {
            KlasTheme {
                Column {
                    PickerField("20 окт. 2026", "Дата", onClick = {}, Modifier.testTag("picker"))
                    OutlinedTextField(
                        value = "1500",
                        onValueChange = {},
                        label = { Text("Цена за час") },
                        singleLine = true,
                        modifier = Modifier.testTag("typed"),
                    )
                }
            }
        }

        assertEquals(bounds("typed").height, bounds("picker").height)
    }

    @Test
    fun `an empty picker shows its label in the field and opens on a tap`() {
        var opened = false
        compose.setContent { KlasTheme { PickerField("", "Начало", onClick = { opened = true }) } }

        compose.onNodeWithText("Начало").performClick()

        assertTrue(opened)
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
}
