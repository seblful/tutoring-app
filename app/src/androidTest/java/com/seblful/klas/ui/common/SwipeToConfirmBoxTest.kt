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
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The gesture's promise, end to end: **the tick comes at the mark, the action comes on the lift, and the
 * finger can take it all back in between.**
 *
 * A device test because it is the only place a drag exists. `SwipeToConfirmTest` pins the *rule*
 * ([swipeArmed]) on the JVM; this pins that the rule is wired to the three moments — which is the half a
 * refactor can break without changing a single number, by ticking on release, or acting at the mark, or
 * acting on a gesture that came home.
 *
 * The haptics are observed by substituting `LocalHapticFeedback`: a buzz cannot be felt from a test, but
 * *when* the phone was asked for one, and which effect, is the whole of what is being asserted. Each drag is
 * injected in steps with the composition let settle in between, so «at the mark» is a moment the assertions
 * can stand in rather than a frame they race.
 */
@HiltAndroidTest
class SwipeToConfirmBoxTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private class Recorder : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed += hapticFeedbackType
        }
    }

    private val recorder = Recorder()
    private var confirmed = 0

    /**
     * What the reveal was last told, which is what the pop is drawn from (`ui/common/Pop`). Asserted beside
     * the haptics so the felt half and the seen half are pinned to the same flag: a `graphicsLayer` scale
     * cannot be read back from a semantics tree, but *being told* can.
     */
    private var armedNow = false

    /** A row as wide as the screen, so a third of it is a real drag, and tall enough for a thumb. */
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

    /** The same row, twenty of them, in a list that scrolls the other way — where the gesture ships. */
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

    /** Drags from the near edge to four fifths across — well past the mark, whatever the screen. */
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
        // The row is home again, so the promise is over: what says «done» from here is the caller's badge,
        // which pops on its own (`Modifier.popsOnChange`).
        assertFalse("the reveal is still armed after the row came home", armedNow)
    }

    /**
     * The list scrolls the other way, and the two gestures have to tell themselves apart: a lesson marked
     * taught by a flick down the schedule would be the worst way to learn this rule exists.
     */
    @Test
    fun scrollingTheListConfirmsNothing() {
        listOfRows()
        rule.onNodeWithText("занятие 0").assertExists()

        repeat(2) {
            rule.onNodeWithTag(LIST).performTouchInput { swipeUp() }
            rule.waitForIdle()
        }

        // The list took the vertical drag: the first row is scrolled away, not merely offset sideways.
        rule.onNodeWithText("занятие 0").assertDoesNotExist()
        assertEquals("a scroll is not a swipe", 0, confirmed)
        assertEquals(emptyList<HapticFeedbackType>(), recorder.performed)
    }

    /** And the other way round: inside that list, a sideways drag is still the row's own. */
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

    /** A row is still a row: a touch that goes nowhere is a tap for the content, not a confirmation. */
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
