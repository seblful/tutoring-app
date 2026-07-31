package com.seblful.klas.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import org.junit.Rule
import org.junit.Test

/**
 * The page turn, wired: **a drag past the mark moves one segment, one step, and ticks like a tap** — and
 * gives way to whatever else on the screen wants the same drag.
 *
 * A device test because that is the only place a drag exists. `SegmentSwipeTest` pins the *rule*
 * ([segmentAfterSwipe]) on the JVM; what is left here is everything the rule cannot see, and all of it is
 * the kind of thing a refactor breaks without touching a number:
 * - the **latch** — one gesture decides once, so a long drag down the schedule does not walk День →
 *   Неделя → Месяц a threshold at a time;
 * - the **arbitration** — a drag begun on a lesson card still bills that lesson and leaves the view alone,
 *   and a flick down a list still scrolls it. This is the whole reason the gesture is on the body rather
 *   than being a pager, so it is asserted rather than assumed.
 *
 * Distances are said as multiples of the threshold, taken from the rule's own density, so the test asks
 * about the shape — *far enough*, *not far enough* — instead of restating 56dp in pixels.
 */
@HiltAndroidTest
class SegmentSwipeBoxTest {

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
    private val views = listOf(DAY, WEEK, MONTH)
    private var selected by mutableStateOf(DAY)
    private var confirmed = 0

    /** 56dp — [swipeBetweenSegments]'s own mark, in this device's pixels. */
    private val threshold: Float get() = with(rule.density) { 56.dp.toPx() }

