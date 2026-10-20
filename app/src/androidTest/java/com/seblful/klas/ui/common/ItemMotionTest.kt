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
