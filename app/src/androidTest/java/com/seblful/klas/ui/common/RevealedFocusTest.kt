package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.KeyboardType
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RevealedFocusTest {

    @get:Rule
    val rule = createComposeRule()

    private var typed = ""

    @Composable
    private fun FoldedPrice() {
        var revealed by remember { mutableStateOf(false) }
        Column {
            if (revealed) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Цена за час") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.focusRequester(rememberRevealedFocus()),
                )
            } else {
                PickerField(
                    value = "",
                    label = "Цена за час",
                    placeholder = "Цена не указана",
                    onClick = { revealed = true },
                )
            }
        }
    }

    @Test
    fun theRevealedFieldIsFocusedByTheTapThatRevealedIt() {
        rule.setContent { KlasTheme { FoldedPrice() } }

        rule.onNodeWithText("Цена не указана").performClick()

        rule.onNodeWithText("Цена за час").assertIsFocused()
    }

    @Test
    fun typingWorksImmediatelyAfterTheOneTap() {
        rule.setContent { KlasTheme { FoldedPrice() } }

        rule.onNodeWithText("Цена не указана").performClick()
        rule.onNodeWithText("Цена за час").performTextInput("1500")

        assertEquals("the keystrokes went to the revealed field, with no second tap", "1500", typed)
    }
}
