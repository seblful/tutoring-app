package com.seblful.klas.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.seblful.klas.print.PdfSheetPrinter.Companion.PAGE_HEIGHT
import com.seblful.klas.print.PdfSheetPrinter.Companion.PAGE_WIDTH
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The page drawing, on a bitmap; the document around it is written only on a device (androidTest). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfSheetPageTest {

    @Test
    fun `every page of a long sheet draws within the margins`() {
        val rows = List(100) { Sheet.Row("Lesson, Maths $it — a name long enough to need cutting short", "25.00 BYN") }
        val sheet = Sheet("Anna: lessons and payments", listOf(Sheet.Section("June", rows)), closing = "To pay: 25 BYN")
        val pages = sheet.pages(PdfSheetPrinter.LINES_PER_PAGE)

        pages.forEachIndexed { index, lines ->
            val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            PdfSheetPrinter().drawPage(Canvas(bitmap), lines, index, pages.size)

            assertTrue("page ${index + 1} is blank", inked(bitmap, 0, bitmap.height))
            val margin = PdfSheetPrinter.MARGIN.toInt() / 2
            assertTrue("page ${index + 1} runs into the top margin", !inked(bitmap, 0, margin))
        }
    }

    private fun inked(bitmap: Bitmap, from: Int, to: Int): Boolean =
        (from until to).any { y -> (0 until bitmap.width).any { x -> bitmap.getPixel(x, y) != Color.WHITE } }
}
