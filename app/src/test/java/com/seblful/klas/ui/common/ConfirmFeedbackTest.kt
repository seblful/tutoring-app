package com.seblful.klas.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfirmFeedbackTest {

    @get:Rule
    val rule = createComposeRule()

    private class Recorder : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed += hapticFeedbackType
        }
    }

    @Test
    fun theConfirmBuzzIsTheConfirmEffect() {
        val recorder = Recorder()
        rule.setContent {
            KlasTheme {
                CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                    val confirm = rememberConfirmFeedback()
                    Text("готово", Modifier.clickable { confirm() })
                }
            }
        }

        rule.onNodeWithText("готово").performClick()

        assertEquals(listOf(HapticFeedbackType.Confirm), recorder.performed)
    }

    @Test
    fun theArmedTickIsTheThresholdEffect() {
        val recorder = Recorder()
        rule.setContent {
            KlasTheme {
                CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                    val armed = rememberArmedFeedback()
                    Text("метка", Modifier.clickable { armed() })
                }
            }
        }

        rule.onNodeWithText("метка").performClick()

        assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), recorder.performed)
    }

    @Test
    fun nothingBuzzesUntilTheActionHappens() {
        val recorder = Recorder()
        rule.setContent {
            KlasTheme {
                CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                    val confirm = rememberConfirmFeedback()
                    Text("готово", Modifier.clickable { confirm() })
                }
            }
        }

        assertEquals(emptyList<HapticFeedbackType>(), recorder.performed)
    }
}
