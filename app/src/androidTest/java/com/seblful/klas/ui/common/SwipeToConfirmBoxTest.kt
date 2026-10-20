package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SwipeToConfirmBoxTest {

    @get:Rule
    val rule = createComposeRule()

    private class Recorder : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed += hapticFeedbackType
        }
    }

    private val recorder = Recorder()
    private var confirmed = 0

    private var armedNow = false

    private fun row() {
        rule.setContent {
            KlasTheme {
                CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                    SwipeToConfirmBox(
                        onConfirm = { confirmed++ },
                        reveal = { armed ->
                            armedNow = armed
                            Text("Проведено")
                        },
                        modifier = Modifier.testTag(ROW).fillMaxWidth().height(72.dp),
                    ) {
                        Text("занятие")
                    }
                }
            }
        }
    }

    private fun listOfRows() {
        rule.setContent {
            KlasTheme {
                CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                    LazyColumn(Modifier.testTag(LIST).height(200.dp)) {
                        items(20) { index ->
                            SwipeToConfirmBox(
                                onConfirm = { confirmed++ },
                                reveal = { armed ->
                                    if (armed) armedNow = true
                                    Text("Проведено")
                                },
                                modifier = Modifier.testTag(rowTag(index)).fillMaxWidth().height(72.dp),
                            ) {
                                Text("занятие $index")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun width(): Float = rule.onNodeWithTag(ROW).fetchSemanticsNode().size.width.toFloat()

    private fun dragPastTheMark() {
        val target = Offset(width() * 0.8f, EDGE)
        rule.onNodeWithTag(ROW).performTouchInput {
            down(Offset(EDGE, EDGE))
            moveTo(target)
        }
        rule.waitForIdle()
    }

    @Test
    fun theMarkTicksAndDoesNothingElse() {
        row()

        dragPastTheMark()

        assertEquals(
            "the mark is announced with the platform's threshold tick, once",
            listOf(HapticFeedbackType.GestureThresholdActivate),
            recorder.performed,
        )
        assertTrue("the reveal was not told, so nothing pops for a phone with haptics off", armedNow)
        assertEquals("nothing is done until the finger lifts", 0, confirmed)
    }

    @Test
    fun slidingHomeTakesItBack() {
        row()
        dragPastTheMark()

        rule.onNodeWithTag(ROW).performTouchInput {
            moveTo(Offset(EDGE * 2, EDGE))
            up()
        }
        rule.waitForIdle()

        assertEquals("a gesture taken back does nothing", 0, confirmed)
        assertEquals(
            "a swipe that came home said «done» anyway",
            listOf(HapticFeedbackType.GestureThresholdActivate),
            recorder.performed,
        )
        assertFalse("the check is still held large over a row that will not be marked", armedNow)
    }

    @Test
    fun liftingPastTheMarkConfirms() {
        row()
        dragPastTheMark()

        rule.onNodeWithTag(ROW).performTouchInput { up() }
        rule.waitForIdle()

        assertEquals("the lift is what acts, and it acts once", 1, confirmed)
        assertEquals(
            "the two moments are said with two effects: «now» at the mark, «done» on the lift",
            listOf(HapticFeedbackType.GestureThresholdActivate, HapticFeedbackType.Confirm),
            recorder.performed,
        )
        assertFalse("the reveal is still armed after the row came home", armedNow)
    }

    @Test
    fun scrollingTheListConfirmsNothing() {
        listOfRows()
        rule.onNodeWithText("занятие 0").assertExists()

        repeat(2) {
            rule.onNodeWithTag(LIST).performTouchInput { swipeUp() }
            rule.waitForIdle()
        }

        rule.onNodeWithText("занятие 0").assertDoesNotExist()
        assertEquals("a scroll is not a swipe", 0, confirmed)
        assertEquals(emptyList<HapticFeedbackType>(), recorder.performed)
    }

    @Test
    fun aRowInTheListStillConfirms() {
        listOfRows()
        val node = rule.onNodeWithTag(rowTag(1))
        val across = Offset(node.fetchSemanticsNode().size.width * 0.8f, EDGE)

        node.performTouchInput {
            down(Offset(EDGE, EDGE))
            moveTo(across)
        }
        rule.waitForIdle()
        node.performTouchInput { up() }
        rule.waitForIdle()

        assertEquals(1, confirmed)
        assertEquals(
            listOf(HapticFeedbackType.GestureThresholdActivate, HapticFeedbackType.Confirm),
            recorder.performed,
        )
    }

    @Test
    fun aTapConfirmsNothing() {
        row()

        rule.onNodeWithTag(ROW).performTouchInput {
            down(Offset(EDGE, EDGE))
            up()
        }
        rule.waitForIdle()

        assertEquals(0, confirmed)
        assertEquals(emptyList<HapticFeedbackType>(), recorder.performed)
    }

    private companion object {
        const val ROW = "swipe-to-confirm-row"
        const val LIST = "swipe-to-confirm-list"

        fun rowTag(index: Int) = "$ROW-$index"

        /** Inside the row, off its corner — a thumb's starting point rather than a boundary. */
        const val EDGE = 4f
    }
}
