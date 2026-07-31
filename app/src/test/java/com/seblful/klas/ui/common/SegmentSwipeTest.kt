package com.seblful.klas.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The swipe's one rule: [segmentAfterSwipe] — *where does a drag in this direction land?*
 *
 * A JVM test, which is the point of the rule not living inside the gesture callback that reads it. What it
 * mostly asserts is the **absence** of a wrap: that is the decision, and it is one of the two places this
 * feature could quietly become something else (the other being one gesture walking more than one step,
 * which only a device can show — `SegmentSwipeBoxTest`).
 *
 * Said in `String`s rather than in either screen's enum on purpose: the rule is about a list and a
 * position, and pinning it to `ScheduleViewMode` would make «День» look like part of it.
 */
class SegmentSwipeTest {

    private val three = listOf("День", "Неделя", "Месяц")
    private val two = listOf("Ученики", "Группы")

    @Test
    fun `dragging left goes to the next segment`() {
        assertEquals("Неделя", segmentAfterSwipe(three, "День", forward = true))
        assertEquals("Месяц", segmentAfterSwipe(three, "Неделя", forward = true))
    }

    @Test
    fun `dragging right goes to the previous one`() {
        assertEquals("Неделя", segmentAfterSwipe(three, "Месяц", forward = false))
        assertEquals("День", segmentAfterSwipe(three, "Неделя", forward = false))
    }

    /**
     * **The end of the row is the end.** A segmented control is a line of detents the tutor can see, so
     * carrying on past «Месяц» and arriving at «День» would be the pill jumping the full width of the
     * control in the direction opposite to the thumb — and on «Люди», where there are two sides, a wrap
     * would make every swipe in either direction do the same thing, which is a toggle pretending to be
     * navigation.
     */
    @Test
    fun `the ends do not wrap`() {
        assertNull("dragged back off the front", segmentAfterSwipe(three, "День", forward = false))
        assertNull("dragged on past the end", segmentAfterSwipe(three, "Месяц", forward = true))
    }

    @Test
    fun `two segments are the same rule`() {
        assertEquals("Группы", segmentAfterSwipe(two, "Ученики", forward = true))
        assertEquals("Ученики", segmentAfterSwipe(two, "Группы", forward = false))
        assertNull(segmentAfterSwipe(two, "Ученики", forward = false))
        assertNull(segmentAfterSwipe(two, "Группы", forward = true))
    }

    /**
     * A selection the options do not contain is a caller whose two arguments disagree. Answering "the
     * first one" would turn that into a page turn nobody asked for, at a position nobody chose; answering
     * nothing leaves the screen where it is, which is what a gesture the app cannot make sense of should
     * cost.
     */
    @Test
    fun `a selection that is not among the options lands nowhere`() {
        assertNull(segmentAfterSwipe(three, "Год", forward = true))
        assertNull(segmentAfterSwipe(three, "Год", forward = false))
    }

    /** Nothing to move between, and — the case a `getOrNull` is what makes safe — nothing to fall off. */
    @Test
    fun `an empty or single choice never moves`() {
        assertNull(segmentAfterSwipe(emptyList<String>(), "День", forward = true))
        assertNull(segmentAfterSwipe(listOf("День"), "День", forward = true))
        assertNull(segmentAfterSwipe(listOf("День"), "День", forward = false))
    }
}
