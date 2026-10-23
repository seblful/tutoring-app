package com.seblful.klas.print

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.io.ByteArrayOutputStream

/**
 * [SheetPrinter] over the platform's [PdfDocument]: A4 pages, amounts against the right margin, a page count
 * at the foot. Plain system type, so Cyrillic prints as it reads on screen.
 */
@ContributesBinding(AppScope::class)
@Inject
class PdfSheetPrinter : SheetPrinter {

    override fun print(sheet: Sheet): ByteArray {
        val pages = sheet.pages(LINES_PER_PAGE)
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, lines ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
                drawPage(page.canvas, lines, index, pages.size)
                document.finishPage(page)
            }
            return ByteArrayOutputStream().also(document::writeTo).toByteArray()
        } finally {
            document.close()
        }
    }

    /** Draws [lines] as page [index] of [count] — apart from the document, which only a device can write. */
    internal fun drawPage(canvas: Canvas, lines: List<Sheet.Line>, index: Int, count: Int) {
        draw(canvas, lines)
        if (count > 1) footer(canvas, "${index + 1} / $count")
    }

    private fun draw(canvas: Canvas, lines: List<Sheet.Line>) {
        var top = MARGIN
        lines.forEach { line ->
            val bottom = top + line.height * LINE
            when (line) {
                is Sheet.Line.Title -> canvas.drawText(fit(line.text, title, WIDTH), MARGIN, bottom - LINE / 2, title)
                is Sheet.Line.Subtitle -> canvas.drawText(fit(line.text, muted, WIDTH), MARGIN, baseline(top), muted)
                is Sheet.Line.Heading -> {
                    canvas.drawText(fit(line.text, heading, WIDTH), MARGIN, baseline(bottom - LINE), heading)
                    canvas.drawLine(MARGIN, bottom - RULE_GAP, MARGIN + WIDTH, bottom - RULE_GAP, rule)
                }
                is Sheet.Line.Entry -> entry(canvas, line.row, top)
                is Sheet.Line.Closing ->
                    canvas.drawText(fit(line.text, heading, WIDTH), MARGIN, baseline(bottom - LINE), heading)
            }
            top = bottom
        }
    }

    private fun entry(canvas: Canvas, row: Sheet.Row, top: Float) {
        val valueWidth = body.measureText(row.value)
        val labelWidth = WIDTH - valueWidth - COLUMN_GAP
        canvas.drawText(fit(row.label, body, labelWidth), MARGIN, baseline(top), body)
        canvas.drawText(row.value, MARGIN + WIDTH - valueWidth, baseline(top), body)
        if (row.detail.isNotEmpty()) {
            canvas.drawText(fit(row.detail, muted, labelWidth), MARGIN, baseline(top + LINE), muted)
        }
    }

    private fun footer(canvas: Canvas, text: String) {
        canvas.drawText(text, MARGIN + WIDTH - muted.measureText(text), PAGE_HEIGHT - MARGIN / 2, muted)
    }

    /** The baseline of a line of body text whose box starts at [top]. */
    private fun baseline(top: Float) = top + LINE - DESCENT

    /** [text] cut with «…» to [width]: a row is one line, so a long name must not run into its amount. */
    private fun fit(text: String, paint: TextPaint, width: Float): String =
        TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()

    private val title = textPaint(TITLE_SIZE, Typeface.BOLD, Color.BLACK)
    private val heading = textPaint(BODY_SIZE, Typeface.BOLD, Color.BLACK)
    private val body = textPaint(BODY_SIZE, Typeface.NORMAL, Color.BLACK)
    private val muted = textPaint(SMALL_SIZE, Typeface.NORMAL, Color.DKGRAY)
    private val rule = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

    private fun textPaint(size: Float, style: Int, ink: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, style)
        color = ink
    }

    internal companion object {
        /** A4 in PostScript points. */
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 48f
        const val WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val LINE = 18f
        const val DESCENT = 5f
        const val RULE_GAP = 3f
        const val COLUMN_GAP = 16f
        const val TITLE_SIZE = 18f
        const val BODY_SIZE = 11f
        const val SMALL_SIZE = 9f

        /** The lines between the margins, leaving the foot for the page count. */
        val LINES_PER_PAGE = ((PAGE_HEIGHT - 2 * MARGIN - LINE) / LINE).toInt()
    }
}
