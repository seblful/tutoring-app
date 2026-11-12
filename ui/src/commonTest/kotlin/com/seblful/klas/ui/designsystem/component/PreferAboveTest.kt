package com.seblful.klas.ui.designsystem.component

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.test.Test
import kotlin.test.assertEquals

/** A list item's tip opens above it while it fits there, so it never covers the items after. */
class PreferAboveTest {

    /** Places a bubble [gap] px above or below its anchor, as Material's providers do. */
    private class Side(private val over: Boolean, private val gap: Int = 8) : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: IntRect,
            windowSize: IntSize,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
        ) = IntOffset(
            anchorBounds.left,
            if (over) anchorBounds.top - popupContentSize.height - gap else anchorBounds.bottom + gap,
        )
    }

    private val provider = PreferAbove(above = Side(over = true), below = Side(over = false), top = STATUS_BAR)

    private fun yFor(anchorTop: Int): Int = provider.calculatePosition(
        IntRect(left = 0, top = anchorTop, right = 1000, bottom = anchorTop + 200),
        IntSize(1080, 2340),
        LayoutDirection.Ltr,
        IntSize(900, BUBBLE),
    ).y

    @Test
    fun `with room above the card the bubble opens there`() {
        assertEquals(700 - BUBBLE - 8, yFor(anchorTop = 700))
    }

    @Test
    fun `a card near the top opens below - never under the status bar`() {
        assertEquals(300 + 200 + 8, yFor(anchorTop = 300))
    }

    private companion object {
        const val STATUS_BAR = 110
        const val BUBBLE = 350
    }
}