    /**
     * A body of a stated size, so a drag of *n* thresholds is known to stay inside it. Whatever [content]
     * holds sits under the same gesture, which is the point of most of what follows.
     */
    private fun body(content: @Composable () -> Unit = {}) {
        rule.setContent {
            KlasTheme {
                CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                    Column(
                        Modifier
                            .testTag(BODY)
                            .size(width = BodyWidth, height = BodyHeight)
                            .swipeBetweenSegments(views, selected) { selected = it },
                    ) {
                        content()
                    }
                }
            }
        }
    }

    /**
     * The schedule's shape: lesson cards, each owning the same axis this gesture uses. The list is given
     * less than the body's height on purpose — what is under it is the free space the page turns from, and
     * a list filling the body would leave the screen with none.
     */
    private fun bodyOfLessons() {
        body {
            LazyColumn(Modifier.testTag(LIST).height(ListHeight)) {
                items(20) { index ->
                    SwipeToConfirmBox(
                        onConfirm = { confirmed++ },
                        reveal = { Text("Проведено") },
                        modifier = Modifier.testTag(cardTag(index)).fillMaxWidth().height(72.dp),
                    ) {
                        Text("занятие $index")
                    }
                }
            }
        }
    }

    /**
     * One gesture: [steps] moves of [each] thresholds, then up. Negative moves left, which is forwards.
     *
     * It starts at whichever edge leaves room for the whole travel, so a test says how far it drags and
     * never how close that comes to running off the body.
     */
    private fun drag(each: Float, steps: Int = 1) {
        rule.onNodeWithTag(BODY).performTouchInput {
            val fromTrailing = each < 0f
            down(Offset(if (fromTrailing) BodyWidth.toPx() - EDGE else EDGE, EDGE))
            repeat(steps) { moveBy(Offset(threshold * each, 0f)) }
            up()
        }
        rule.waitForIdle()
    }

    @Test
    fun aDragPastTheMarkTurnsThePage() {
        body()

        drag(each = -2f)

        assertEquals("dragging left is the next view", WEEK, selected)
        assertEquals(
            "the page turned without the tick a tap on the same control would give",
            listOf(HapticFeedbackType.SegmentTick),
            recorder.performed,
        )
    }

    @Test
    fun draggingBackGoesToThePreviousOne() {
        selected = MONTH
        body()

        drag(each = 2f)

        assertEquals(WEEK, selected)
    }

    /** Short of the mark is not a swipe — which is what keeps a wobble off the app's busiest screen. */
    @Test
    fun aDragShortOfTheMarkDoesNothing() {
        body()

        drag(each = -0.5f)

        assertEquals(DAY, selected)
        assertEquals(emptyList<HapticFeedbackType>(), recorder.performed)
    }

    /**
     * **One gesture, one step.** Three moves in a row, each past the mark on its own: without the latch
     * this lands on «Месяц», having passed through a «Неделя» nobody saw, and buzzed three times doing it.
     */
    @Test
    fun oneGestureMovesOneSegment() {
        body()

        drag(each = -1.5f, steps = 3)

        assertEquals(WEEK, selected)
        assertEquals(listOf(HapticFeedbackType.SegmentTick), recorder.performed)
    }

    /**
     * And having decided, the gesture stays decided even where the answer was *nowhere to go*: a tutor on
     * «Месяц» who drags on past the end and then wanders back must not arrive at «Неделя» for it.
     */
    @Test
    fun theEndOfTheRowIsTheEnd() {
        selected = MONTH
        body()

        drag(each = -2f)
        assertEquals("there is nothing past «Месяц»", MONTH, selected)
        assertEquals("and nothing to say about it", emptyList<HapticFeedbackType>(), recorder.performed)

        // From the middle, so the wander back stays on the body.
        rule.onNodeWithTag(BODY).performTouchInput {
            down(Offset(BodyWidth.toPx() / 2f, EDGE))
            moveBy(Offset(-threshold * 1.5f, 0f))
            moveBy(Offset(threshold * 3f, 0f))
            up()
        }
        rule.waitForIdle()

        assertEquals("a gesture that ran out does not get a second answer", MONTH, selected)
    }

    /**
     * **The card keeps its own swipe.** This is the arbitration the whole design rests on: a child consumes
     * the drag past touch slop before the body is offered it, so the lesson is marked taught and the view
     * does not move. Both gestures on one axis, neither aware of the other.
     */
    @Test
    fun aDragOnALessonBillsItAndLeavesTheViewAlone() {
        // Started on «Неделя» deliberately: the card's swipe runs left-to-right, which for the body is
        // *previous*. From «День» that is nowhere to go, and the assertion below would hold whether the
        // card consumed the drag or the body simply ran out of segments — which is no assertion at all.
        selected = WEEK
        bodyOfLessons()
        val card = rule.onNodeWithTag(cardTag(1))
        val width = card.fetchSemanticsNode().size.width.toFloat()

        card.performTouchInput {
            down(Offset(EDGE, EDGE))
            moveTo(Offset(width * 0.8f, EDGE))
            up()
        }
        rule.waitForIdle()

        assertEquals("the lesson was marked taught", 1, confirmed)
        assertEquals("and the schedule stayed on the view it was on", WEEK, selected)
    }

    /** The free space around the cards is where the page turns from — the other half of the same rule. */
    @Test
    fun aDragBesideTheLessonsTurnsThePage() {
        bodyOfLessons()

        // Below the list, where the body is all that is under the thumb.
        rule.onNodeWithTag(BODY).performTouchInput {
            down(Offset(BodyWidth.toPx() - EDGE, BodyHeight.toPx() - EDGE))
            moveBy(Offset(-threshold * 2f, 0f))
            up()
        }
        rule.waitForIdle()

        assertEquals(WEEK, selected)
        assertEquals("no lesson was billed on the way", 0, confirmed)
    }

    /** And the list still scrolls: a flick down the schedule is not a change of view. */
    @Test
    fun scrollingTheListTurnsNothing() {
        bodyOfLessons()
        rule.onNodeWithText("занятие 0").assertExists()

        repeat(2) {
            rule.onNodeWithTag(LIST).performTouchInput { swipeUp() }
            rule.waitForIdle()
        }

        rule.onNodeWithText("занятие 0").assertDoesNotExist()
        assertEquals(DAY, selected)
        assertEquals(emptyList<HapticFeedbackType>(), recorder.performed)
    }

    private companion object {
        const val DAY = "День"
        const val WEEK = "Неделя"
        const val MONTH = "Месяц"

        const val BODY = "segment-swipe-body"
        const val LIST = "segment-swipe-list"

        fun cardTag(index: Int) = "segment-swipe-card-$index"

        /** Inside the body, off its corner — a thumb's starting point rather than a boundary. */
        const val EDGE = 4f

        /** Wide enough for four thresholds of travel and narrow enough to be a phone. */
        val BodyWidth = 320.dp
        val BodyHeight = 480.dp

        /** Less than [BodyHeight], so there is free body below the lessons to drag on. */
        val ListHeight = 200.dp
    }
}
