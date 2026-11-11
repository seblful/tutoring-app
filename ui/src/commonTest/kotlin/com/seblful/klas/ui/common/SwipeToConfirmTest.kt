package com.seblful.klas.ui.common

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwipeToConfirmTest {

    private val width = 360f

    @Test
    fun `a row that has not reached the mark is not armed`() {
        assertFalse(swipeArmed(dragged = 0f, width = width), "an untouched row")
        assertFalse(swipeArmed(dragged = 4f, width = width), "a nudge")
        assertFalse(swipeArmed(dragged = width / 4f, width = width), "a quarter of the way")
    }

    @Test
    fun `a third of the way arms — and so does everything past it`() {
        assertTrue(swipeArmed(dragged = width / 3f, width = width), "a third")
        assertTrue(swipeArmed(dragged = width / 2f, width = width), "halfway")
        assertTrue(swipeArmed(dragged = width, width = width), "the whole width")
    }

    @Test
    fun `sliding home again disarms`() {
        val armed = swipeArmed(dragged = width / 2f, width = width)
        val takenBack = swipeArmed(dragged = width / 8f, width = width)

        assertTrue(armed)
        assertFalse(takenBack)
    }

    @Test
    fun `an unmeasured row arms nothing`() {
        assertFalse(swipeArmed(dragged = 0f, width = 0f))
        assertFalse(swipeArmed(dragged = 120f, width = 0f))
    }

    @Test
    fun `the mark scales with the row`() {
        val third = width / 3f

        assertTrue(swipeArmed(dragged = third, width = width))
        assertFalse(swipeArmed(third, width * 3f), "the same drag on a row three times as wide")
    }
}
