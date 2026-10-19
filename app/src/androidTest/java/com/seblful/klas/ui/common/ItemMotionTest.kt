package com.seblful.klas.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * **A list whose content is swapped draws the new content on the next frame, and nothing of the old.**
 *
 * The schedule's glitch, at its smallest: with Compose's default `animateItem()`, a row leaving a lazy list
 * keeps drawing at its last place while it fades out, and the row taking that place fades in under it — so
 * a day tap printed one day's names over the next for a quarter of a second, and a view switch laid the
 * departing lesson cards across the month grid. [animateInPlace] takes the fades away.
 *
 * Asserted on **pixels**, on a paused frame clock, because that is where the ghost lives: a departing row
 * is no longer in the semantics tree while it is still being drawn, so a node assertion passes either way.
 * Each page is one solid colour; one frame after the swap, the first row must be the new page's colour
 * exactly — not the old one fading over it.
 */
class ItemMotionTest {

    @get:Rule
    val rule = createComposeRule()

    private var page by mutableStateOf(listOf(OLD to 1, OLD to 2))

    @Test
    fun aSwappedListDrawsNothingOfTheOldPageOneFrameLater() {
        rule.setContent {
            LazyColumn(Modifier.background(Color.White)) {
                items(page, key = { "${it.first}/${it.second}" }) { (colour, _) ->
                    Box(animateInPlace(Modifier.fillMaxWidth().height(48.dp).background(colour)))
                }
            }
        }
        assertEquals(OLD, firstRowColour())

        rule.mainClock.autoAdvance = false
        page = listOf(NEW to 1, NEW to 2)
        rule.mainClock.advanceTimeByFrame()

        assertEquals(NEW, firstRowColour())
    }

    private fun firstRowColour(): Color {
        val pixels = rule.onRoot().captureToImage().toPixelMap()
        val y = with(rule.density) { 24.dp.roundToPx() }
        return pixels[pixels.width / 2, y]
    }

    private companion object {
        val OLD = Color.Red
        val NEW = Color.Blue
    }
}
