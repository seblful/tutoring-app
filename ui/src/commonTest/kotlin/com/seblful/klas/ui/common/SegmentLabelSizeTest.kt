package com.seblful.klas.ui.common

import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

/** A row of segments takes one size: the largest its longest label fits (see `SegmentLabel`). */
class SegmentLabelSizeTest {

    /** Labels as widths per sp of font size, in a segment [width] wide. */
    private fun sizeFor(widthPerSp: List<Float>, width: Float) =
        largestFitting(14.sp, 10.sp) { size -> widthPerSp.all { it * size.value <= width } }

    @Test
    fun `a row whose labels all fit keeps the full size`() {
        assertEquals(14.sp, sizeFor(listOf(3f, 4f), width = 60f))
    }

    @Test
    fun `the longest label sets the size for the whole row`() {
        // «System setting» needs 12 sp; «Monday» would fit at 14 but takes the row's size, not its own.
        assertEquals(12.sp, sizeFor(listOf(5f, 3.5f, 3.5f, 4f), width = 60f))
    }

    @Test
    fun `nothing shrinks below the floor - the label is cut there instead`() {
        assertEquals(10.sp, sizeFor(listOf(9f, 3f), width = 60f))
    }
}
