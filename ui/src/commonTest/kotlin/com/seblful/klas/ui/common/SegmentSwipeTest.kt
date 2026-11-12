package com.seblful.klas.ui.common

import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `forward is toward the reading start in either direction`() {
        assertTrue(swipeIsForward(-1f, LayoutDirection.Ltr))
        assertFalse(swipeIsForward(1f, LayoutDirection.Ltr))
        assertTrue(swipeIsForward(1f, LayoutDirection.Rtl))
        assertFalse(swipeIsForward(-1f, LayoutDirection.Rtl))
    }

    @Test
    fun `the ends do not wrap`() {
        assertNull(segmentAfterSwipe(three, "День", forward = false), "dragged back off the front")
        assertNull(segmentAfterSwipe(three, "Месяц", forward = true), "dragged on past the end")
    }

    @Test
    fun `two segments are the same rule`() {
        assertEquals("Группы", segmentAfterSwipe(two, "Ученики", forward = true))
        assertEquals("Ученики", segmentAfterSwipe(two, "Группы", forward = false))
        assertNull(segmentAfterSwipe(two, "Ученики", forward = false))
        assertNull(segmentAfterSwipe(two, "Группы", forward = true))
    }

    @Test
    fun `a selection that is not among the options lands nowhere`() {
        assertNull(segmentAfterSwipe(three, "Год", forward = true))
        assertNull(segmentAfterSwipe(three, "Год", forward = false))
    }

    @Test
    fun `an empty or single choice never moves`() {
        assertNull(segmentAfterSwipe(emptyList<String>(), "День", forward = true))
        assertNull(segmentAfterSwipe(listOf("День"), "День", forward = true))
        assertNull(segmentAfterSwipe(listOf("День"), "День", forward = false))
    }
}
