package com.seblful.klas.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertFalse("the same drag on a row three times as wide", swipeArmed(third, width * 3f))
    }
}
