package com.seblful.klas.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The swipe's one rule: [swipeArmed] — *would letting go now confirm?*
 *
 * Worth a test of its own because two moments read it and they must never disagree: the tick that tells the
 * tutor the mark is crossed, and the lift that marks the lesson taught. A tick with no action behind it, or
 * an action nothing announced, is the gesture failing at the only thing it promises.
 *
 * A JVM test, which is the point of the rule not living inside the `@Composable` that drags the row. The
 * offsets are said as fractions of the row rather than as the constant, so the test asks about the shape of
 * the rule — a mark somewhere between a quarter and a third of the way — and not about a number it would
 * only be restating.
 */
class SwipeToConfirmTest {

    private val width = 360f

    @Test
    fun `a row that has not reached the mark is not armed`() {
        assertFalse("an untouched row", swipeArmed(dragged = 0f, width = width))
        assertFalse("a nudge", swipeArmed(dragged = 4f, width = width))
        assertFalse("a quarter of the way", swipeArmed(dragged = width / 4f, width = width))
    }

    @Test
    fun `a third of the way arms, and so does everything past it`() {
        assertTrue("a third", swipeArmed(dragged = width / 3f, width = width))
        assertTrue("halfway", swipeArmed(dragged = width / 2f, width = width))
        assertTrue("the whole width", swipeArmed(dragged = width, width = width))
    }

    /**
     * Sliding back is not a second gesture — it is the same question asked at a smaller offset, which is
     * what makes the swipe reversible without any state to keep in step.
     */
    @Test
    fun `sliding home again disarms`() {
        val armed = swipeArmed(dragged = width / 2f, width = width)
        val takenBack = swipeArmed(dragged = width / 8f, width = width)

        assertTrue(armed)
        assertFalse(takenBack)
    }

    /**
     * A row is measured a frame after it is composed, and every offset is past a mark at zero. Left
     * unguarded, a list arriving under a resting thumb could bill a lesson.
     */
    @Test
    fun `an unmeasured row arms nothing`() {
        assertFalse(swipeArmed(dragged = 0f, width = 0f))
        assertFalse(swipeArmed(dragged = 120f, width = 0f))
    }

    /** The mark is a fraction of the row, so a wider row asks for a longer drag, not the same pixels. */
    @Test
    fun `the mark scales with the row`() {
        val third = width / 3f

        assertTrue(swipeArmed(dragged = third, width = width))
        assertFalse("the same drag on a row three times as wide", swipeArmed(third, width * 3f))
    }
}
