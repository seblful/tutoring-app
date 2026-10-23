package com.seblful.klas.print

import org.junit.Assert.assertEquals
import org.junit.Test

/** On a device: the platform's PDF writer has no JVM stand-in. */
class PdfSheetPrinterTest {

    @Test
    fun aSheetPrintsAsAPdfWithAPageForEachPageOfLines() {
        val rows = List(120) { Sheet.Row("Занятие, математика $it", "25,00 BYN", "Баланс после: $it BYN") }
        val sheet = Sheet("Анна: занятия и оплаты", listOf(Sheet.Section("", rows)), closing = "К оплате: 25 BYN")

        val pdf = PdfSheetPrinter().print(sheet).decodeToString()

        assertEquals("%PDF", pdf.take(4))
        assertEquals(
            sheet.pages(PdfSheetPrinter.LINES_PER_PAGE).size,
            Regex("""/Type\s*/Page\b""").findAll(pdf).count(),
        )
    }
}
