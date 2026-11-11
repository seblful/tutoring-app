package com.seblful.klas.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CurrencyFontTest {

    @Test
    fun `every font weight can draw every currency symbol`() {
        WEIGHTS.forEach { weight ->
            val paint = Paint().apply {
                typeface = golos(weight)
                textSize = 32f
            }
            Currency.entries.forEach { currency ->
                assertTrue(
                    "golos_text_$weight lacks $currency",
                    currency.symbol.all { paint.hasGlyph(it.toString()) },
                )
            }
        }
    }

    @Test
    fun `the Belarusian ruble gains ink at every heavier weight`() {
        val ink = WEIGHTS.map { weight ->
            val bitmap = Bitmap.createBitmap(300, 260, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = golos(weight)
                textSize = 200f
            }
            Canvas(bitmap).drawText(Currency.BYN.symbol, 20f, 210f, paint)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle()
            pixels.sumOf { (it ushr 24).toLong() }
        }
        assertTrue("BYN ink must increase with weight: $ink", ink.zipWithNext().all { (a, b) -> b > a })
        assertTrue("Bold BYN should be visibly heavier: $ink", ink.last() > ink.first() * 1.2)
    }

    private companion object {
        /** The weights the app ships, lightest first: `ui/src/commonMain/composeResources/font`. */
        val WEIGHTS = listOf("regular", "medium", "semibold", "bold")

        fun golos(weight: String): Typeface =
            Typeface.createFromFile(ProjectFiles.composeResource("font/golos_text_$weight.ttf"))
    }
}
