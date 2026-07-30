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
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * **One tap.** A field that is revealed by tapping is focused by that same tap.
 *
 * The subject is `rememberRevealedFocus`, exercised through the wiring the booking form's price uses: a
 * [PickerField] that swaps itself for a text field. That form's own price took two taps — one to unfold
 * the field, one to put the cursor in it — for an input that exists to be typed into, and the fix is
 * only worth anything if a *keystroke* lands after the first tap, which is what is asserted here rather
 * than the focus flag alone.
 *
 * A device test because focus and the soft keyboard are the platform's, not Compose's.
 *
 * `@HiltAndroidTest` although nothing here is injected: instrumentation swaps the app class for
 * `HiltTestApplication`, and any of the app's own components starting in that process — `MainActivity`
 * if the phone's owner opens the app mid-run, `BootReceiver` after the run's own install — asks it for a
 * component. Without the rule there is none, and the *whole run* dies reporting zero tests rather than a
 * failure. The rule creates one, so a stray wake-up injects and is harmless.
 */
@HiltAndroidTest
class RevealedFocusTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createComposeRule()

    /** What has been typed into the revealed field. */
    private var typed = ""

    /** The booking form's fold, reduced to the part under test. */
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
