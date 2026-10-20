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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SegmentSwipeBoxTest {

    @get:Rule
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

    private val threshold: Float get() = with(rule.density) { 56.dp.toPx() }

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

    @Test
    fun aDragShortOfTheMarkDoesNothing() {
        body()

        drag(each = -0.5f)

        assertEquals(DAY, selected)
        assertEquals(emptyList<HapticFeedbackType>(), recorder.performed)
    }

    @Test
    fun oneGestureMovesOneSegment() {
        body()

        drag(each = -1.5f, steps = 3)

        assertEquals(WEEK, selected)
        assertEquals(listOf(HapticFeedbackType.SegmentTick), recorder.performed)
    }

    @Test
    fun theEndOfTheRowIsTheEnd() {
        selected = MONTH
        body()

        drag(each = -2f)
        assertEquals("there is nothing past «Месяц»", MONTH, selected)
        assertEquals("and nothing to say about it", emptyList<HapticFeedbackType>(), recorder.performed)

        rule.onNodeWithTag(BODY).performTouchInput {
            down(Offset(BodyWidth.toPx() / 2f, EDGE))
            moveBy(Offset(-threshold * 1.5f, 0f))
            moveBy(Offset(threshold * 3f, 0f))
            up()
        }
        rule.waitForIdle()

        assertEquals("a gesture that ran out does not get a second answer", MONTH, selected)
    }

    @Test
    fun aDragOnALessonBillsItAndLeavesTheViewAlone() {
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

    @Test
    fun aDragBesideTheLessonsTurnsThePage() {
        bodyOfLessons()

        rule.onNodeWithTag(BODY).performTouchInput {
            down(Offset(BodyWidth.toPx() - EDGE, BodyHeight.toPx() - EDGE))
            moveBy(Offset(-threshold * 2f, 0f))
            up()
        }
        rule.waitForIdle()

        assertEquals(WEEK, selected)
        assertEquals("no lesson was billed on the way", 0, confirmed)
    }

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
