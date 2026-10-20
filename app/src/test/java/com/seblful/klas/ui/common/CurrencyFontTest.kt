package com.seblful.klas.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.R
import com.seblful.klas.domain.model.Currency
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fonts = listOf(
            R.font.golos_text_regular,
            R.font.golos_text_medium,
            R.font.golos_text_semibold,
            R.font.golos_text_bold,
        )
        fonts.forEach { font ->
            val paint = Paint().apply {
                typeface = ResourcesCompat.getFont(context, font)
                textSize = 32f
            }
            Currency.entries.forEach { currency ->
                assertTrue(
                    "${context.resources.getResourceEntryName(font)} lacks $currency",
                    currency.symbol.all { paint.hasGlyph(it.toString()) },
                )
            }
        }
    }

    @Test
    fun `the Belarusian ruble gains ink at every heavier weight`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ink = listOf(
            R.font.golos_text_regular, R.font.golos_text_medium, R.font.golos_text_semibold, R.font.golos_text_bold,
        ).map { font ->
            val bitmap = Bitmap.createBitmap(300, 260, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = ResourcesCompat.getFont(context, font)
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
}
