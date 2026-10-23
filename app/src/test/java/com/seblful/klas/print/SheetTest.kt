package com.seblful.klas.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetTest {

    private fun rows(count: Int, prefix: String = "Row") = List(count) { Sheet.Row("$prefix $it", "$it BYN") }

    @Test
    fun `a short sheet is one page, in order, without empty sections`() {
        val sheet = Sheet(
            title = "Klas report: 2026",
            subtitle = "Maths",
            sections = listOf(Sheet.Section("Total", rows(1)), Sheet.Section("Debts", emptyList())),
            closing = "That is all",
        )

        val pages = sheet.pages(linesPerPage = 40)

        assertEquals(
            listOf(
                Sheet.Line.Title("Klas report: 2026"),
                Sheet.Line.Subtitle("Maths"),
                Sheet.Line.Heading("Total"),
                Sheet.Line.Entry(Sheet.Row("Row 0", "0 BYN")),
                Sheet.Line.Closing("That is all"),
            ),
            pages.single(),
        )
    }

    @Test
    fun `no page holds more lines than it has room for`() {
        val sheet = Sheet("Statement", listOf(Sheet.Section("", List(30) { Sheet.Row("Lesson", "25", "Balance 25") })))

        val pages = sheet.pages(linesPerPage = 10)

        assertTrue(pages.all { page -> page.sumOf { it.height } <= 10 })
        assertEquals(30, pages.flatten().count { it is Sheet.Line.Entry })
        assertTrue("an untitled section prints no heading", pages.flatten().none { it is Sheet.Line.Heading })
    }

    @Test
    fun `a heading moves to the next page with its first row rather than end a page`() {
        // Title (2) + 6 rows = 8 lines; the heading (2) and a row (1) would need 11.
        val sheet = Sheet("Report", listOf(Sheet.Section("First", rows(6)), Sheet.Section("Second", rows(2, "Other"))))

        val pages = sheet.pages(linesPerPage = 10)

        assertEquals(2, pages.size)
        assertTrue(pages[0].last() is Sheet.Line.Entry)
        assertEquals(Sheet.Line.Heading("Second"), pages[1].first())
    }

    @Test
    fun `a filename loses the characters a file system refuses`() {
        assertEquals("Statement — Anna-Maria K-.pdf", fileSafe("Statement — Anna/Maria K:.pdf"))
    }
}
