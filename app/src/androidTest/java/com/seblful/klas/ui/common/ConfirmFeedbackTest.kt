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

/**
 * Each buzz is the platform effect that *means* its moment, and each is performed through
 * `LocalHapticFeedback` — which is the whole point, since that is the path that honours the phone's
 * touch-feedback setting.
 *
 * Asserted by substituting the composition local: a real buzz cannot be observed from a test, but *what
 * was asked of the phone* can, and the effect is the part a refactor could quietly change (`LongPress` for
 * a confirmation feels like a rejection). The two moments are named apart on purpose — «now» at a drag's
 * mark and «done» when the action lands — so a single gesture saying both says two different things.
 */
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

        // The platform's own name for a drag that has become eligible and can still be cancelled by
        // moving back — see [rememberArmedFeedback]. `Confirm` here would say the thing was already done.
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

        // Composing the caller must not buzz — the buzz belongs to the gesture, not to the recomposition
        // that put the card on screen.
        assertEquals(emptyList<HapticFeedbackType>(), recorder.performed)
    }
}
