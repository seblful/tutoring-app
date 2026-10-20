package com.seblful.klas.ui.common

import android.content.Context
import android.graphics.Paint
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.R
import com.seblful.klas.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
                    paint.hasGlyph(currency.symbol),
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `English currency choices show the same symbols as amounts`() = assertLabels()

    @Test
    @Config(qualifiers = "ru")
    fun `Russian currency choices show the same symbols as amounts`() = assertLabels()

    private fun assertLabels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val labels = listOf(R.string.currency_byn, R.string.currency_rub, R.string.currency_usd, R.string.currency_eur)
        assertEquals(Currency.entries.map { it.symbol }, labels.map(context::getString))
    }
}
