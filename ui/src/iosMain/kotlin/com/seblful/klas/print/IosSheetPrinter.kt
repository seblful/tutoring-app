package com.seblful.klas.print

import com.seblful.klas.storage.toByteArray
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSString
import platform.Foundation.create
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSLineBreakByTruncatingTail
import platform.UIKit.NSMutableParagraphStyle
import platform.UIKit.NSParagraphStyleAttributeName
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererFormat
import platform.UIKit.drawInRect
import platform.UIKit.sizeWithAttributes

/**
 * [SheetPrinter] over UIKit's PDF renderer, laid out as Android's `PdfSheetPrinter` lays it out: A4 pages,
 * amounts against the right margin, a page count at the foot, the system font.
 */
@ContributesBinding(AppScope::class)
@Inject
class IosSheetPrinter : SheetPrinter {

    @OptIn(ExperimentalForeignApi::class)
    override fun print(sheet: Sheet): ByteArray {
        val pages = sheet.pages(LINES_PER_PAGE)
        val renderer = UIGraphicsPDFRenderer(
            bounds = CGRectMake(0.0, 0.0, PAGE_WIDTH, PAGE_HEIGHT),
            format = UIGraphicsPDFRendererFormat(),
        )
        return renderer.PDFDataWithActions { context ->
            pages.forEachIndexed { index, lines ->
                context?.beginPage()
                draw(lines)
                if (pages.size > 1) footer("${index + 1} / ${pages.size}")
            }
        }.toByteArray()
    }

    private fun draw(lines: List<Sheet.Line>) {
        var top = MARGIN
        lines.forEach { line ->
            val bottom = top + line.height * LINE
            when (line) {
                is Sheet.Line.Title -> text(line.text, title, MARGIN, top + LINE / 2, WIDTH, 2 * LINE)
                is Sheet.Line.Subtitle -> text(line.text, muted, MARGIN, top, WIDTH)
                is Sheet.Line.Heading -> {
                    text(line.text, heading, MARGIN, bottom - LINE, WIDTH)
                    rule(bottom - RULE_GAP)
                }
                is Sheet.Line.Entry -> entry(line.row, top)
                is Sheet.Line.Closing -> text(line.text, heading, MARGIN, bottom - LINE, WIDTH)
            }
            top = bottom
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun entry(row: Sheet.Row, top: Double) {
        val valueWidth = measure(row.value, body)
        val labelWidth = WIDTH - valueWidth - COLUMN_GAP
        text(row.label, body, MARGIN, top, labelWidth)
        text(row.value, body, MARGIN + WIDTH - valueWidth, top, valueWidth + 1)
        if (row.detail.isNotEmpty()) text(row.detail, muted, MARGIN, top + LINE, labelWidth)
    }

    private fun footer(page: String) {
        val width = measure(page, muted)
        text(page, muted, MARGIN + WIDTH - width, PAGE_HEIGHT - MARGIN / 2 - LINE + DESCENT, width + 1)
    }

    /** [value] in the box of one line from [top], cut with «…» to [width] — a row is one line. */
    @OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
    private fun text(value: String, style: Map<Any?, *>, x: Double, top: Double, width: Double, height: Double = LINE) {
        NSString.create(string = value).drawInRect(CGRectMake(x, top, width, height), withAttributes = style)
    }

    @OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
    private fun measure(value: String, style: Map<Any?, *>): Double =
        NSString.create(string = value).sizeWithAttributes(style).useContents { width }

    @OptIn(ExperimentalForeignApi::class)
    private fun rule(y: Double) {
        UIColor.lightGrayColor.setStroke()
        UIBezierPath().apply {
            lineWidth = 1.0
            moveToPoint(CGPointMake(MARGIN, y))
            addLineToPoint(CGPointMake(MARGIN + WIDTH, y))
            stroke()
        }
    }

    private fun style(font: UIFont, ink: UIColor): Map<Any?, *> = mapOf(
        NSFontAttributeName to font,
        NSForegroundColorAttributeName to ink,
        NSParagraphStyleAttributeName to
            NSMutableParagraphStyle().apply { setLineBreakMode(NSLineBreakByTruncatingTail) },
    )

    private val title = style(UIFont.boldSystemFontOfSize(TITLE_SIZE), UIColor.blackColor)
    private val heading = style(UIFont.boldSystemFontOfSize(BODY_SIZE), UIColor.blackColor)
    private val body = style(UIFont.systemFontOfSize(BODY_SIZE), UIColor.blackColor)
    private val muted = style(UIFont.systemFontOfSize(SMALL_SIZE), UIColor.darkGrayColor)

    private companion object {
        /** A4 in PostScript points. */
        const val PAGE_WIDTH = 595.0
        const val PAGE_HEIGHT = 842.0
        const val MARGIN = 48.0
        const val WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val LINE = 18.0
        const val DESCENT = 5.0
        const val RULE_GAP = 3.0
        const val COLUMN_GAP = 16.0
        const val TITLE_SIZE = 18.0
        const val BODY_SIZE = 11.0
        const val SMALL_SIZE = 9.0

        /** The lines between the margins, leaving the foot for the page count — Android's count. */
        val LINES_PER_PAGE = ((PAGE_HEIGHT - 2 * MARGIN - LINE) / LINE).toInt()
    }
}
